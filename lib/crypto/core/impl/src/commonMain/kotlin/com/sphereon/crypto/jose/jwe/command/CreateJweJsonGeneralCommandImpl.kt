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
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralCommand
import com.sphereon.crypto.jose.jwe.JweHeader
import com.sphereon.crypto.jose.jwe.JweJsonGeneral
import com.sphereon.crypto.jose.jwe.JweRecipient
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Command implementation for creating general JSON JWE with multiple recipients
 *
 * This command creates a general JSON JWE serialization for multi-recipient scenarios:
 * - The same plaintext is encrypted once with a random CEK
 * - The CEK is encrypted separately for each recipient using their public key
 * - This allows efficient multi-recipient encryption without duplicating the ciphertext
 * - Each recipient can decrypt using their own private key
 *
 * Use cases:
 * - Broadcasting encrypted data to multiple recipients
 * - Group messaging with different key types per recipient
 * - Hybrid encryption scenarios (e.g., RSA for some, ECDH for others)
 *
 * The general format includes:
 * - protected: Base64url-encoded JWE Protected Header (shared)
 * - unprotected: Optional JWE Shared Unprotected Header
 * - recipients: Array of per-recipient encrypted keys
 * - iv: Base64url-encoded Initialization Vector (shared)
 * - ciphertext: Base64url-encoded ciphertext (shared)
 * - tag: Base64url-encoded Authentication Tag (shared)
 * - aad: Optional base64url-encoded Additional Authenticated Data
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonGeneralCommandImpl", exact = true)
class CreateJweJsonGeneralCommandImpl(
    execution: SessionExecution,
    private val keyManagerService: KeyManagerService,
    private val identifierService: MultiManagedIdentifierService,
) : TypedServiceCommandAdapter<CreateJweJsonGeneralArgs, JweJsonGeneral>(
        commandId = CreateJweJsonGeneralCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateJweJsonGeneralArgs>(),
        outputTypeToken = typeToken<JweJsonGeneral>(),
    ),
    CreateJweJsonGeneralCommand {
    override val commandId: String get() = CreateJweJsonGeneralCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateJweJsonGeneralArgs,
        applyDuring: (CreateJweJsonGeneralArgs) -> CreateJweJsonGeneralArgs,
    ): IdkResult<JweJsonGeneral, IdkError> {
        val appliedArgs = applyDuring(args)

        // Validate required fields
        val preparedJwe = appliedArgs.preparedJwe ?: return IdkResult.err(IdkError.fromString("PreparedJwe is required"))
        val plaintext = preparedJwe.plaintext ?: return IdkResult.err(IdkError.fromString("Plaintext is required"))
        val cek = preparedJwe.cek ?: return IdkResult.err(IdkError.fromString("CEK is required"))
        val firstRecipient = preparedJwe.recipient ?: return IdkResult.err(IdkError.fromString("Recipient is required"))
        val keyAlgStr = preparedJwe.header.alg ?: return IdkResult.err(IdkError.fromString("alg header is required"))
        val encAlgStr = preparedJwe.header.enc ?: return IdkResult.err(IdkError.fromString("enc header is required"))

        val encAlg =
            ContentEncryptionAlgorithm.fromIdentifier(encAlgStr)
                ?: return IdkResult.err(IdkError.fromString("Unsupported content encryption algorithm: $encAlgStr"))

        // Create a mutable copy of the header for potential modification
        val header = JweHeader(preparedJwe.header.underlying)

        // Step 1: Construct Additional Authenticated Data (AAD)
        // For multi-recipient, AAD is based on the shared protected header (without recipient-specific elements like epk)
        val aad = appliedArgs.aad ?: constructAAD(header)

        // Step 2: Create a temporary KeyInfo for the CEK
        val cekInfo = createCEKInfo(cek)

        // Step 3: Encrypt the plaintext once using the CEK (shared for all recipients)
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

        // Step 4: Wrap CEK for each recipient
        val recipients = mutableListOf<JweRecipient>()

        // First recipient (from preparedJwe)
        val firstRecipientResult =
            wrapKeyForRecipient(
                cek = cek,
                recipient = firstRecipient,
                algorithm = keyAlgStr,
                perRecipientHeader = null,
            ).getOrElse { error -> return Err(error) }
        recipients.add(firstRecipientResult)

        // Additional recipients
        appliedArgs.additionalRecipients?.forEach { recipientInfo ->
            val recipient = recipientInfo.recipient ?: return IdkResult.err(IdkError.fromString("Recipient is required for each recipient info"))
            val additionalRecipient =
                wrapKeyForRecipient(
                    cek = cek,
                    recipient = recipient,
                    algorithm = keyAlgStr,
                    perRecipientHeader = recipientInfo.perRecipientHeader,
                ).getOrElse { error -> return Err(error) }
            recipients.add(additionalRecipient)
        }

        // Step 5: Serialize to JSON general format
        val headerJson = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), header.underlying)
        val protectedB64 = headerJson.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        val ivB64 = iv.encodeTo(Encoding.BASE64URL)
        val ciphertextB64 = ciphertext.encodeTo(Encoding.BASE64URL)
        val tagB64 = authTag.encodeTo(Encoding.BASE64URL)
        val aadB64 = appliedArgs.aad?.encodeTo(Encoding.BASE64URL)

        val jweJsonGeneral =
            JweJsonGeneral(
                protected = protectedB64,
                unprotected = null, // Unprotected header not currently passed through PreparedJwe
                recipients = recipients.toTypedArray(),
                iv = ivB64,
                ciphertext = ciphertextB64,
                tag = tagB64,
                aad = aadB64,
            )

        return jweJsonGeneral.asOkResult()
    }

    /**
     * Result of wrapping the CEK for a recipient.
     */
    private data class RecipientWrapResult(
        val encryptedKey: ByteArray,
        val perRecipientHeader: JsonObject?,
    )

    /**
     * Wraps the CEK for a specific recipient.
     *
     * For standard key wrapping (RSA-OAEP, AES-KW, etc.), wraps the CEK with recipient's public key.
     * For ECDH-ES algorithms, generates ephemeral key pair and derives wrapping key.
     *
     * @param cek The Content Encryption Key to wrap
     * @param recipient The recipient's identifier/key info
     * @param algorithm The key encryption algorithm identifier
     * @param perRecipientHeader Optional per-recipient unprotected header
     * @return JweRecipient with encrypted key and per-recipient header
     */
    private suspend fun wrapKeyForRecipient(
        cek: ByteArray,
        recipient: ManagedIdentifierOptsOrResult,
        algorithm: String,
        perRecipientHeader: JweHeader?,
    ): IdkResult<JweRecipient, IdkError> {
        // Resolve the recipient identifier to get public key
        val identifierResult = identifierService.resolve(recipient)
        if (!identifierResult.isOk) {
            return Err(IdkError.fromDTO(identifierResult.error))
        }
        val resolvedRecipient = identifierResult.value

        val keyAgreementAlg = KeyAgreementAlgorithm.fromIdentifier(algorithm)
        val keyWrapAlg = KeyWrapAlgorithm.fromIdentifier(algorithm)

        val wrapResult: RecipientWrapResult

        if (keyAgreementAlg != null) {
            // ECDH-ES algorithm - perform key agreement for this recipient
            wrapResult =
                performEcdhEsForRecipient(
                    keyAgreementAlg = keyAgreementAlg,
                    encAlgStr = algorithm, // Not used for key wrapping variants
                    recipient = resolvedRecipient,
                    cek = cek,
                    perRecipientHeader = perRecipientHeader,
                ).getOrElse { error -> return Err(error) }
        } else if (keyWrapAlg != null) {
            // Standard key wrapping algorithm
            val encryptedKey =
                if (keyWrapAlg == KeyWrapAlgorithm.DIR) {
                    // Direct encryption - no key wrapping, CEK is used directly
                    ByteArray(0)
                } else {
                    // Wrap the CEK using recipient's public key
                    keyManagerService.wrapKey(
                        wrappingKeyInfo = resolvedRecipient.asResult().keyInfo,
                        keyToWrap = cek,
                        algorithm = keyWrapAlg,
                    )
                }
            // Build per-recipient header with kid for recipient matching
            val recipientKeyInfo = resolvedRecipient.asResult().keyInfo
            val headerWithKid =
                if (recipientKeyInfo.kid != null || perRecipientHeader != null) {
                    val header = JweHeader()
                    perRecipientHeader?.underlying?.let { existing ->
                        // Copy existing per-recipient header values
                        existing.forEach { (key, value) ->
                            header.put(key, value)
                        }
                    }
                    // Add kid from key info if available
                    recipientKeyInfo.kid?.let { header.kid = it }
                    header.underlying
                } else {
                    null
                }
            wrapResult =
                RecipientWrapResult(
                    encryptedKey = encryptedKey,
                    perRecipientHeader = headerWithKid,
                )
        } else {
            return IdkResult.err(IdkError.fromString("Unsupported key encryption algorithm: $algorithm"))
        }

        // Construct JweRecipient
        val encryptedKeyB64 = wrapResult.encryptedKey.encodeTo(Encoding.BASE64URL)
        return JweRecipient(
            header = wrapResult.perRecipientHeader,
            encrypted_key = encryptedKeyB64,
        ).asOkResult()
    }

    /**
     * Performs ECDH-ES key agreement for a specific recipient in multi-recipient scenario.
     *
     * For multi-recipient JWE with ECDH-ES, each recipient gets their own ephemeral key pair
     * and the epk is placed in the per-recipient header rather than the protected header.
     */
    private suspend fun performEcdhEsForRecipient(
        keyAgreementAlg: KeyAgreementAlgorithm,
        encAlgStr: String,
        recipient: ManagedIdentifierResult<*>,
        cek: ByteArray,
        perRecipientHeader: JweHeader?,
    ): IdkResult<RecipientWrapResult, IdkError> {
        // Get recipient's public key
        val recipientKeyInfo = recipient.keyInfo
        val recipientJwk =
            recipientKeyInfo.key as? Jwk
                ?: return IdkResult.err(IdkError.fromString("Recipient key must be a JWK for ECDH-ES"))

        // Validate recipient key is EC
        if (recipientJwk.kty != JwaKeyType.EC) {
            return IdkResult.err(IdkError.fromString("Recipient key must be an EC key for ECDH-ES, got: ${recipientJwk.kty}"))
        }

        // Get curve from recipient's key
        val curve = EcdhUtils.getCurveFromJwk(recipientJwk)

        // Generate ephemeral key pair for this recipient
        val ephemeralKeyPair = EcdhUtils.generateEphemeralKeyPair(curve)

        // Build per-recipient header with epk
        val recipientHeader = JweHeader()
        if (perRecipientHeader != null) {
            // Copy existing per-recipient header values
            perRecipientHeader.alg?.let { recipientHeader.alg = it }
            perRecipientHeader.kid?.let { recipientHeader.kid = it }
            perRecipientHeader.apu?.let { recipientHeader.apu = it }
            perRecipientHeader.apv?.let { recipientHeader.apv = it }
        }
        // Add algorithm and ephemeral public key to per-recipient header
        recipientHeader.alg = keyAgreementAlg.identifier
        recipientHeader.epk = ephemeralKeyPair.publicKeyJwk

        // Perform ECDH key agreement
        val sharedSecret =
            EcdhUtils.performKeyAgreement(
                ephemeralPrivateKeyDer = ephemeralKeyPair.privateKeyDer,
                recipientPublicKeyJwk = recipientJwk,
                curve = curve,
            )

        // Get apu and apv from per-recipient header (if present)
        val apu = recipientHeader.apu?.let { it.decodeFrom(Encoding.BASE64URL) } ?: ByteArray(0)
        val apv = recipientHeader.apv?.let { it.decodeFrom(Encoding.BASE64URL) } ?: ByteArray(0)

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

        val encryptedKey =
            if (keyAgreementAlg.requiresKeyWrap) {
                // ECDH-ES+AxxxKW: Use derived key to wrap the CEK
                wrapCekWithDerivedKey(derivedKey, cek, keyAgreementAlg)
            } else {
                // ECDH-ES: Direct key derivation, no wrapping needed
                // Note: For pure ECDH-ES in multi-recipient, the CEK should be derived, not wrapped
                // This is typically not used in multi-recipient scenarios as CEK must be shared
                ByteArray(0)
            }

        return RecipientWrapResult(
            encryptedKey = encryptedKey,
            perRecipientHeader = recipientHeader.underlying,
        ).asOkResult()
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
