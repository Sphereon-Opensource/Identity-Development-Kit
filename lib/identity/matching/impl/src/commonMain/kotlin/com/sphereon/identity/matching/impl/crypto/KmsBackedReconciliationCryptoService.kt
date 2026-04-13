/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.impl.crypto

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.command.GenerateMacArgs
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * KMS-backed implementation of [ReconciliationCryptoService].
 *
 * Delegates to the IDK [GenerateMacCommand] for HMAC and [KeyManagerService] for AES
 * operations using domain-separated keys:
 * - "reconciliation:holder" (Key A) for holder key identifiers
 * - "reconciliation:institution" (Key B) for institution/external identifiers
 * - "reconciliation:encryption" (Key C) for AES-256-GCM encryption
 *
 * @param generateMacCommand The IDK command for generating MACs
 * @param keyManagerService The IDK KMS service for encrypt/decrypt
 * @param holderKeyAlias The KMS alias for holder HMAC key (Key A)
 * @param institutionKeyAlias The KMS alias for institution HMAC key (Key B)
 * @param encryptionKeyAlias The KMS alias for AES-256-GCM encryption key (Key C)
 * @param holderKeyVersion Current version identifier for Key A
 * @param institutionKeyVersion Current version identifier for Key B
 * @param encryptionKeyVersion Current version identifier for Key C
 * @param previousHolderKeyAlias Optional alias for previous Key A (rotation)
 * @param previousInstitutionKeyAlias Optional alias for previous Key B (rotation)
 * @param previousEncryptionKeyAlias Optional alias for previous Key C (rotation)
 * @param previousHolderKeyVersion Optional version for previous Key A
 * @param previousInstitutionKeyVersion Optional version for previous Key B
 * @param previousEncryptionKeyVersion Optional version for previous Key C
 * @param providerId The KMS provider ID to use (e.g., "software")
 */
@OptIn(ExperimentalEncodingApi::class)
class KmsBackedReconciliationCryptoService(
    private val generateMacCommand: GenerateMacCommand,
    private val keyManagerService: KeyManagerService,
    private val holderKeyAlias: String = DEFAULT_HOLDER_KEY_ALIAS,
    private val institutionKeyAlias: String = DEFAULT_INSTITUTION_KEY_ALIAS,
    private val encryptionKeyAlias: String = DEFAULT_ENCRYPTION_KEY_ALIAS,
    private val holderKeyVersion: String = "v1",
    private val institutionKeyVersion: String = "v1",
    private val encryptionKeyVersion: String = "v1",
    private val previousHolderKeyAlias: String? = null,
    private val previousInstitutionKeyAlias: String? = null,
    private val previousEncryptionKeyAlias: String? = null,
    private val previousHolderKeyVersion: String? = null,
    private val previousInstitutionKeyVersion: String? = null,
    private val previousEncryptionKeyVersion: String? = null,
    private val providerId: String = "software",
) : ReconciliationCryptoService {

    companion object {
        const val DEFAULT_HOLDER_KEY_ALIAS = "reconciliation:holder"
        const val DEFAULT_INSTITUTION_KEY_ALIAS = "reconciliation:institution"
        const val DEFAULT_ENCRYPTION_KEY_ALIAS = "reconciliation:encryption"
    }

    override suspend fun hashHolderKey(holderKey: String): HashedIdentifier {
        return generateHmac(holderKey, holderKeyAlias, holderKeyVersion)
    }

    override suspend fun hashExternalIdentifier(identifier: String): HashedIdentifier {
        return generateHmac(identifier, institutionKeyAlias, institutionKeyVersion)
    }

    override suspend fun encrypt(plaintext: String): EncryptedPayload {
        val keyInfo = KeyInfo<Nothing>(alias = encryptionKeyAlias, providerId = providerId)
        val result = keyManagerService.encryptResult(
            keyInfo = keyInfo,
            plaintext = plaintext.encodeToByteArray(),
            algorithm = ContentEncryptionAlgorithm.A256GCM
        )
        val encryptResult = result.getOrNull()
            ?: throw IllegalStateException("Encryption failed: ${result.errorOrNull()}")

        // Combine IV (12 bytes) + authTag (16 bytes) + ciphertext into a single blob
        val combined = encryptResult.iv + encryptResult.authTag + encryptResult.ciphertext
        return EncryptedPayload(
            ciphertext = Base64.UrlSafe.encode(combined),
            keyVersion = encryptionKeyVersion,
        )
    }

    override suspend fun decrypt(payload: EncryptedPayload): String {
        val combined = Base64.UrlSafe.decode(payload.ciphertext)

        // Extract IV (12 bytes), authTag (16 bytes), ciphertext (remaining)
        require(combined.size > 28) { "Invalid encrypted payload: too short" }
        val iv = combined.copyOfRange(0, 12)
        val authTag = combined.copyOfRange(12, 28)
        val ciphertext = combined.copyOfRange(28, combined.size)

        // Version-aware key selection: use the key version stored in the payload
        // to determine which encryption key alias to use for decryption
        val alias = resolveEncryptionKeyAlias(payload.keyVersion)

        val keyInfo = KeyInfo<Nothing>(alias = alias, providerId = providerId)
        val result = keyManagerService.decryptResult(
            keyInfo = keyInfo,
            ciphertext = ciphertext,
            algorithm = ContentEncryptionAlgorithm.A256GCM,
            iv = iv,
            authTag = authTag
        )
        val decryptResult = result.getOrNull()
            ?: throw IllegalStateException(
                "Decryption failed for key version '${payload.keyVersion}' (alias=$alias): ${result.errorOrNull()}"
            )

        return decryptResult.plaintext.decodeToString()
    }

    /**
     * Resolves the encryption key alias based on the key version stored in the payload.
     *
     * - If the payload's key version matches the current encryption key version, use the current alias.
     * - If a previous encryption key is configured and the payload's key version matches
     *   the previous key version, use the previous alias.
     * - Otherwise, throw an error indicating the key version is unknown.
     */
    private fun resolveEncryptionKeyAlias(payloadKeyVersion: String): String {
        if (payloadKeyVersion == encryptionKeyVersion) {
            return encryptionKeyAlias
        }
        if (previousEncryptionKeyAlias != null && previousEncryptionKeyVersion != null
            && payloadKeyVersion == previousEncryptionKeyVersion
        ) {
            return previousEncryptionKeyAlias
        }
        throw IllegalStateException(
            "Cannot decrypt: unknown key version '$payloadKeyVersion'. " +
                    "Current version is '$encryptionKeyVersion'" +
                    (previousEncryptionKeyVersion?.let { ", previous version is '$it'" } ?: "") +
                    ". The payload was encrypted with a key version that is no longer available."
        )
    }

    override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? {
        val alias = previousHolderKeyAlias ?: return null
        val version = previousHolderKeyVersion ?: return null
        return generateHmac(holderKey, alias, version)
    }

    override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? {
        val alias = previousInstitutionKeyAlias ?: return null
        val version = previousInstitutionKeyVersion ?: return null
        return generateHmac(identifier, alias, version)
    }

    private suspend fun generateHmac(
        input: String,
        keyAlias: String,
        keyVersion: String,
    ): HashedIdentifier {
        val args = GenerateMacArgs(
            keyId = keyAlias,
            message = input.encodeToByteArray(),
            digestAlgorithm = DigestAlg.SHA256,
            providerId = providerId
        )

        val result = generateMacCommand.execute(args)
        val macResult = result.getOrNull()
            ?: throw IllegalStateException("HMAC generation failed: ${result.errorOrNull()}")

        return HashedIdentifier(
            hash = macResult.macMultibase,
            keyVersion = keyVersion,
        )
    }
}
