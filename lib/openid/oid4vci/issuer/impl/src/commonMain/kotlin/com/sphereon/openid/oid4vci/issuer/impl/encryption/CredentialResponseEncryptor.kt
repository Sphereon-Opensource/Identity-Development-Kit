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

package com.sphereon.openid.oid4vci.issuer.impl.encryption

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonPrimitive

/**
 * Encrypts OID4VCI credential responses when the client requests response encryption.
 *
 * Per OID4VCI 1.0 Section 8.3 / 1.1 Section 9.2, the credential issuer encrypts
 * the entire credential response JSON as a JWE compact serialization using the
 * recipient's public key from [RequestedCredentialResponseEncryption.jwk].
 *
 * When no encryption is requested (null encryption parameter), the response passes
 * through unchanged.
 */
@Inject
@SingleIn(SessionScope::class)
class CredentialResponseEncryptor(
    private val jweService: JweService,
) {
    /**
     * Encrypts the credential response if encryption was requested.
     *
     * @param response The credential response to potentially encrypt
     * @param encryption The client's requested encryption parameters, or null if no encryption requested
     * @return The original response (if no encryption) or a response with credential replaced by the JWE compact string
     */
    suspend fun encryptIfRequested(
        response: CredentialResponse,
        encryption: RequestedCredentialResponseEncryption?,
    ): IdkResult<CredentialResponse, IdkError> {
        if (encryption == null) {
            return Ok(response)
        }

        // Parse the recipient's public JWK from the request
        val recipientJwk =
            Jwk.tryFromJsonObject(encryption.jwk).getOrElse { error ->
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Invalid JWK in credential_response_encryption: ${error.message.defaultMessage}",
                    ),
                )
            }

        // Determine key encryption algorithm: use explicit alg if provided, otherwise
        // fall back to the algorithm embedded in the JWK itself
        val keyEncryptionAlg =
            encryption.alg
                ?: recipientJwk.alg?.value
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Key encryption algorithm (alg) must be specified either in the encryption request or in the JWK",
                    ),
                )

        // Serialize the full credential response to JSON bytes
        val responseJson = Oid4vciJson.lenientNoDefaults.encodeToString(CredentialResponse.serializer(), response)
        val plaintext = responseJson.encodeToByteArray()

        // Prepare the JWE with the recipient's public key
        val prepareArgs =
            PrepareJweArgs(
                plaintext = plaintext,
                recipient = ManagedOptsJwk(identifier = recipientJwk),
                keyEncryptionAlg = keyEncryptionAlg,
                contentEncryptionAlg = encryption.enc,
            )

        val preparedJwe =
            jweService.prepareJwe(prepareArgs).getOrElse { error ->
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to prepare JWE for credential response encryption: ${error.message.defaultMessage}",
                    ),
                )
            }

        // Create JWE compact serialization
        val createArgs = CreateJweCompactArgs(preparedJwe = preparedJwe)
        val jweCompact =
            jweService.createJweCompact(createArgs).getOrElse { error ->
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to create JWE compact for credential response encryption: ${error.message.defaultMessage}",
                    ),
                )
            }

        // Per OID4VCI spec, the encrypted response replaces the entire response
        // with the JWE compact string as the credential value
        return Ok(
            CredentialResponse(
                credential = JsonPrimitive(jweCompact.serialize()),
            ),
        )
    }
}
