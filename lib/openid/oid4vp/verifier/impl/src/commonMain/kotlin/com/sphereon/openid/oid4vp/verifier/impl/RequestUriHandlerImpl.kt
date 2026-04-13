/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriResponse
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriSigningConfig
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestUriHandler>())
class RequestUriHandlerImpl(
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val createSignedJarCommand: CreateSignedJarCommand,
    private val signingConfig: RequestUriSigningConfig,
    private val clock: Clock = Clock.System
) : RequestUriHandler {

    override suspend fun handleGet(requestUriPath: String): IdkResult<RequestUriResponse, IdkError> {
        return handle(requestUriPath = requestUriPath, walletMetadata = null, walletNonce = null)
    }

    override suspend fun handlePost(
        requestUriPath: String,
        walletMetadata: String?,
        walletNonce: String?
    ): IdkResult<RequestUriResponse, IdkError> {
        return handle(requestUriPath = requestUriPath, walletMetadata = walletMetadata, walletNonce = walletNonce)
    }

    private suspend fun handle(
        requestUriPath: String,
        walletMetadata: String?,
        walletNonce: String?
    ): IdkResult<RequestUriResponse, IdkError> {
        // For now walletMetadata + walletNonce are accepted but not used to customize the request object.
        // This keeps the interface compatible with request_uri_method=post and enables future enhancements.
        @Suppress("UNUSED_VARIABLE")
        val ignoredWalletMetadata = walletMetadata
        @Suppress("UNUSED_VARIABLE")
        val ignoredWalletNonce = walletNonce

        val correlationId = extractCorrelationId(requestUriPath)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid requestUriPath: $requestUriPath"))

        val session = authorizationSessionStore.getForRequestUri(correlationId = correlationId, markRetrieved = true).getOrElse {
            return Err(it)
        }
            ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "authorizationSession:$correlationId", message = "Authorization session not found or not eligible for request_uri"))

        // RFC 9101 merge rules: parameters MUST NOT be duplicated between request object and outer query.
        // The OpenID4VP-specific `client_metadata(_uri)` are sent in the outer URI so the wallet can
        // resolve verification keys for the signed request object, but must not be embedded in the JAR.
        // Strip OAuth2-level and outer-URI parameters from the request object (JAR).
        // Per RFC 9101: params MUST NOT be duplicated between request object and outer query.
        // Per OID4VP: state is an OAuth2 correlation param, not a request object param.
        // client_metadata(_uri) are in the outer URI for key discovery before JWT validation.
        val requestForJar: AuthorizationRequest = session.authorizationRequest.copy(
            requestUri = null,
            request = null,
            state = null,
            additionalParameters = session.authorizationRequest.additionalParameters - setOf("client_metadata", "client_metadata_uri")
        )

        val now = clock.now().toEpochMilliseconds()

        // When JAR signing is disabled, return an unsigned JWT (alg: none).
        // Per OID4VP spec: request_uri always returns JWT format. With client_id_scheme=redirect_uri
        // the request cannot be signed (no trusted key), so we use alg:none.
        if (!signingConfig.enabled) {
            val expiresAt = now + (signingConfig.expirationSeconds * 1000)
            val requestJson = Json { encodeDefaults = true }.encodeToString(
                AuthorizationRequest.serializer(),
                requestForJar
            )
            val header = """{"alg":"none"}"""
            val unsignedJwt = "${header.encodeToByteArray().encodeToBase64Url()}.${requestJson.encodeToByteArray().encodeToBase64Url()}."
            return Ok(
                RequestUriResponse(
                    signedJar = unsignedJwt,
                    contentType = RequestUriResponse.CONTENT_TYPE_JAR,
                    sessionId = session.sessionId,
                    expiresAt = expiresAt
                )
            )
        }

        val jarArgs = CreateSignedJarArgs(
            authorizationRequest = requestForJar,
            signingKey = signingConfig.signingKey,
            issuer = session.authorizationRequest.clientId,
            audience = signingConfig.audience,
            expirationSeconds = signingConfig.expirationSeconds
        )

        val signedJar = createSignedJarCommand.execute(jarArgs).fold(
            success = { it.value },
            failure = { e ->
                return Err(
                    IdkError.fromString(
                        message = "Failed to create signed JAR for request_uri: ${e.message.defaultMessage}",
                        exception = (e.exception as? Exception) ?: Exception(e.toString())
                    )
                )
            }
        )

        val expiresAt = now + (signingConfig.expirationSeconds * 1000)

        return Ok(
            RequestUriResponse(
                signedJar = signedJar,
                sessionId = session.sessionId,
                expiresAt = expiresAt
            )
        )
    }

    private fun extractCorrelationId(requestUriPath: String): String? {
        val trimmed = requestUriPath.trim().trimStart('/')
        if (trimmed.isBlank()) return null
        return trimmed.substringAfterLast('/').takeIf { it.isNotBlank() }
    }
}
