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
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestDeferredCredentialCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Polls the deferred credential endpoint to retrieve a pending credential.
 *
 * Per OID4VCI 1.1 Section 10.1: HTTP POST to deferred endpoint with access-token auth.
 * Request body: DeferredCredentialRequest with transaction_id.
 *
 * Response handling:
 * - HTTP 200 = credential ready     → parse as CredentialResponse
 * - HTTP 202 = still pending        → parse as CredentialResponse (contains updated interval)
 * - HTTP 4xx = error                → parse as Oid4vciErrorResponse, return Err
 *
 * Additional features:
 * - JWE decryption (C-3): When the response Content-Type is application/jwt and
 *   [RequestDeferredCredentialArgs.decryptionKey] is provided, the JWE compact response is
 *   decrypted before parsing as CredentialResponse.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestDeferredCredentialCommand>())
class RequestDeferredCredentialCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val decryptJweCommand: DecryptJweCommand,
    private val jweService: JweService,
) : TypedServiceCommandAdapter<RequestDeferredCredentialArgs, CredentialResponse, IdkError>(
        commandId = RequestDeferredCredentialCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RequestDeferredCredentialArgs>(),
        outputTypeToken = typeToken<CredentialResponse>(),
    ),
    RequestDeferredCredentialCommand {
    override val commandId: String get() = RequestDeferredCredentialCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RequestDeferredCredentialArgs

    override suspend fun doExecute(
        args: RequestDeferredCredentialArgs,
        applyDuring: (RequestDeferredCredentialArgs) -> RequestDeferredCredentialArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val applied = applyDuring(args)

        log.debug("Polling deferred credential endpoint: ${applied.deferredCredentialEndpoint}")

        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to create HTTP client: ${expected.message}", throwable = expected))
            }

        return try {
            val deferredRequest =
                DeferredCredentialRequest(
                    transactionId = applied.transactionId,
                    credentialResponseEncryption = applied.credentialResponseEncryption,
                )

            val requestJson = Oid4vciJson.lenient.encodeToString(DeferredCredentialRequest.serializer(), deferredRequest)

            // Encrypt request body if request encryption parameters are provided
            val (requestBody, requestContentType) =
                encryptRequestIfNeeded(requestJson, applied.requestEncryptionJwk, applied.requestEncryptionAlg, applied.requestEncryptionEnc)
                    ?: return Err(
                        IdkError.fromString(
                            message = "Failed to encrypt deferred credential request body",
                            code = "REQUEST_ENCRYPTION_FAILED",
                        ),
                    )

            val response =
                httpClient.post(applied.deferredCredentialEndpoint) {
                    contentType(requestContentType)
                    headers {
                        val scheme = if (applied.dpopProofJwt != null) "DPoP" else "Bearer"
                        append("Authorization", "$scheme ${applied.accessToken}")
                        applied.dpopProofJwt?.let { append("DPoP", it) }
                    }
                    setBody(requestBody)
                }

            val body = response.bodyAsText()

            when {
                response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Accepted -> {
                    // C-3: JWE decryption — detect encrypted credential response
                    val plaintextBody =
                        if (isJweResponse(response.headers["Content-Type"], body)) {
                            decryptJweBody(body, applied)
                                ?: return Err(
                                    IdkError.fromString(
                                        message =
                                            "Encrypted deferred credential response received but decryption failed. " +
                                                "Ensure decryptionKey is provided in RequestDeferredCredentialArgs.",
                                        code = "CREDENTIAL_RESPONSE_DECRYPTION_FAILED",
                                    ),
                                )
                        } else {
                            body
                        }

                    val credentialResponse =
                        try {
                            Oid4vciJson.lenient.decodeFromString(CredentialResponse.serializer(), plaintextBody)
                        } catch (expected: Exception) {
                            return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "Failed to parse deferred credential response JSON: ${expected.message}",
                                    throwable = expected,
                                ),
                            )
                        }
                    log.debug("Deferred credential response received (status: ${response.status.value})")
                    Ok(credentialResponse)
                }

                response.status.value in HTTP_CLIENT_ERROR_RANGE -> {
                    val errorResponse =
                        try {
                            Oid4vciJson.lenient.decodeFromString(Oid4vciErrorResponse.serializer(), body)
                        } catch (expected: Exception) {
                            log.debug("Failed to parse deferred credential error response JSON: ${expected.message}")
                            null
                        }
                    val dpopNonce = response.headers["DPoP-Nonce"]
                    val wwwAuthenticate = response.headers["WWW-Authenticate"].orEmpty()
                    if (dpopNonce != null && (errorResponse?.error == "use_dpop_nonce" || wwwAuthenticate.contains("use_dpop_nonce"))) {
                        return Err(
                            IdkError(
                                code = "use_dpop_nonce",
                                message =
                                    IdkError.Message(
                                        i18nKey = "use_dpop_nonce",
                                        defaultMessage = "Deferred credential endpoint requires nonce in DPoP proof",
                                    ),
                                meta = mapOf("dpop_nonce" to dpopNonce),
                            ),
                        )
                    }
                    val errorMsg =
                        errorResponse?.let {
                            "${it.error}: ${it.errorDescription ?: ""}"
                        } ?: "HTTP ${response.status.value}: $body"
                    Err(
                        IdkError.fromString(
                            message = "Deferred credential request failed: $errorMsg",
                            code = errorResponse?.error ?: "DEFERRED_CREDENTIAL_FAILED",
                        ),
                    )
                }

                else -> {
                    Err(
                        IdkError.fromString(
                            message = "Deferred credential endpoint returned unexpected status ${response.status.value}: $body",
                            code = "DEFERRED_CREDENTIAL_FAILED",
                        ),
                    )
                }
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error polling deferred credential from ${applied.deferredCredentialEndpoint}: ${expected.message}",
                    code = "DEFERRED_CREDENTIAL_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        } finally {
            httpClient.close()
        }
    }

    /**
     * Returns true when the response appears to be a JWE compact serialization.
     * Detects via Content-Type "application/jwt" combined with valid compact JWE format.
     */
    private fun isJweResponse(
        contentType: String?,
        body: String,
    ): Boolean {
        if (contentType != null && contentType.contains("application/jwt", ignoreCase = true)) {
            return JweCompact.isValidCompactFormat(body.trim())
        }
        return false
    }

    /**
     * Encrypts the request body as a JWE compact serialization if encryption parameters are provided.
     *
     * @return A pair of (body, contentType) or null if encryption fails.
     *         When no encryption is requested, returns the original JSON with application/json.
     */
    private suspend fun encryptRequestIfNeeded(
        jsonBody: String,
        jwk: kotlinx.serialization.json.JsonObject?,
        alg: String?,
        enc: String?,
    ): Pair<String, ContentType>? {
        if (jwk == null || alg == null || enc == null) {
            return jsonBody to ContentType.Application.Json
        }

        val recipientJwk =
            Jwk.tryFromJsonObject(jwk).getOrElse { error ->
                log.warn("Invalid JWK for request encryption: ${error.message.defaultMessage}")
                return null
            }

        val prepareArgs =
            PrepareJweArgs(
                plaintext = jsonBody.encodeToByteArray(),
                recipient = ManagedOptsJwk(identifier = recipientJwk),
                keyEncryptionAlg = alg,
                contentEncryptionAlg = enc,
            )

        val preparedJwe =
            jweService.prepareJwe(prepareArgs).getOrElse { error ->
                log.warn("Failed to prepare JWE for request encryption: ${error.message.defaultMessage}")
                return null
            }

        val jweCompact =
            jweService.createJweCompact(CreateJweCompactArgs(preparedJwe = preparedJwe)).getOrElse { error ->
                log.warn("Failed to create JWE compact for request encryption: ${error.message.defaultMessage}")
                return null
            }

        return jweCompact.serialize() to ContentType.parse("application/jwt")
    }

    /**
     * Decrypts a JWE compact body using [DecryptJweCommand].
     * Returns the plaintext string, or null if decryption is not possible.
     */
    private suspend fun decryptJweBody(
        jweBody: String,
        args: RequestDeferredCredentialArgs,
    ): String? {
        val decryptionKey = args.decryptionKey
        if (decryptionKey == null) {
            log.warn("Encrypted deferred credential response received but no decryptionKey provided in RequestDeferredCredentialArgs")
            return null
        }
        val jwe =
            try {
                JweCompact.parse(jweBody.trim())
            } catch (expected: Exception) {
                log.warn("Failed to parse JWE compact response: ${expected.message}")
                return null
            }
        val decryptResult =
            decryptJweCommand.execute(
                DecryptJweArgs(jwe = jwe, decryptor = decryptionKey),
            )
        return if (decryptResult.isOk) {
            decryptResult.value?.plaintext?.decodeToString()
        } else {
            log.warn("JWE decryption failed: ${decryptResult.error?.message}")
            null
        }
    }

    private companion object {
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
    }
}
