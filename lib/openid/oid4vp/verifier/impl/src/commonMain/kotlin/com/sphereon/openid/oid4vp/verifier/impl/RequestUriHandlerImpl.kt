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
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.impl.config.requireAbsoluteVerificationMethodIdForDid
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriResponse
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestUriHandler>())
class RequestUriHandlerImpl(
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val createSignedJarCommand: CreateSignedJarCommand,
    private val signingConfig: RequestObjectSigningConfig,
    private val clock: Clock,
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
        // walletMetadata is OID4VP-1FINAL-5.10's optional wallet feature advertisement.
        // We do not yet consume it; once we do, wire it through here.
        @Suppress("UNUSED_VARIABLE")
        val ignoredWalletMetadata = walletMetadata

        // walletNonce: per OID4VP-1FINAL-5.10, when the wallet POSTs to request_uri with a
        // wallet_nonce form field, the verifier MUST reflect that value as a `wallet_nonce`
        // claim in the signed Request Object so the wallet can confirm it produced the request
        // it is looking at. handleGet passes null and the GET path emits no wallet_nonce claim.
        if (walletNonce != null) {
            validateWalletNonce(walletNonce)?.let { return Err(it) }
        }

        val correlationId =
            extractCorrelationId(requestUriPath)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid requestUriPath: $requestUriPath"))

        val session =
            authorizationSessionStore.getForRequestUri(correlationId = correlationId, markRetrieved = true).getOrElse {
                return Err(it)
            }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "authorizationSession:$correlationId", message = "Authorization session not found or not eligible for request_uri"))

        // Strip the self-referential parameters — the JAR can't point at / embed itself.
        // Also strip `request_uri_method`: it's a URL-only parameter the wallet needs to
        // read BEFORE fetching the JAR (OID4VP §5.10), so embedding it inside the signed
        // Request Object is pointless and the conformance suite's
        // WarnIfRequestUriMethodInRequestObject condition flags it as a misuse. The
        // outer-URL emission still happens via BuildAuthorizationRequestUriCommandImpl,
        // which reads from the un-filtered AuthorizationRequest.additionalParameters.
        // Everything else (state, client_metadata, client_metadata_uri, nonce, dcql_query,
        // response_mode, response_uri, …) stays in the JAR per OID4VP §5.10 ("parameters
        // other than request, request_uri, client_id MUST be in the Request Object").
        // `state` is ALSO kept in the outer URI by the URI builder for OAuth2 (RFC 6749 /
        // RFC 9101 §5 allow duplication with the same value); strict wallets that only
        // trust signed params read state from the JAR.
        val urlOnlyParams = setOf("request_uri_method")
        val additionalParametersForJar =
            (
                if (walletNonce != null) {
                    session.authorizationRequest.additionalParameters + (WALLET_NONCE_PARAM to JsonPrimitive(walletNonce))
                } else {
                    session.authorizationRequest.additionalParameters
                }
            ).filterKeys { it !in urlOnlyParams }
        val requestForJar: AuthorizationRequest =
            session.authorizationRequest.copy(
                requestUri = null,
                request = null,
                additionalParameters = additionalParametersForJar,
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
        // Use the scheme derived from the session's already-prefixed client_id. The session
        // was created with one of OID4VP §5.9.3's prefixes; the JAR JOSE header (kid for did,
        // x5c for x509_san_dns / x509_hash) MUST match that prefix or the wallet rejects.
        // Multi-binding signing configs use this to pick the right binding per session.
        val sessionScheme = ClientIdScheme.fromClientId(session.authorizationRequest.clientId)
        val binding =
            signingConfig.resolveSignerBinding(sessionScheme)
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

        // A DID-signed request object identifies the bare DID in `iss`; the client_id keeps its
        // OID4VP Client Identifier Prefix. The JOSE kid is the full assertion-method DID URL and
        // must be rooted in exactly that issuer DID.
        val jarArgs =
            CreateSignedJarArgs(
                authorizationRequest = requestForJar,
                signingKey = signingKey,
                issuer = binding.requestObjectIssuer(),
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

    private fun validateWalletNonce(walletNonce: String): IdkError? {
        val result = walletNonceValidator(walletNonce)
        if (result is Invalid) {
            val detail = result.errors.joinToString("; ") { it.message }
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid wallet_nonce: $detail")
        }
        return null
    }

    private companion object {
        const val WALLET_NONCE_PARAM = "wallet_nonce"
        const val WALLET_NONCE_MIN_LEN = 8
        const val WALLET_NONCE_MAX_LEN = 512

        // OID4VP-1FINAL §5.10 ties wallet_nonce to nonce-shaped semantics; we hold it to the
        // RFC 3986 unreserved set (the same charset OID4VP-1FINAL §5.2 enforces on the
        // verifier-emitted `nonce`) plus a sane length bracket so a wallet cannot stuff
        // arbitrary content into a signed JAR claim.
        private val walletNonceValidator =
            Validation<String> {
                minLength(WALLET_NONCE_MIN_LEN) hint "wallet_nonce must be at least $WALLET_NONCE_MIN_LEN characters"
                maxLength(WALLET_NONCE_MAX_LEN) hint "wallet_nonce must not exceed $WALLET_NONCE_MAX_LEN characters"
                pattern("^[A-Za-z0-9._~-]+$") hint "wallet_nonce must use RFC 3986 unreserved characters: [A-Z], [a-z], [0-9], '-', '.', '_', '~'"
            }
    }
}

internal fun VerifierSignerBinding.requestObjectIssuer(): String =
    when (this) {
        is VerifierSignerBinding.Did -> {
            requireAbsoluteVerificationMethodIdForDid(did, verificationMethodId)
            did
        }

        else -> clientId
    }
