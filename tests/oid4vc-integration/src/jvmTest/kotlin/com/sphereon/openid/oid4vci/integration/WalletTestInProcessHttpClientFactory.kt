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
import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelector
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientFactoryJvmImpl
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
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
// which both issue requests against the same route-first HTTP graph as production.
//
// Any holder (OID4VCI/OID4VP) uses the session-scoped HttpClientFactory binding for all
// outgoing HTTP. The default binding (HttpClientFactoryJvmImpl) makes real CIO/OkHttp calls
// that can't reach an in-process issuer/verifier.
//
// The AppScope selector is metadata-only and the SessionScope dispatcher resolves only the
// selected Lazy<HttpAdapter>. This keeps the HttpClientFactory -> adapter graph lazy without the
// mutable eager-adapter holder that used to materialize every adapter before each test.
//
// replaces = [HttpClientFactoryJvmImpl::class] ensures Metro picks this binding when this test
// module is on the classpath.
// =========================================================================

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<HttpClientFactory>(),
    replaces = [HttpClientFactoryJvmImpl::class],
)
class WalletTestInProcessHttpClientFactory(
    private val routeSelector: HttpAdapterRouteSelector,
    private val dispatcher: HttpAdapterDispatcher,
) : HttpClientFactory {
    override fun createClient(options: HttpClientOptions): HttpClient {
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

                val resp =
                    when (val selection = routeSelector.select(method, path)) {
                        is HttpAdapterRouteSelection.Selected -> dispatcher.dispatch(genericRequest, selection.match)
                        is HttpAdapterRouteSelection.NotFound ->
                            com.sphereon.core.api.http.GenericHttpResponse(
                                404,
                                emptyMap(),
                                "Not found by WalletTestInProcessHttpClientFactory",
                            )
                        is HttpAdapterRouteSelection.Ambiguous ->
                            com.sphereon.core.api.http.GenericHttpResponse(500, emptyMap(), "Ambiguous in-process HTTP route")
                        is HttpAdapterRouteSelection.Misconfigured ->
                            com.sphereon.core.api.http.GenericHttpResponse(500, emptyMap(), selection.message)
                    }

                WalletTestInProcessHttpCapture.record(genericRequest, resp)

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

/** Captures holder-originated HTTP exchanges for E2E assertions at the verifier/RP boundary. */
internal object WalletTestInProcessHttpCapture {
    private val exchanges = mutableListOf<WalletTestHttpExchange>()

    @Synchronized
    fun clear() {
        exchanges.clear()
    }

    @Synchronized
    fun snapshot(): List<WalletTestHttpExchange> = exchanges.toList()

    @Synchronized
    internal fun record(request: GenericHttpRequest, response: GenericHttpResponse) {
        exchanges += WalletTestHttpExchange(request.method, request.path, request.body, response)
    }
}

internal data class WalletTestHttpExchange(
    val method: String,
    val path: String,
    val body: String?,
    val response: GenericHttpResponse,
)

/** Route a test request through the same AppScope selection and SessionScope dispatch as ingress. */
internal suspend fun Oid4vciTestContext.dispatchInProcessHttp(request: GenericHttpRequest): GenericHttpResponse {
    val selector = (app as HttpAdapterRouteSelector.Graph).httpAdapterRouteSelector
    val dispatcher = (session.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher
    return when (val selection = selector.select(request.method, request.path)) {
        is HttpAdapterRouteSelection.Selected -> dispatcher.dispatch(request, selection.match)
        is HttpAdapterRouteSelection.NotFound -> GenericHttpResponse(404, emptyMap(), "Not found")
        is HttpAdapterRouteSelection.Ambiguous -> GenericHttpResponse(500, emptyMap(), "Ambiguous route")
        is HttpAdapterRouteSelection.Misconfigured -> GenericHttpResponse(500, emptyMap(), selection.message)
    }
}
