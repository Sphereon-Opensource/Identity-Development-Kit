/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface WalletCredentialBodyProtector {
    suspend fun protect(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        plaintext: ByteArray,
    ): IdkResult<ByteArray, IdkError>

    suspend fun open(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        protectedBody: ByteArray,
    ): IdkResult<ByteArray, IdkError>
}

@Serializable
private data class ProtectedCredentialBodyEnvelope(
    val version: Int = 1,
    val protection: String = "kms-a256gcm",
    val algorithm: String = ContentEncryptionAlgorithm.A256GCM.identifier,
    val keyAlias: String,
    val providerId: String? = null,
    val iv: String,
    val authTag: String,
    val ciphertext: String,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletCredentialBodyProtector>())
class KmsWalletCredentialBodyProtector(
    private val keyManagerService: KeyManagerService,
) : WalletCredentialBodyProtector {
    private val bodyKeyProvisioningMutex = Mutex()

    override suspend fun protect(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        plaintext: ByteArray,
    ): IdkResult<ByteArray, IdkError> {
        val keyInfo = resolveOrCreateBodyKey(walletInstanceId).getOrElse { return Err(it) }
        val encrypted =
            keyManagerService.encryptResult(
                keyInfo = keyInfo,
                plaintext = plaintext,
                algorithm = ContentEncryptionAlgorithm.A256GCM,
                additionalAuthenticatedData = bodyAad(walletInstanceId, credentialRecordId, credentialInstanceId),
            )
        if (encrypted.isErr) return Err(encrypted.error)

        val envelope =
            ProtectedCredentialBodyEnvelope(
                keyAlias = requireNotNull(keyInfo.alias) { "Wallet body key alias is required" },
                providerId = keyInfo.providerId,
                iv = encrypted.value.iv.encodeTo(Encoding.BASE64URL),
                authTag = encrypted.value.authTag.encodeTo(Encoding.BASE64URL),
                ciphertext = encrypted.value.ciphertext.encodeTo(Encoding.BASE64URL),
            )
        return Ok(walletBodyJson.encodeToString(envelope).encodeToByteArray())
    }

    override suspend fun open(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        protectedBody: ByteArray,
    ): IdkResult<ByteArray, IdkError> {
        val envelope =
            runCatching {
                walletBodyJson.decodeFromString<ProtectedCredentialBodyEnvelope>(protectedBody.decodeToString())
            }.getOrElse { cause ->
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Credential instance body '$credentialInstanceId' is not a supported protected wallet body envelope: ${cause.message}",
                    ),
                )
            }
        if (envelope.version != 1 || envelope.protection != "kms-a256gcm") {
            return Err(
                IdkError.UNSUPPORTED_OPERATION_ERROR(
                    operation = "wallet-credential-body-open",
                    reason = "Unsupported protected wallet body envelope '${envelope.protection}' version ${envelope.version}",
                ),
            )
        }

        val decrypted =
            keyManagerService.decryptResult(
                keyInfo = KeyInfo<Nothing>(alias = envelope.keyAlias, providerId = envelope.providerId),
                ciphertext = envelope.ciphertext.decodeFrom(Encoding.BASE64URL),
                algorithm = ContentEncryptionAlgorithm.A256GCM,
                iv = envelope.iv.decodeFrom(Encoding.BASE64URL),
                authTag = envelope.authTag.decodeFrom(Encoding.BASE64URL),
                additionalAuthenticatedData = bodyAad(walletInstanceId, credentialRecordId, credentialInstanceId),
            )
        return if (decrypted.isOk) Ok(decrypted.value.plaintext) else Err(decrypted.error)
    }

    private suspend fun resolveOrCreateBodyKey(walletInstanceId: String): IdkResult<KeyInfo<Nothing>, IdkError> {
        val alias = bodyKeyAlias(walletInstanceId)
        val existing = keyManagerService.getKeyResult(KeyInfo<Nothing>(alias = alias))
        if (existing.isOk) {
            val key = existing.value.key
            if (key == null) return provisionBodyKeySerialized(alias)
            return Ok(KeyInfo(alias = key.alias, providerId = key.providerId))
        }
        return provisionBodyKeySerialized(alias)
    }

    private suspend fun provisionBodyKeySerialized(alias: String): IdkResult<KeyInfo<Nothing>, IdkError> =
        bodyKeyProvisioningMutex.withLock {
            val existing = keyManagerService.getKeyResult(KeyInfo<Nothing>(alias = alias))
            if (existing.isOk) {
                val key = existing.value.key
                if (key != null) return@withLock Ok(KeyInfo(alias = key.alias, providerId = key.providerId))
            }
            provisionBodyKey(alias)
        }

    private suspend fun provisionBodyKey(alias: String): IdkResult<KeyInfo<Nothing>, IdkError> {
        val generated =
            runCatching {
                keyManagerService.generateKey(
                    alias = alias,
                    use = JwkUse.enc,
                    keyOperations = arrayOf(KeyOperations.ENCRYPT, KeyOperations.DECRYPT),
                    alg = null,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            }.getOrElse { cause ->
                return Err(
                    IdkError.fromString(
                        code = "WALLET_CREDENTIAL_BODY_KEY_PROVISIONING_FAILED",
                        message = "Could not provision wallet credential body key '$alias': ${cause.message}",
                        exception = cause as? Exception,
                    ),
                )
            }
        return Ok(KeyInfo(alias = generated.alias, providerId = generated.providerId))
    }

    private fun bodyKeyAlias(walletInstanceId: String): String = "wallet-instances/$walletInstanceId/credential-body/a256gcm"
}

private val walletBodyJson = Json { ignoreUnknownKeys = true }

private fun bodyAad(
    walletInstanceId: String,
    credentialRecordId: String,
    credentialInstanceId: String,
): ByteArray = "wallet-credential-body:v1:$walletInstanceId:$credentialRecordId:$credentialInstanceId".encodeToByteArray()
