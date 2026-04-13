/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.metadata

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.util.isSecureUrl
import com.sphereon.oauth2.client.validation.validateAuthorizationServerMetadata
import com.sphereon.oauth2.common.error.MetadataError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.validation.toIdkResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Implementation of FetchAuthorizationServerMetadataCommand
 *
 * Fetches authorization server metadata from well-known endpoints following RFC 8414
 * and OpenID Connect Discovery specifications.
 */
@Inject
@SingleIn(SessionScope::class)
class FetchAuthorizationServerMetadataCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<FetchServerMetadataArgs, AuthorizationServerMetadata>(
        commandId = FetchAuthorizationServerMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FetchServerMetadataArgs>(),
        outputTypeToken = typeToken<AuthorizationServerMetadata>(),
    ),
    FetchAuthorizationServerMetadataCommand {
    override val commandId: String get() = FetchAuthorizationServerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FetchServerMetadataArgs

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun doExecute(
        args: FetchServerMetadataArgs,
        applyDuring: (FetchServerMetadataArgs) -> FetchServerMetadataArgs,
    ): IdkResult<AuthorizationServerMetadata, IdkError> {
        val applied = applyDuring(args)
        return fetchAuthorizationServerMetadataInternal(applied.issuer).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun fetchAuthorizationServerMetadataInternal(issuer: String): IdkResult<AuthorizationServerMetadata, MetadataError> {
        // Validate issuer format (allow HTTP for localhost/127.0.0.1 in development)
        if (!isSecureUrl(issuer)) {
            return Err(
                MetadataError.InvalidUrl(
                    url = issuer,
                    reason = "Issuer must be an HTTPS URL (HTTP is only allowed for localhost)",
                ),
            )
        }

        // Parse issuer to extract origin and path
        val issuerUrl =
            try {
                // Remove trailing slash
                val normalizedIssuer = issuer.trimEnd('/')
                val protocolEnd =
                    if (normalizedIssuer.startsWith("https://")) {
                        HTTPS_PREFIX_LENGTH
                    } else {
                        HTTP_PREFIX_LENGTH
                    }
                val originEnd = normalizedIssuer.indexOf('/', protocolEnd)
                if (originEnd == -1) {
                    Pair(normalizedIssuer, "")
                } else {
                    Pair(normalizedIssuer.substring(0, originEnd), normalizedIssuer.substring(originEnd))
                }
            } catch (e: Exception) {
                return Err(
                    MetadataError.InvalidUrl(
                        url = issuer,
                        reason = "Invalid URL format: ${e.message}",
                    ),
                )
            }

        val (origin, path) = issuerUrl

        // Construct well-known URLs
        val oauthServerWellKnownUrl = "$origin/.well-known/oauth-authorization-server$path"
        val legacyOauthServerWellKnownUrl = "$issuer/.well-known/oauth-authorization-server"
        val openIdConfigurationUrl = "$issuer/.well-known/openid-configuration"

        val attemptedUrls = mutableListOf<String>()
        var firstError: MetadataError? = null

        // Try OAuth 2.0 Authorization Server Metadata (RFC 8414 compliant)
        attemptedUrls.add(oauthServerWellKnownUrl)
        val oauthResult = fetchMetadata(oauthServerWellKnownUrl)
        when {
            oauthResult is Ok -> return validateAndCheckIssuer(oauthResult.value, issuer, oauthServerWellKnownUrl)
            oauthResult is Err && oauthResult.error !is MetadataError.FetchFailed -> firstError = oauthResult.error
        }

        // Try legacy non-compliant URL (if different)
        if (legacyOauthServerWellKnownUrl != oauthServerWellKnownUrl) {
            attemptedUrls.add(legacyOauthServerWellKnownUrl)
            val legacyResult = fetchMetadata(legacyOauthServerWellKnownUrl)
            when {
                legacyResult is Ok -> {
                    return validateAndCheckIssuer(legacyResult.value, issuer, legacyOauthServerWellKnownUrl)
                }

                legacyResult is Err && legacyResult.error !is MetadataError.FetchFailed && firstError == null -> {
                    firstError = legacyResult.error
                }
            }
        }

        // Try OpenID Connect Discovery
        attemptedUrls.add(openIdConfigurationUrl)
        val openIdResult = fetchMetadata(openIdConfigurationUrl)
        when {
            openIdResult is Ok -> {
                return validateAndCheckIssuer(openIdResult.value, issuer, openIdConfigurationUrl)
            }

            openIdResult is Err && openIdResult.error !is MetadataError.FetchFailed && firstError == null -> {
                firstError = openIdResult.error
            }
        }

        // All attempts failed
        return Err(
            firstError ?: MetadataError.NotFound(
                issuer = issuer,
                attemptedUrls = attemptedUrls,
            ),
        )
    }

    /**
     * Fetches metadata from a specific URL
     */
    private suspend fun fetchMetadata(url: String): IdkResult<AuthorizationServerMetadata, MetadataError> {
        val httpClient =
            httpClientFactory.createClient(
                HttpClientOptions(
                    engine = null,
                    enableContentNegotiation = true,
                ),
            )

        return try {
            val response: HttpResponse =
                httpClient.get(url) {
                    accept(ContentType.Application.Json)
                }

            when {
                response.status == HttpStatusCode.NotFound -> {
                    Err(
                        MetadataError.FetchFailed(
                            url = url,
                            reason = "Not found (404)",
                        ),
                    )
                }

                !response.status.isSuccess() -> {
                    Err(
                        MetadataError.FetchFailed(
                            url = url,
                            reason = "HTTP ${response.status.value}: ${response.status.description}",
                        ),
                    )
                }

                else -> {
                    val bodyText = response.body<String>()
                    try {
                        val metadata = json.decodeFromString<AuthorizationServerMetadata>(bodyText)
                        Ok(metadata)
                    } catch (e: Exception) {
                        Err(
                            MetadataError.FetchFailed(
                                url = url,
                                reason = "JSON parsing failed: ${e.message}",
                                exception = e,
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Err(
                MetadataError.FetchFailed(
                    url = url,
                    reason = "Network error: ${e.message}",
                    exception = e,
                ),
            )
        }
    }

    /**
     * Validates metadata and checks issuer match
     */
    private fun validateAndCheckIssuer(
        metadata: AuthorizationServerMetadata,
        requestedIssuer: String,
        metadataUrl: String,
    ): IdkResult<AuthorizationServerMetadata, MetadataError> {
        // Validate metadata structure
        val validationResult =
            validateAuthorizationServerMetadata(metadata).toIdkResult { errors ->
                MetadataError.ValidationFailed(
                    url = metadataUrl,
                    details = errors,
                )
            }

        if (validationResult is Err) {
            return validationResult
        }

        // Check issuer match
        if (metadata.issuer != requestedIssuer.trimEnd('/')) {
            return Err(
                MetadataError.IssuerMismatch(
                    requestedIssuer = requestedIssuer.trimEnd('/'),
                    receivedIssuer = metadata.issuer,
                    metadataUrl = metadataUrl,
                ),
            )
        }

        return Ok(metadata)
    }

    companion object {
        private const val HTTPS_PREFIX_LENGTH = 8
        private const val HTTP_PREFIX_LENGTH = 7
    }
}
