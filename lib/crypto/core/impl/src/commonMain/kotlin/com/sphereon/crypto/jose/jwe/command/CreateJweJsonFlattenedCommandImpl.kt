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

package com.sphereon.crypto.jose.jwe.command

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import com.sphereon.crypto.core.kms.ConcatKdf
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EcdhUtils
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.jose.jwe.CreateJweJsonArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonFlattenedCommand
import com.sphereon.crypto.jose.jwe.JweHeader
import com.sphereon.crypto.jose.jwe.JweJsonFlattened
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Command implementation for creating flattened JSON JWE
 *
 * This command creates a flattened JSON JWE serialization, which is used for:
 * - Single recipient scenarios where JSON format is preferred
 * - Including unprotected headers that are not integrity protected
 * - Better readability and debugging compared to compact format
 *
 * The flattened format includes:
 * - protected: Base64url-encoded JWE Protected Header
 * - unprotected: Optional JWE Shared Unprotected Header (not integrity protected)
 * - encrypted_key: Base64url-encoded encrypted Content Encryption Key
 * - iv: Base64url-encoded Initialization Vector
 * - ciphertext: Base64url-encoded ciphertext
 * - tag: Base64url-encoded Authentication Tag
 * - aad: Optional base64url-encoded Additional Authenticated Data
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonFlattenedCommandImpl", exact = true)
class CreateJweJsonFlattenedCommandImpl(
    execution: SessionExecution,
    private val keyManagerService: KeyManagerService,
) : TypedServiceCommandAdapter<CreateJweJsonArgs, JweJsonFlattened, IdkError>(
        commandId = CreateJweJsonFlattenedCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateJweJsonArgs>(),
        outputTypeToken = typeToken<JweJsonFlattened>(),
    ),
    CreateJweJsonFlattenedCommand {
    override val commandId: String get() = CreateJweJsonFlattenedCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateJweJsonArgs,
        applyDuring: (CreateJweJsonArgs) -> CreateJweJsonArgs,
    ): IdkResult<JweJsonFlattened, IdkError> {
        val appliedArgs = applyDuring(args)

        // Validate required fields
        val preparedJwe = appliedArgs.preparedJwe ?: return IdkResult.err(IdkError.fromString("PreparedJwe is required"))
        val plaintext = preparedJwe.plaintext ?: return IdkResult.err(IdkError.fromString("Plaintext is required"))
        var cek = preparedJwe.cek ?: return IdkResult.err(IdkError.fromString("CEK is required"))
        val recipient = preparedJwe.recipient ?: return IdkResult.err(IdkError.fromString("Recipient is required"))
        val keyAlgStr = preparedJwe.header.alg ?: return IdkResult.err(IdkError.fromString("alg header is required"))
        val encAlgStr = preparedJwe.header.enc ?: return IdkResult.err(IdkError.fromString("enc header is required"))

        val encAlg =
            ContentEncryptionAlgorithm.fromIdentifier(encAlgStr)
                ?: return IdkResult.err(IdkError.fromString("Unsupported content encryption algorithm: $encAlgStr"))

        // Check if this is an ECDH-ES algorithm
        val keyAgreementAlg = KeyAgreementAlgorithm.fromIdentifier(keyAlgStr)
        val keyWrapAlg = KeyWrapAlgorithm.fromIdentifier(keyAlgStr)

        // Create a mutable copy of the header for potential modification (epk for ECDH-ES)
        val header = JweHeader(preparedJwe.header.underlying)
        val encryptedKey: ByteArray

        if (keyAgreementAlg != null) {
            // ECDH-ES algorithm - perform key agreement
            val ecdhResult =
                performEcdhEs(
                    keyAgreementAlg = keyAgreementAlg,
                    encAlgStr = encAlgStr,
                    recipient = recipient,
                    header = header,
                    cek = cek,
                ).getOrElse { error -> return Err(error) }

            cek = ecdhResult.cek
            encryptedKey = ecdhResult.encryptedKey
        } else if (keyWrapAlg != null) {
            // Standard key wrapping algorithm
            encryptedKey =
                if (keyWrapAlg == KeyWrapAlgorithm.DIR) {
                    // Direct encryption - CEK is derived from shared secret, no key wrapping needed
                    ByteArray(0)
                } else {
                    // Wrap the CEK using recipient's public key
                    keyManagerService.wrapKey(
                        wrappingKeyInfo = recipient.asResult().keyInfo,
                        keyToWrap = cek,
                        algorithm = keyWrapAlg,
                    )
                }
        } else {
            return IdkResult.err(IdkError.fromString("Unsupported key encryption algorithm: $keyAlgStr"))
        }

        // Step 2: Construct Additional Authenticated Data (AAD)
        val aad = appliedArgs.aad ?: constructAAD(header)

        // Step 3: Create a temporary KeyInfo for the CEK
        val cekInfo = createCEKInfo(cek)

        // Step 4: Encrypt the plaintext using the CEK
        val encryptionResult =
            keyManagerService.encrypt(
                keyInfo = cekInfo,
                plaintext = plaintext,
                algorithm = encAlg,
                additionalAuthenticatedData = aad,
            )

        // Extract components from EncryptionResult
        val iv = encryptionResult.iv
        val authTag = encryptionResult.authTag
        val ciphertext = encryptionResult.ciphertext

        // Step 5: Serialize to JSON flattened format
        val headerJson = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), header.underlying)
        val protectedB64 = headerJson.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        val encryptedKeyB64 = encryptedKey.encodeTo(Encoding.BASE64URL)
        val ivB64 = iv.encodeTo(Encoding.BASE64URL)
        val ciphertextB64 = ciphertext.encodeTo(Encoding.BASE64URL)
        val tagB64 = authTag.encodeTo(Encoding.BASE64URL)
        val aadB64 = appliedArgs.aad?.encodeTo(Encoding.BASE64URL)

        val jweJsonFlattened =
            JweJsonFlattened(
                protected = protectedB64,
                unprotected = null, // Unprotected header not currently passed through PreparedJwe
                encrypted_key = encryptedKeyB64,
                iv = ivB64,
                ciphertext = ciphertextB64,
                tag = tagB64,
                aad = aadB64,
            )

        return jweJsonFlattened.asOkResult()
    }

    /**
     * Result of ECDH-ES key agreement.
     */
    private data class EcdhResult(
        val cek: ByteArray,
        val encryptedKey: ByteArray,
    )

    /**
     * Performs ECDH-ES key agreement and derives the CEK or key-wrapping key.
     */
    private suspend fun performEcdhEs(
        keyAgreementAlg: KeyAgreementAlgorithm,
        encAlgStr: String,
        recipient: com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult,
        header: JweHeader,
        cek: ByteArray,
    ): IdkResult<EcdhResult, IdkError> {
        // Get recipient's public key
        val recipientKeyInfo = recipient.asResult().keyInfo
        val recipientJwk =
            recipientKeyInfo.key as? Jwk
                ?: return IdkResult.err(IdkError.fromString("Recipient key must be a JWK for ECDH-ES"))

        // Validate recipient key is EC
        if (recipientJwk.kty != JwaKeyType.EC) {
            return IdkResult.err(IdkError.fromString("Recipient key must be an EC key for ECDH-ES, got: ${recipientJwk.kty}"))
        }

        // Get curve from recipient's key
        val curve = EcdhUtils.getCurveFromJwk(recipientJwk)

        // Generate ephemeral key pair
        val ephemeralKeyPair = EcdhUtils.generateEphemeralKeyPair(curve)

        // Add ephemeral public key to header
        header.epk = ephemeralKeyPair.publicKeyJwk

        // Perform ECDH key agreement
        val sharedSecret =
            EcdhUtils.performKeyAgreement(
                ephemeralPrivateKeyDer = ephemeralKeyPair.privateKeyDer,
                recipientPublicKeyJwk = recipientJwk,
                curve = curve,
            )

        // Get apu and apv from header (if present)
        val apu = header.apu?.let { it.decodeFrom(Encoding.BASE64URL) } ?: ByteArray(0)
        val apv = header.apv?.let { it.decodeFrom(Encoding.BASE64URL) } ?: ByteArray(0)

        // Determine key length and algorithm ID for Concat KDF
        val keyLengthBits = ConcatKdf.getKeyLengthBits(keyAgreementAlg, encAlgStr)
        val algorithmId = ConcatKdf.getAlgorithmId(keyAgreementAlg, encAlgStr)

        // Apply Concat KDF to derive the key
        val derivedKey =
            ConcatKdf.deriveKey(
                sharedSecret = sharedSecret,
                keyDataLen = keyLengthBits,
                algorithmId = algorithmId,
                apu = apu,
                apv = apv,
            )

        return if (keyAgreementAlg.requiresKeyWrap) {
            // ECDH-ES+AxxxKW: Use derived key to wrap the CEK
            val wrappedCek = wrapCekWithDerivedKey(derivedKey, cek, keyAgreementAlg)
            IdkResult.ok(EcdhResult(cek = cek, encryptedKey = wrappedCek))
        } else {
            // ECDH-ES: Use derived key directly as CEK
            IdkResult.ok(EcdhResult(cek = derivedKey, encryptedKey = ByteArray(0)))
        }
    }

    /**
     * Wraps the CEK using AES Key Wrap with the derived key.
     */
    private suspend fun wrapCekWithDerivedKey(
        derivedKey: ByteArray,
        cek: ByteArray,
        algorithm: KeyAgreementAlgorithm,
    ): ByteArray {
        // Determine the AES-KW algorithm based on ECDH-ES variant
        val aesKwAlg =
            when (algorithm) {
                KeyAgreementAlgorithm.ECDH_ES_A128KW -> KeyWrapAlgorithm.A128KW
                KeyAgreementAlgorithm.ECDH_ES_A192KW -> KeyWrapAlgorithm.A192KW
                KeyAgreementAlgorithm.ECDH_ES_A256KW -> KeyWrapAlgorithm.A256KW
                else -> throw IllegalArgumentException("Algorithm $algorithm does not require key wrapping")
            }

        // Create a KeyInfo for the derived key
        val derivedKeyInfo = createCEKInfo(derivedKey)

        // Use KMS to wrap the CEK
        return keyManagerService.wrapKey(
            wrappingKeyInfo = derivedKeyInfo,
            keyToWrap = cek,
            algorithm = aesKwAlg,
        )
    }

    /**
     * Constructs Additional Authenticated Data (AAD) from JWE header
     * AAD = ASCII(BASE64URL(UTF8(JWE Protected Header)))
     */
    private fun constructAAD(header: JweHeader): ByteArray {
        // Serialize header to JSON
        val headerJson = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), header.underlying)

        // Encode as BASE64URL
        val base64Url = headerJson.encodeToByteArray().encodeTo(Encoding.BASE64URL)

        // Return as ASCII bytes
        return base64Url.encodeToByteArray()
    }

    /**
     * Creates a temporary KeyInfo for the Content Encryption Key (CEK)
     */
    private fun createCEKInfo(cek: ByteArray): KeyInfoType<*> {
        // Create a symmetric JWK for the CEK
        val jwk =
            Jwk(
                kty = JwaKeyType.oct, // Octet sequence (symmetric key)
                k = cek.encodeTo(Encoding.BASE64URL), // Base64url encoded key material
            )

        // Return KeyInfo with the symmetric key
        return KeyInfo(
            key = jwk,
            keyVisibility = KeyVisibility.PRIVATE,
        )
    }
}
