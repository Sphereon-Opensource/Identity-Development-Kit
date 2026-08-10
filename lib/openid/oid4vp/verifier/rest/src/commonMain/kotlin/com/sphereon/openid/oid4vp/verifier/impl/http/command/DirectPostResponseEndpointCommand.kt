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

package com.sphereon.openid.oid4vp.verifier.impl.http.command

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requireBody
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.tryGenerateJwkThumbprint
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.clientMetadata
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.config.ResponseEncryptionKeyConfig
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Command interface for the OID4VP direct_post response endpoint.
 *
 * The wallet POSTs the VP token and presentation_submission to this endpoint
 * after the user has approved the presentation request.
 *
 * Per OpenID4VP 1.0 Section 8.4 - Response Mode: direct_post
 */
interface DirectPostResponseEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vp.directpost.response"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/auth/response",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "handleDirectPostResponse",
                tags = setOf("oid4vp", "direct-post"),
                summary = "Handle OID4VP direct_post authorization response from wallet",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )
    }
}

/**
 * Implementation of [DirectPostResponseEndpointCommand].
 *
 * POST /oid4vp/auth/response
 *
 * Receives the wallet's authorization response (vp_token, state, presentation_submission)
 * via application/x-www-form-urlencoded POST and delegates to [HandleDirectPostResponseCommand].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DirectPostResponseEndpointCommand>())
class DirectPostResponseEndpointCommandImpl(
    execution: SessionExecution,
    private val handleDirectPostCommand: HandleDirectPostResponseCommand,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val responseEncryptionKeyConfig: ResponseEncryptionKeyConfig,
) : HttpEndpointCommandAdapter(
        id = DirectPostResponseEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DirectPostResponseEndpointCommand.ENDPOINT,
    ),
    DirectPostResponseEndpointCommand {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // Parse form-urlencoded body into response params
        val body = request.requireBody().getOrElse { return Err(it) }
        val responseParams = parseFormBody(body)
        if (responseParams.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing or empty request body"))
        }

        // Identify the session. Two response shapes per OID4VP §8.4:
        //   - direct_post: form has plaintext fields including `state` (the correlationId).
        //   - direct_post.jwt: form has only `response=<JWE>`. State lives INSIDE the
        //     encrypted JWT payload — we can't read it without first decrypting. To find
        //     the right decryption key without that chicken-and-egg, we use the JWE
        //     header's `kid` parameter, which the wallet MUST copy from the JWK's `kid`
        //     it selected (OID4VP §8.3). The universal command sets that kid equal to
        //     the session correlationId so a single store lookup suffices.
        val correlationId =
            responseParams["state"]?.takeIf { it.isNotBlank() }
                ?: responseParams["response"]?.let { jwe -> extractKidFromJweHeader(jwe) }
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Cannot identify session: form body lacks `state` and the JWE in `response` carries no `kid` header.",
                    ),
                )

        // Look up the authorization session to get the original request
        val session =
            authorizationSessionStore.getByCorrelationId(correlationId).getOrNull()
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Authorization session not found: $correlationId"))

        // This response-endpoint redirect is distinct from the authorization request's
        // `redirect_uri`: direct_post uses `response_uri` for wallet submission, while this
        // optional session value controls the subsequent browser navigation.
        val sessionRedirectUri = session.directPostResponseRedirectUri.orEmpty()

        // A `direct_post.jwt` session decrypts under the key the server holds for its verifier
        // instance. The session carries the instance, never a key selector, so a stored session
        // cannot steer decryption at any raw alias, provider, or key id. The resolved name builds
        // the opaque `KeyInfo` handle the JARM decryption command (deeper in
        // ParseAuthorizationResponseCommandImpl → VerifyJarmResponseCommandImpl) hands the KMS for
        // the ECDH key agreement. PRIVATE expresses the intended operation; it does not request key
        // export, and no key material crosses this boundary.
        val encryptedResponse = session.authorizationRequest.responseMode == ResponseMode.DIRECT_POST_JWT.value
        val jarmDecryptionKeyName =
            if (encryptedResponse) {
                responseEncryptionKeyConfig.resolveEncryptionKeyName(session.instanceId)
                    ?: return Err(
                        IdkError.UNKNOWN_ERROR(message = ResponseEncryptionKeyConfig.RESPONSE_ENCRYPTION_KEY_UNAVAILABLE),
                    )
            } else {
                null
            }
        val jarmDecryptionKey =
            jarmDecryptionKeyName?.let { keyName ->
                ManagedOptsKeyInfo(
                    identifier =
                        KeyInfo<Nothing>(
                            alias = keyName,
                            keyVisibility = KeyVisibility.PRIVATE,
                        ),
                )
            }

        // OID4VP §B.2.6.2 mdoc handover: when the response is encrypted (`direct_post.jwt`),
        // the SessionTranscript handover MUST embed the SHA-256 thumbprint (RFC 7638) of
        // the verifier's encryption-key JWK. We published that JWK in client_metadata.jwks
        // when creating the authorization request, so the public params are already on the
        // session — extract the same key the wallet selected (matched by `kid`, which the
        // universal command sets equal to the session correlationId) and hash it. Raw
        // 32-byte digest (the spec mandates the bytes, not the base64url encoding).
        val verifierEncryptionJwkThumbprint =
            if (encryptedResponse) {
                val jwks =
                    session.authorizationRequest.clientMetadata
                        ?.jwks
                        ?.keys
                        ?.toList()
                        .orEmpty()
                val matchingJwk = jwks.firstOrNull { it.kid == correlationId } ?: jwks.firstOrNull()
                matchingJwk?.let { jwk ->
                    tryGenerateJwkThumbprint(jwk).getOrNull()?.decodeFrom(Encoding.BASE64URL)
                }
            } else {
                null
            }

        // Build args for the direct_post handler
        val directPostArgs =
            HandleDirectPostResponseArgs(
                responseParams = responseParams,
                originalRequest = session.authorizationRequest,
                dcqlQuery = session.dcqlQuery,
                redirectUri = sessionRedirectUri,
                jarmDecryptionKey = jarmDecryptionKey,
                verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                verifierId = session.verifierId,
                dcqlQueryId = session.dcqlQueryId,
                templateId = session.templateId,
            )

        // Delegate to the service command
        return handleDirectPostCommand.execute(directPostArgs).map { result ->
            // Per OID4VP 1.0: response is HTTP 200 with optional redirect_uri
            // containing response_code as fragment. When no redirect is configured,
            // return empty JSON — the wallet treats HTTP 200 as success.
            val includeRedirect = sessionRedirectUri.isNotBlank()
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                    ),
                body =
                    json.encodeToString(
                        kotlinx.serialization.serializer(),
                        buildJsonObject {
                            if (includeRedirect) {
                                put("redirect_uri", result.redirectUri)
                            }
                        },
                    ),
            )
        }
    }

    /**
     * Read the `kid` parameter from the JWE Protected Header without performing any
     * decryption. The header is the first segment of the compact serialization
     * (`<header>.<encryptedKey>.<iv>.<ciphertext>.<tag>`), base64url-encoded JSON.
     *
     * Used by `direct_post.jwt` session lookup — wallets per OID4VP §8.3 MUST copy the
     * selected JWK's `kid` to the JWE Protected Header, and the universal command sets
     * that kid to the session correlationId, so a single store lookup resolves the
     * session before any decryption is attempted.
     */
    private fun extractKidFromJweHeader(jwe: String): String? {
        val firstDot = jwe.indexOf('.')
        if (firstDot <= 0) return null
        val headerB64 = jwe.substring(0, firstDot)
        return try {
            val headerJson = headerB64.decodeFromBase64Url().decodeToString()
            (json.parseToJsonElement(headerJson) as? kotlinx.serialization.json.JsonObject)
                ?.get("kid")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        } catch (expected: Exception) {
            null
        }
    }

    /**
     * Parse application/x-www-form-urlencoded body into a map.
     */
    private fun parseFormBody(body: String?): Map<String, String> {
        if (body.isNullOrBlank()) return emptyMap()
        return body
            .split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    urlDecode(parts[0]) to urlDecode(parts[1])
                } else {
                    null
                }
            }.toMap()
    }

    private fun urlDecode(value: String): String =
        buildString {
            var i = 0
            while (i < value.length) {
                when {
                    value[i] == '%' && i + 2 < value.length -> {
                        val hex = value.substring(i + 1, i + 3)
                        append(hex.toInt(16).toChar())
                        i += 3
                    }

                    value[i] == '+' -> {
                        append(' ')
                        i++
                    }

                    else -> {
                        append(value[i])
                        i++
                    }
                }
            }
        }
}
