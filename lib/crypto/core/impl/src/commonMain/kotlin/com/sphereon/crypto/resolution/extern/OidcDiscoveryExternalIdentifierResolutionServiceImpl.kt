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
 *
 */

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves OIDC/OAuth2 AS metadata from a well-known discovery URL.
 *
 * Fetches AS metadata from `{url}/.well-known/oauth-authorization-server` (falling back to
 * `{url}/.well-known/openid-configuration`), then delegates JWKS resolution to
 * [JwksUrlExternalIdentifierResolutionService] using the discovered `jwks_uri`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcDiscoveryExternalIdentifierService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class OidcDiscoveryExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val jwksUrlResolver: JwksUrlExternalIdentifierResolutionService,
) : ExternalIdentifierServiceAdapter<OidcDiscoveryExternalIdentifierResult>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.OIDC_DISCOVERY),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    OidcDiscoveryExternalIdentifierService {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<OidcDiscoveryExternalIdentifierResult, IdkErrorType> {
        val opts = asSupportedOpts(args).value
        val baseUrl = opts.identifier.trimEnd('/')
        if (baseUrl.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OIDC Discovery URL is blank").asErrorResult()
        }

        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to create HTTP client: ${expected.message}",
                        exception = expected,
                    ).asErrorResult()
            }

        val (metadataJson, discoveredUrl) =
            try {
                // Try RFC 8414 AS metadata first, fall back to OIDC discovery
                val oauthUrl = "$baseUrl/.well-known/oauth-authorization-server"
                val oidcUrl = "$baseUrl/.well-known/openid-configuration"

                val oauthResponse =
                    try {
                        httpClient.get(oauthUrl)
                    } catch (expected: Exception) {
                        log.debug("OAuth AS metadata fetch failed, falling back to OIDC discovery: ${expected.message}")
                        null
                    }

                if (oauthResponse != null && oauthResponse.status == HttpStatusCode.OK) {
                    oauthResponse.body<String>() to oauthUrl
                } else {
                    val oidcResponse = httpClient.get(oidcUrl)
                    if (oidcResponse.status != HttpStatusCode.OK) {
                        return IdkError
                            .UNKNOWN_ERROR(
                                message = "Failed to fetch AS metadata from $oidcUrl: HTTP ${oidcResponse.status.value}",
                            ).asErrorResult()
                    }
                    oidcResponse.body<String>() to oidcUrl
                }
            } catch (expected: Exception) {
                return IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to fetch AS metadata from $baseUrl: ${expected.message}",
                        exception = expected,
                    ).asErrorResult()
            } finally {
                try {
                    httpClient.close()
                } catch (expected: Exception) {
                    log.debug("HTTP client close failed: ${expected.message}")
                }
            }

        val metadataJsonObject =
            try {
                json.parseToJsonElement(metadataJson) as? JsonObject
                    ?: return IdkError
                        .ILLEGAL_ARGUMENT_ERROR(
                            message = "AS metadata from $discoveredUrl is not a JSON object",
                        ).asErrorResult()
            } catch (expected: Exception) {
                return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message = "Invalid AS metadata JSON from $discoveredUrl: ${expected.message}",
                        throwable = expected,
                    ).asErrorResult()
            }

        val jwksUri =
            metadataJsonObject["jwks_uri"]?.jsonPrimitive?.content
                ?: return IdkError
                    .NOT_FOUND_ERROR(
                        message = "AS metadata from $discoveredUrl does not contain jwks_uri",
                    ).asErrorResult()

        val jwksResult =
            jwksUrlResolver
                .resolve(
                    ExternalIdentifierJwksUrlOpts(
                        identifier = jwksUri,
                        context = opts.context,
                        lookup = opts.lookup,
                    ),
                ).getOrElse { error -> return error.asErrorResult() }

        val jwksUrlResult =
            jwksResult as? ExternalIdentifierResult.JwksUrl
                ?: return IdkError
                    .UNKNOWN_ERROR(
                        message = "JWKS resolution for $jwksUri returned unexpected result type: ${jwksResult::class.simpleName}",
                    ).asErrorResult()

        return OidcDiscoveryExternalIdentifierResult(
            identifierOpts = opts,
            jwks = jwksUrlResult.jwks,
            keyInfo = jwksUrlResult.keyInfo,
            authorizationServerMetadata = metadataJsonObject,
            jwksUri = jwksUri,
        ).asOkResult()
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        val methodSupported =
            externalArgs is ExternalIdentifierOidcDiscoveryOpts ||
                externalArgs.method?.let { isSupportedIdentifierMethod(it) } == true
        return methodSupported && isSupportedIdentifier(externalArgs.identifier)
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean =
        identifier is String &&
            (identifier.startsWith("https://", ignoreCase = true) || identifier.startsWith("http://", ignoreCase = true)) &&
            identifier.contains("://")

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<OidcDiscoveryExternalIdentifierResult, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOidcDiscoveryOpts, IdkErrorType> =
        if (opts is ExternalIdentifierOidcDiscoveryOpts) {
            opts.asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    companion object {
        const val COMMAND_ID = "crypto.resolution.oidcdiscovery"
    }
}
