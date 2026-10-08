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
import com.sphereon.crypto.jose.jwe.CreateJweOpts
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Result of [CredentialResponseEncryptor.encryptIfRequested]. Per OID4VCI 1.0 §8.3.5 the
 * encrypted form is the entire HTTP response body — a single JWE-compact string with
 * `Content-Type: application/jwt` — so it cannot be carried inside a [CredentialResponse]
 * JSON envelope. Callers (the HTTP endpoint) match on this type to render the right body.
 */
sealed class MaybeEncryptedCredentialResponse {
    data class Plain(
        val response: CredentialResponse
    ) : MaybeEncryptedCredentialResponse()

    data class Encrypted(
        val jweCompact: String
    ) : MaybeEncryptedCredentialResponse()
}

/**
 * Encrypts OID4VCI credential responses when the client requests response encryption.
 *
 * Per OID4VCI 1.0 §8.3.5, the credential issuer encrypts the entire credential response JSON
 * as a JWE compact serialization using the recipient's public key from
 * [RequestedCredentialResponseEncryption.jwk]. When no encryption is requested, the response
 * passes through unchanged.
 */
@Inject
@SingleIn(SessionScope::class)
class CredentialResponseEncryptor(
    private val jweService: JweService,
    private val configProvider: Oid4vciIssuerConfigProvider,
) {
    /**
     * Encrypts the credential response if encryption was requested.
     *
     * @param response The credential response to potentially encrypt
     * @param encryption The client's requested encryption parameters, or null if no encryption requested
     * @return [MaybeEncryptedCredentialResponse.Plain] when encryption was not requested,
     *   otherwise [MaybeEncryptedCredentialResponse.Encrypted] carrying the JWE compact string.
     */
    suspend fun encryptIfRequested(
        response: CredentialResponse,
        encryption: RequestedCredentialResponseEncryption?,
    ): IdkResult<MaybeEncryptedCredentialResponse, IdkError> {
        if (encryption == null) {
            return Ok(MaybeEncryptedCredentialResponse.Plain(response))
        }

        // OID4VCI 1.0 §10 + §11.2: the wallet's `credential_response_encryption.{alg, enc, zip}`
        // values MUST be in the issuer's advertised supported lists, otherwise the issuer MUST
        // reject with `invalid_encryption_parameters` (HTTP 400). Without these checks an
        // unsupported alg would either fail deeper in the JWE pipeline (mapped to a generic
        // 5xx) or — worse — silently encrypt with a non-advertised algorithm. The metadata
        // values come from `Oid4vciIssuerConfigProvider`; when not configured the issuer is
        // signalling "no encryption support" (see `BuildIssuerMetadataCommandImpl`), and any
        // request asking for encryption is by definition asking for unsupported parameters.
        val supported =
            configProvider.credentialResponseEncryption
                ?: return invalidEncryptionParameters("Issuer does not advertise credential response encryption support")

        // Parse the recipient's public JWK from the request
        val recipientJwk =
            Jwk.tryFromJsonObject(encryption.jwk).getOrElse { error ->
                return invalidEncryptionParameters("Invalid JWK in credential_response_encryption: ${error.message.defaultMessage}")
            }

        // Determine key encryption algorithm: use explicit alg if provided, otherwise
        // fall back to the algorithm embedded in the JWK itself
        val keyEncryptionAlg =
            encryption.alg
                ?: recipientJwk.alg?.value
                ?: return invalidEncryptionParameters(
                    "Key encryption algorithm (alg) must be specified either in the encryption request or in the JWK",
                )

        if (keyEncryptionAlg !in supported.algValuesSupported) {
            return invalidEncryptionParameters(
                "Key encryption algorithm '$keyEncryptionAlg' is not in the issuer-supported list ${supported.algValuesSupported}",
            )
        }
        if (encryption.enc !in supported.encValuesSupported) {
            return invalidEncryptionParameters(
                "Content encryption algorithm '${encryption.enc}' is not in the issuer-supported list ${supported.encValuesSupported}",
            )
        }
        val zip = encryption.zip
        if (zip != null) {
            val supportedZip = supported.zipValuesSupported.orEmpty()
            if (zip !in supportedZip) {
                return invalidEncryptionParameters(
                    "Compression algorithm '$zip' is not in the issuer-supported list $supportedZip",
                )
            }
        }

        // Serialize the full credential response to JSON bytes
        val responseJson = Oid4vciJson.lenientNoDefaults.encodeToString(CredentialResponse.serializer(), response)
        val plaintext = responseJson.encodeToByteArray()

        // Prepare the JWE with the recipient's public key. When the wallet asked for `zip=DEF`
        // (RFC 7516 §4.1.3) AND the issuer advertises support for it (validated above), flip
        // the JWE pipeline's compress flag so the plaintext is DEFLATE-compressed before
        // encryption and the protected header carries `"zip":"DEF"`. Without this, OIDF
        // `VCICheckCredentialResponseCompression` flags the metadata-vs-actual inconsistency:
        // we say we can compress, the wallet asks us to, and we don't.
        val prepareArgs =
            PrepareJweArgs(
                plaintext = plaintext,
                recipient = ManagedOptsJwk(identifier = recipientJwk),
                keyEncryptionAlg = keyEncryptionAlg,
                contentEncryptionAlg = encryption.enc,
                opts = CreateJweOpts(compress = zip == "DEF"),
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

        return Ok(MaybeEncryptedCredentialResponse.Encrypted(jweCompact.serialize()))
    }

    /**
     * Build an [IdkError] whose `code` is the OID4VCI `invalid_encryption_parameters` literal so
     * `Oid4vciErrorRenderer` (`mapOid4vciError` in `Oid4vciProtocolUtils`) renders it as the
     * spec-correct HTTP 400 + `{"error":"invalid_encryption_parameters", ...}` body.
     */
    private fun invalidEncryptionParameters(message: String): IdkResult<MaybeEncryptedCredentialResponse, IdkError> =
        Err(IdkError.fromString(message = message, code = Oid4vciErrors.INVALID_ENCRYPTION_PARAMETERS))
}
