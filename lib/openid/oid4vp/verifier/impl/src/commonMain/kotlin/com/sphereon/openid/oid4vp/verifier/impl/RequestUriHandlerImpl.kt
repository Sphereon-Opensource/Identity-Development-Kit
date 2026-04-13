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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriResponse
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestUriHandler>())
class RequestUriHandlerImpl(
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val createSignedJarCommand: CreateSignedJarCommand,
    private val signingConfig: RequestObjectSigningConfig,
    private val clock: Clock = Clock.System,
) : RequestUriHandler {
    override suspend fun handleGet(requestUriPath: String): IdkResult<RequestUriResponse, IdkError> = handle(requestUriPath = requestUriPath, walletMetadata = null, walletNonce = null)

    override suspend fun handlePost(
        requestUriPath: String,
        walletMetadata: String?,
        walletNonce: String?,
    ): IdkResult<RequestUriResponse, IdkError> = handle(requestUriPath = requestUriPath, walletMetadata = walletMetadata, walletNonce = walletNonce)

    private suspend fun handle(
        requestUriPath: String,
        walletMetadata: String?,
        walletNonce: String?,
    ): IdkResult<RequestUriResponse, IdkError> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredWalletMetadata = walletMetadata

        @Suppress("UNUSED_VARIABLE")
        val ignoredWalletNonce = walletNonce

        val correlationId =
            extractCorrelationId(requestUriPath)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid requestUriPath: $requestUriPath"))

        val session =
            authorizationSessionStore.getForRequestUri(correlationId = correlationId, markRetrieved = true).getOrElse {
                return Err(it)
            }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "authorizationSession:$correlationId", message = "Authorization session not found or not eligible for request_uri"))

        // Strip only the self-referential parameters — the JAR can't point at / embed
        // itself. Everything else (state, client_metadata, client_metadata_uri, nonce,
        // dcql_query, response_mode, response_uri, …) stays in the JAR per OID4VP §5.10
        // ("parameters other than request, request_uri, client_id MUST be in the
        // Request Object"). `state` is ALSO kept in the outer URI by the URI builder
        // for OAuth2 (RFC 6749 / RFC 9101 §5 allow duplication with the same value);
        // strict wallets that only trust signed params read state from the JAR.
        val requestForJar: AuthorizationRequest =
            session.authorizationRequest.copy(
                requestUri = null,
                request = null,
            )

        val now = clock.now().toEpochMilliseconds()

        if (!signingConfig.enabled) {
            val expiresAt = now + (signingConfig.expirationSeconds * 1000)
            val requestJson =
                Json { encodeDefaults = true }.encodeToString(
                    AuthorizationRequest.serializer(),
                    requestForJar,
                )
            val header = """{"alg":"none"}"""
            val unsignedJwt = "${header.encodeToByteArray().encodeToBase64Url()}.${requestJson.encodeToByteArray().encodeToBase64Url()}."
            return Ok(
                RequestUriResponse(
                    signedJar = unsignedJwt,
                    contentType = RequestUriResponse.CONTENT_TYPE_JAR,
                    sessionId = session.sessionId,
                    expiresAt = expiresAt,
                ),
            )
        }

        val signingKey = signingConfig.resolveSigningKey()
        val binding =
            signingConfig.resolveSignerBinding()
                ?: return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "JAR signing is enabled but no VerifierSignerBinding was produced — configuration is incomplete.",
                    ),
                )

        // Guard: the session's client_id MUST already carry the binding prefix, because
        // CreateAuthorizationRequestCommandImpl substitutes it at session creation time.
        // If these ever disagree, the wallet will reject the request — surface instead.
        if (session.authorizationRequest.clientId != binding.clientId) {
            return Err(
                IdkError.UNKNOWN_ERROR(
                    message =
                        "Session client_id '${session.authorizationRequest.clientId}' does not match " +
                            "signer binding client_id '${binding.clientId}'. Session was likely created " +
                            "before signing was configured; recreate it.",
                ),
            )
        }

        // When `iss` is emitted, use the bare identifier (bare DID / DNS name / cert hash).
        // Wallet validators (e.g. credo-ts `decode-jwt.ts:154-159`) cross-check `iss` against
        // the JOSE header's DID URL / x5c SAN / hash — they expect the un-prefixed form,
        // not the §5.9.3 `<prefix>:<identifier>` wrapper that `client_id` carries.
        val jarArgs =
            CreateSignedJarArgs(
                authorizationRequest = requestForJar,
                signingKey = signingKey,
                issuer = binding.bareIdentifier,
                audience = signingConfig.audience,
                expirationSeconds = signingConfig.expirationSeconds,
                kid = (binding as? VerifierSignerBinding.Did)?.verificationMethodId,
                x5c =
                    (binding as? VerifierSignerBinding.X509SanDns)?.certificateChain
                        ?: (binding as? VerifierSignerBinding.X509Hash)?.certificateChain,
                includeIss = signingConfig.includeIss,
            )

        val signedJar =
            createSignedJarCommand.execute(jarArgs).fold(
                success = { it.value },
                failure = { e ->
                    return Err(
                        IdkError.fromString(
                            message = "Failed to create signed JAR for request_uri: ${e.message.defaultMessage}",
                            exception = (e.exception as? Exception) ?: Exception(e.toString()),
                        ),
                    )
                },
            )

        val expiresAt = now + (signingConfig.expirationSeconds * 1000)

        return Ok(
            RequestUriResponse(
                signedJar = signedJar,
                sessionId = session.sessionId,
                expiresAt = expiresAt,
            ),
        )
    }

    private fun extractCorrelationId(requestUriPath: String): String? {
        val trimmed = requestUriPath.trim().trimStart('/')
        if (trimmed.isBlank()) return null
        return trimmed.substringAfterLast('/').takeIf { it.isNotBlank() }
    }
}
