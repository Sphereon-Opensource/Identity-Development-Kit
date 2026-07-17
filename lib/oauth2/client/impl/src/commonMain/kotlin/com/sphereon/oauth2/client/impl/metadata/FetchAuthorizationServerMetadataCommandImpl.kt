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
import com.sphereon.core.api.validation.toIdkResult
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.DiscoveryMode
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.command.authorizationServerMetadataDiscoveryUrls
import com.sphereon.oauth2.client.util.isSecureUrl
import com.sphereon.oauth2.client.validation.validateAuthorizationServerMetadata
import com.sphereon.oauth2.common.error.MetadataError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
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
) : TypedServiceCommandAdapter<FetchServerMetadataArgs, AuthorizationServerMetadata, IdkError>(
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
        return fetchAuthorizationServerMetadataInternal(applied.issuer, applied.discoveryMode)
            .mapError { IdkError.fromDTO(it) }
    }

    private suspend fun fetchAuthorizationServerMetadataInternal(
        issuer: String,
        discoveryMode: DiscoveryMode,
    ): IdkResult<AuthorizationServerMetadata, MetadataError> {
        // Validate issuer format (allow HTTP for localhost/127.0.0.1 in development)
        if (!isSecureUrl(issuer)) {
            return Err(
                MetadataError.InvalidUrl(
                    url = issuer,
                    reason = "Issuer must be an HTTPS URL (HTTP is only allowed for localhost)",
                ),
            )
        }

        val candidates =
            try {
                authorizationServerMetadataDiscoveryUrls(issuer, discoveryMode)
            } catch (e: Exception) {
                return Err(
                    MetadataError.InvalidUrl(
                        url = issuer,
                        reason = "Invalid URL format: ${e.message}",
                    ),
                )
            }

        // OAUTH2_FIRST tries RFC 8414 first (legacy OAuth2 callers — prior behaviour);
        // OIDC_FIRST tries OIDC Discovery first so OIDC RPs see the richer metadata document.
        val attemptedUrls = mutableListOf<String>()
        var firstError: MetadataError? = null
        for (url in candidates) {
            attemptedUrls.add(url)
            val result = fetchMetadata(url)
            when {
                result is Ok -> {
                    return validateAndCheckIssuer(result.value, issuer, url)
                }

                result is Err && result.error !is MetadataError.FetchFailed && firstError == null -> {
                    firstError = result.error
                }
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
                    additionalConfig = { followRedirects = false },
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
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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

        // Check issuer match. Trailing-slash-insensitive on BOTH sides: authorization servers
        // legitimately publish their issuer with a trailing slash while callers pass the bare
        // URL (the OIDF conformance suite does exactly this), and a one-sided trim rejects
        // conformant servers.
        if (metadata.issuer.trimEnd('/') != requestedIssuer.trimEnd('/')) {
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
}
