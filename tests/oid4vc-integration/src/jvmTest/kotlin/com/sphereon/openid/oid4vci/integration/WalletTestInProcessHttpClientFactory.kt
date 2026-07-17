/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutableHttpAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientFactoryJvmImpl
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

// =========================================================================
// In-process HttpClientFactory replacement for holder HTTP calls, shared by
// WalletInteractionOid4vciRealProtocolE2ETest and WalletInteractionOid4vpDcqlRealProtocolE2ETest,
// which both depend on WalletTestAdapterHolderGraph for their wireInProcessAdapters() helper.
//
// Any holder (OID4VCI/OID4VP) uses the session-scoped HttpClientFactory binding for all
// outgoing HTTP. The default binding (HttpClientFactoryJvmImpl) makes real CIO/OkHttp calls
// that can't reach an in-process issuer/verifier.
//
// We cannot inject Set<HttpAdapter> directly because some adapters transitively depend on
// FetchRequestUriCommandImpl -> HttpClientFactory -> this factory, creating a hard runtime
// cycle (StackOverflowError even if the compile-time cycle is broken with Provider<>).
//
// Solution: a thread-safe holder object (WalletTestAdapterHolder) is bound into the DI graph
// as a singleton. The factory reads adapters from the holder lazily at createClient() time.
// Each test sets the adapters on the holder AFTER the graph is fully constructed (and all
// commands are already instantiated), so there is no circular construction.
//
// replaces = [HttpClientFactoryJvmImpl::class] ensures Metro picks this binding when this test
// module is on the classpath.
// =========================================================================

/**
 * Thread-safe mutable holder for the adapter set.
 * Set once after graph construction; read by WalletTestInProcessHttpClientFactory.createClient().
 */
@Inject
@SingleIn(SessionScope::class)
class WalletTestAdapterHolder {
    @Volatile
    var adapters: Set<HttpAdapter> = emptySet()
}

@ContributesTo(SessionScope::class)
interface WalletTestAdapterHolderGraph {
    val walletTestAdapterHolder: WalletTestAdapterHolder
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<HttpClientFactory>(),
    replaces = [HttpClientFactoryJvmImpl::class],
)
class WalletTestInProcessHttpClientFactory(
    private val adapterHolder: WalletTestAdapterHolder,
) : HttpClientFactory {
    override fun createClient(options: HttpClientOptions): HttpClient {
        val adapters = adapterHolder.adapters
        val engine =
            MockEngine { request ->
                val url = request.url
                val path = url.encodedPath
                val method = request.method.value
                val headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                val body =
                    when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                        is OutgoingContent.NoContent -> null
                        else -> null
                    }

                val host = url.host + (if (url.port != 443 && url.port != 80) ":${url.port}" else "")
                val enrichedHeaders =
                    headers +
                        mapOf(
                            "host" to host,
                            "x-forwarded-proto" to url.protocol.name,
                        )
                val queryParams = url.parameters.names().associateWith { url.parameters[it] }

                val genericRequest =
                    GenericHttpRequest(
                        method = method,
                        path = path,
                        queryParameters = queryParams,
                        headers = enrichedHeaders,
                        bodySupplier = body?.let { { it } },
                    )

                var response: GenericHttpResponse? = null
                for (adapter in adapters) {
                    if (adapter is RoutableHttpAdapter && !adapter.canHandle(genericRequest)) continue
                    val result = adapter.handleRequest(genericRequest)
                    // 404 = not found by this adapter; keep trying.
                    // COMMAND_ARG_NOT_SUPPORTED_ERROR at 400 means canHandle() was a false positive
                    // (e.g. OAuth2DiscoveryHttpAdapter claims /.well-known/* paths but can't handle
                    // /.well-known/openid-credential-issuer - it returns 400 with that error code).
                    // Continue to the next adapter so the OID4VCI issuer metadata adapter can claim it.
                    if (result.statusCode == 404) continue
                    if (result.statusCode == 400 && result.body?.contains("COMMAND_ARG_NOT_SUPPORTED_ERROR") == true) continue
                    response = result
                    break
                }
                val resp = response ?: GenericHttpResponse(404, emptyMap(), "Not found by WalletTestInProcessHttpClientFactory")

                respond(
                    content = resp.body ?: "",
                    status = HttpStatusCode.fromValue(resp.statusCode),
                    headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray()),
                )
            }
        return HttpClient(engine)
    }

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
}
