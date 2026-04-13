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

package com.sphereon.openid.oid4vci.common.impl.resolution

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOidcDiscoveryOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierServiceAdapter
import com.sphereon.crypto.resolution.extern.OidcDiscoveryExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.OidcDiscoveryExternalIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.Oid4vciUrls
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.ExternalIdentifierOid4vciIssuerOpts
import com.sphereon.openid.oid4vci.common.model.Oid4vciIssuerExternalIdentifierResult
import com.sphereon.openid.oid4vci.common.model.Oid4vciIssuerExternalIdentifierService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves OID4VCI credential issuer metadata from the `.well-known/openid-credential-issuer`
 * endpoint, then delegates AS metadata + JWKS resolution to [OidcDiscoveryExternalIdentifierService].
 *
 * Well-known URL construction follows OID4VCI 1.1 Section 13.2 (path insertion):
 * - `https://issuer.example.com` → `https://issuer.example.com/.well-known/openid-credential-issuer`
 * - `https://issuer.example.com/tenant1` → `https://issuer.example.com/.well-known/openid-credential-issuer/tenant1`
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuerExternalIdentifierService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class Oid4vciIssuerExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val oidcDiscoveryResolver: OidcDiscoveryExternalIdentifierService,
    private val verifyJwsCommand: VerifyJwsCommand? = null,
) : ExternalIdentifierServiceAdapter<Oid4vciIssuerExternalIdentifierResult>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.OID4VCI_ISSUER),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    Oid4vciIssuerExternalIdentifierService {
    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<Oid4vciIssuerExternalIdentifierResult, IdkErrorType> {
        val opts = asSupportedOpts(args).value
        val issuerUrl = opts.identifier.trimEnd('/')
        if (issuerUrl.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VCI issuer URL is blank").asErrorResult()
        }

        val wellKnownUrl = Oid4vciUrls.buildWellKnownUrl(issuerUrl)

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

        val issuerMetadataJson =
            try {
                val response = httpClient.get(wellKnownUrl)
                if (response.status != HttpStatusCode.OK) {
                    return IdkError
                        .UNKNOWN_ERROR(
                            message = "Failed to fetch issuer metadata from $wellKnownUrl: HTTP ${response.status.value}",
                        ).asErrorResult()
                }
                response.body<String>()
            } catch (expected: Exception) {
                return IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to fetch issuer metadata from $wellKnownUrl: ${expected.message}",
                        exception = expected,
                    ).asErrorResult()
            } finally {
                try {
                    httpClient.close()
                } catch (expected: Exception) {
                    log.debug("Failed to close HTTP client: ${expected.message}")
                }
            }

        val issuerMetadataJsonObject =
            try {
                Oid4vciJson.lenient.parseToJsonElement(issuerMetadataJson) as? JsonObject
                    ?: return IdkError
                        .ILLEGAL_ARGUMENT_ERROR(
                            message = "Issuer metadata from $wellKnownUrl is not a JSON object",
                        ).asErrorResult()
            } catch (expected: Exception) {
                return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message = "Invalid issuer metadata JSON from $wellKnownUrl: ${expected.message}",
                        throwable = expected,
                    ).asErrorResult()
            }

        // Validate credential_issuer matches
        val claimedIssuer = issuerMetadataJsonObject["credential_issuer"]?.jsonPrimitive?.content
        if (claimedIssuer == null) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    message = "Issuer metadata from $wellKnownUrl is missing credential_issuer field",
                ).asErrorResult()
        }
        if (claimedIssuer.trimEnd('/') != issuerUrl) {
            log.warn(
                "credential_issuer '$claimedIssuer' does not match requested issuer URL '$issuerUrl'; proceeding with caution",
            )
        }

        val issuerMetadata =
            try {
                Oid4vciJson.lenient.decodeFromJsonElement<CredentialIssuerMetadata>(issuerMetadataJsonObject)
            } catch (expected: Exception) {
                return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to parse issuer metadata from $wellKnownUrl: ${expected.message}",
                        throwable = expected,
                    ).asErrorResult()
            }

        // If the metadata contains a signed_metadata JWT, verify it and prefer its content
        val effectiveIssuerMetadata =
            if (issuerMetadata.signedMetadata != null && verifyJwsCommand != null) {
                try {
                    val verifyIdkResult =
                        verifyJwsCommand.execute(
                            VerifyJwsArgs(jws = JwsCompact(issuerMetadata.signedMetadata!!)),
                        )

                    if (verifyIdkResult.isOk && verifyIdkResult.value.isValid) {
                        try {
                            Oid4vciJson.lenient.decodeFromJsonElement(
                                CredentialIssuerMetadata.serializer(),
                                verifyIdkResult.value.parsedPayload,
                            )
                        } catch (expected: Exception) {
                            log.warn("Failed to parse signed issuer metadata payload: ${expected.message}")
                            issuerMetadata
                        }
                    } else {
                        // Verification failed — fall through to unsigned metadata
                        issuerMetadata
                    }
                } catch (expected: Exception) {
                    log.warn("Unexpected error verifying signed issuer metadata: ${expected.message}")
                    issuerMetadata
                }
            } else {
                issuerMetadata
            }

        // Determine the AS URL: prefer first entry in authorization_servers, fall back to issuer URL
        val asUrl = effectiveIssuerMetadata.authorizationServers?.firstOrNull() ?: issuerUrl

        // Delegate AS metadata + JWKS resolution to the OIDC discovery resolver
        val asResult =
            oidcDiscoveryResolver
                .resolve(
                    ExternalIdentifierOidcDiscoveryOpts(identifier = asUrl),
                ).getOrElse { error -> return error.asErrorResult() }

        val oidcResult =
            asResult as? OidcDiscoveryExternalIdentifierResult
                ?: return IdkError
                    .UNKNOWN_ERROR(
                        message = "AS resolution for $asUrl returned unexpected result type: ${asResult::class.simpleName}.",
                    ).asErrorResult()

        return Oid4vciIssuerExternalIdentifierResult(
            identifierOpts = opts,
            jwks = oidcResult.jwks,
            keyInfo = oidcResult.keyInfo,
            issuerMetadata = effectiveIssuerMetadata,
            authorizationServerUrl = asUrl,
            authorizationServerMetadata = oidcResult.authorizationServerMetadata,
        ).asOkResult()
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        val methodSupported =
            externalArgs is ExternalIdentifierOid4vciIssuerOpts ||
                externalArgs.method?.let { isSupportedIdentifierMethod(it) } == true
        return methodSupported && isSupportedIdentifier(externalArgs.identifier)
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean =
        identifier is String &&
            (identifier.startsWith("https://", ignoreCase = true) || identifier.startsWith("http://", ignoreCase = true)) &&
            identifier.contains("://")

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<Oid4vciIssuerExternalIdentifierResult, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOid4vciIssuerOpts, IdkErrorType> =
        if (opts is ExternalIdentifierOid4vciIssuerOpts) {
            opts.asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    companion object {
        const val COMMAND_ID = "oid4vci.issuer.resolve"
    }
}
