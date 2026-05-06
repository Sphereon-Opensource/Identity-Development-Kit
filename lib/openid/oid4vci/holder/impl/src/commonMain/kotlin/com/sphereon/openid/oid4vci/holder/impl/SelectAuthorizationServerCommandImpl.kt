/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.withClient
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerArgs
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Selects and fetches the Authorization Server metadata.
 *
 * Per OID4VCI 1.1 and RFC 8414:
 * - If issuerMetadata.authorizationServers is null, AS URL = issuer URL itself.
 * - Tries /.well-known/oauth-authorization-server first (RFC 8414 Section 3).
 * - Falls back to /.well-known/openid-configuration (OpenID Connect Discovery).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SelectAuthorizationServerCommand>())
class SelectAuthorizationServerCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<SelectAuthorizationServerArgs, ResolvedAuthorizationServer, IdkError>(
        commandId = SelectAuthorizationServerCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SelectAuthorizationServerArgs>(),
        outputTypeToken = typeToken<ResolvedAuthorizationServer>(),
    ),
    SelectAuthorizationServerCommand {
    override val commandId: String get() = SelectAuthorizationServerCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SelectAuthorizationServerArgs

    override suspend fun doExecute(
        args: SelectAuthorizationServerArgs,
        applyDuring: (SelectAuthorizationServerArgs) -> SelectAuthorizationServerArgs,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> {
        val applied = applyDuring(args)
        val issuerMetadata = applied.issuerMetadata

        // Determine the AS URL: prefer the first entry in authorization_servers, or use issuer URL
        val authServers = issuerMetadata.authorizationServers
        val asUrl =
            when {
                applied.preferredAuthorizationServer != null -> applied.preferredAuthorizationServer!!
                !authServers.isNullOrEmpty() -> authServers.first()
                else -> issuerMetadata.credentialIssuer
            }.trimEnd('/')

        log.debug("Selecting authorization server: $asUrl")

        return try {
            httpClientFactory.withClient { httpClient ->
                // Try RFC 8414 /.well-known/oauth-authorization-server first
                val oauthWellKnown = "$asUrl/.well-known/oauth-authorization-server"
                log.debug("Trying AS metadata from: $oauthWellKnown")

                val metadata =
                    fetchJsonObject(httpClient, oauthWellKnown)
                        ?: run {
                            // Fallback to OpenID Connect discovery
                            val oidcWellKnown = "$asUrl/.well-known/openid-configuration"
                            log.debug("Falling back to OIDC discovery at: $oidcWellKnown")
                            fetchJsonObject(httpClient, oidcWellKnown)
                        }
                        ?: return@withClient Err(
                            IdkError.fromString(
                                message = "Could not fetch AS metadata from $asUrl (tried oauth-authorization-server and openid-configuration)",
                                code = "AS_METADATA_NOT_FOUND",
                            ),
                        )

                log.debug("Successfully resolved AS metadata for: $asUrl")
                Ok(ResolvedAuthorizationServer(authorizationServerUrl = asUrl, metadata = metadata))
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error fetching AS metadata from $asUrl: ${expected.message}",
                    code = "AS_METADATA_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        }
    }

    private suspend fun fetchJsonObject(
        httpClient: io.ktor.client.HttpClient,
        url: String,
    ): JsonObject? {
        return try {
            val response = httpClient.get(url)
            if (!response.status.isSuccess()) {
                return null
            }
            val body = response.bodyAsText()
            Oid4vciJson.lenient.parseToJsonElement(body).jsonObject
        } catch (expected: Exception) {
            log.debug("Failed to fetch from $url: ${expected.message}")
            null
        }
    }
}
