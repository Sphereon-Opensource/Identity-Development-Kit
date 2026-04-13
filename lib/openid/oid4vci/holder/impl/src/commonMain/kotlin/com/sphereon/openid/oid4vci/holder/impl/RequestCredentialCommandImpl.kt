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
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrorResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.holder.RequestCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Requests a credential from the issuer's credential endpoint.
 *
 * Per OID4VCI 1.1 Section 9.2: HTTP POST to credential endpoint with Bearer auth.
 *
 * Response handling:
 * - HTTP 200 = immediate issuance  → parse as CredentialResponse
 * - HTTP 202 = deferred issuance   → parse as CredentialResponse (contains transaction_id + interval)
 * - HTTP 4xx = error               → parse as Oid4vciErrorResponse, return Err
 *
 * Additional features:
 * - JWE decryption (C-3): When the response Content-Type is application/jwt and
 *   [RequestCredentialArgs.decryptionKey] is provided, the JWE compact response is
 *   decrypted before parsing as CredentialResponse.
 * - invalid_nonce auto-fetch (C-4): When the issuer returns invalid_nonce and
 *   [RequestCredentialArgs.nonceEndpoint] is set, a fresh nonce is fetched and
 *   returned in the error so the caller can rebuild the proof and retry.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestCredentialCommand>())
class RequestCredentialCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val decryptJweCommand: DecryptJweCommand,
    private val jweService: JweService,
) : TypedServiceCommandAdapter<RequestCredentialArgs, CredentialResponse>(
        commandId = RequestCredentialCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RequestCredentialArgs>(),
        outputTypeToken = typeToken<CredentialResponse>(),
    ),
    RequestCredentialCommand {
    override val commandId: String get() = RequestCredentialCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RequestCredentialArgs

    override suspend fun doExecute(
        args: RequestCredentialArgs,
        applyDuring: (RequestCredentialArgs) -> RequestCredentialArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val applied = applyDuring(args)

        // Validate mutually exclusive fields
        if (applied.credentialConfigurationId != null && applied.credentialIdentifier != null) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "credential_configuration_id and credential_identifier are mutually exclusive",
                ),
            )
        }
        if (applied.credentialConfigurationId == null && applied.credentialIdentifier == null) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Either credential_configuration_id or credential_identifier must be provided",
                ),
            )
        }

        log.debug("Requesting credential from: ${applied.credentialEndpoint}")

        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to create HTTP client: ${expected.message}", throwable = expected))
            }

        return try {
            val credentialRequest =
                CredentialRequest(
                    credentialConfigurationId = applied.credentialConfigurationId,
                    credentialIdentifier = applied.credentialIdentifier,
                    proofs = applied.proofs,
                    credentialResponseEncryption = applied.credentialResponseEncryption,
                )

            val requestJson = Oid4vciJson.lenient.encodeToString(CredentialRequest.serializer(), credentialRequest)

            // Encrypt request body if request encryption parameters are provided
            val (requestBody, requestContentType) =
                encryptRequestIfNeeded(requestJson, applied.requestEncryptionJwk, applied.requestEncryptionAlg, applied.requestEncryptionEnc)
                    ?: return Err(
                        IdkError.fromString(
                            message = "Failed to encrypt credential request body",
                            code = "REQUEST_ENCRYPTION_FAILED",
                        ),
                    )

            val response =
                httpClient.post(applied.credentialEndpoint) {
                    contentType(requestContentType)
                    bearerAuth(applied.accessToken)
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
                                            "Encrypted credential response received but decryption failed. " +
                                                "Ensure decryptionKey is provided in RequestCredentialArgs.",
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
                                    message = "Failed to parse credential response JSON: ${expected.message}",
                                    throwable = expected,
                                ),
                            )
                        }
                    log.debug("Successfully received credential response (status: ${response.status.value})")
                    Ok(credentialResponse)
                }

                response.status.value in HTTP_CLIENT_ERROR_RANGE -> {
                    val errorResponse =
                        try {
                            Oid4vciJson.lenient.decodeFromString(Oid4vciErrorResponse.serializer(), body)
                        } catch (expected: Exception) {
                            log.debug("Failed to parse credential error response JSON: ${expected.message}")
                            null
                        }

                    // C-4: invalid_nonce auto-fetch — get fresh nonce so caller can retry
                    val nonceEndpoint = applied.nonceEndpoint
                    if (errorResponse?.error == Oid4vciErrors.INVALID_NONCE && nonceEndpoint != null) {
                        val freshNonce = fetchFreshNonce(nonceEndpoint, httpClient)
                        if (freshNonce != null) {
                            log.debug("Received invalid_nonce; fetched fresh c_nonce from $nonceEndpoint")
                            return Err(
                                IdkError.fromString(
                                    message = "invalid_nonce: retry with fresh c_nonce: $freshNonce",
                                    code = "INVALID_NONCE_FRESH_NONCE_AVAILABLE:$freshNonce",
                                ),
                            )
                        }
                        log.warn("Received invalid_nonce but failed to fetch fresh nonce from $nonceEndpoint")
                    }

                    val errorMsg =
                        errorResponse?.let {
                            "${it.error}: ${it.errorDescription ?: ""}"
                        } ?: "HTTP ${response.status.value}: $body"
                    Err(
                        IdkError.fromString(
                            message = "Credential request failed: $errorMsg",
                            code = errorResponse?.error ?: "CREDENTIAL_REQUEST_FAILED",
                        ),
                    )
                }

                else -> {
                    Err(
                        IdkError.fromString(
                            message = "Credential endpoint returned unexpected status ${response.status.value}: $body",
                            code = "CREDENTIAL_REQUEST_FAILED",
                        ),
                    )
                }
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Network error requesting credential from ${applied.credentialEndpoint}: ${expected.message}",
                    code = "CREDENTIAL_NETWORK_ERROR",
                    exception = expected,
                ),
            )
        } finally {
            httpClient.close()
        }
    }

    /**
     * Returns true when the response appears to be a JWE compact serialization.
     * Detects via Content-Type "application/jwt" or by checking the body structure
     * (5 base64url parts separated by dots) when encryption was requested.
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
     * Decrypts a JWE compact body using [DecryptJweCommand].
     * Returns the plaintext string, or null if decryption is not possible.
     */
    private suspend fun decryptJweBody(
        jweBody: String,
        args: RequestCredentialArgs,
    ): String? {
        val decryptionKey = args.decryptionKey
        if (decryptionKey == null) {
            log.warn("Encrypted credential response received but no decryptionKey provided in RequestCredentialArgs")
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
     * Fetches a fresh c_nonce from the nonce endpoint.
     * Returns the fresh nonce string, or null if the fetch fails.
     */
    private suspend fun fetchFreshNonce(
        nonceEndpoint: String,
        httpClient: io.ktor.client.HttpClient,
    ): String? {
        return try {
            val nonceResponse = httpClient.post(nonceEndpoint)
            if (!nonceResponse.status.isSuccess()) {
                log.warn("Nonce endpoint returned HTTP ${nonceResponse.status.value}")
                return null
            }
            val nonceBody = nonceResponse.bodyAsText()
            val parsed =
                try {
                    Oid4vciJson.lenient.decodeFromString(
                        com.sphereon.openid.oid4vci.common.model.NonceResponse
                            .serializer(),
                        nonceBody,
                    )
                } catch (expected: Exception) {
                    log.warn("Failed to parse nonce response: ${expected.message}")
                    return null
                }
            parsed.cNonce
        } catch (expected: Exception) {
            log.warn("Network error fetching fresh nonce from $nonceEndpoint: ${expected.message}")
            null
        }
    }

    private companion object {
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
    }
}
