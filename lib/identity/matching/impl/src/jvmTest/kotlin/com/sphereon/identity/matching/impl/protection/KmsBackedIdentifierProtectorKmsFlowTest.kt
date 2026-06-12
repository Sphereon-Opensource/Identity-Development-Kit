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

package com.sphereon.identity.matching.impl.protection

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.GetAllCapabilitiesResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.crypto.core.kms.QueryProviderResult
import com.sphereon.crypto.core.kms.QueryProvidersResult
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.kms.command.DecryptResult
import com.sphereon.crypto.core.kms.command.DeleteKeyResult
import com.sphereon.crypto.core.kms.command.EncryptResult
import com.sphereon.crypto.core.kms.command.GenerateKeyResult
import com.sphereon.crypto.core.kms.command.GenerateMacArgs
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.crypto.core.kms.command.GenerateMacResult
import com.sphereon.crypto.core.kms.command.GetKeyResult
import com.sphereon.crypto.core.kms.command.ListKeysResult
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyResult
import com.sphereon.crypto.core.kms.command.StoreKeyResult
import com.sphereon.crypto.core.kms.command.UnwrapKeyResult
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult
import com.sphereon.crypto.core.kms.command.WrapKeyResult
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.identity.matching.protection.IdentifierProtectionPolicy
import com.sphereon.identity.matching.protection.NormalizationProfile
import kotlinx.coroutines.test.runTest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end flow test for the REAL [KmsBackedIdentifierProtector] against a KMS whose key
 * lifecycle enforces the production contract: a key must be provisioned (generated/stored)
 * under its alias BEFORE it can be used for MAC or encrypt operations. Crypto is real
 * (javax.crypto HMAC-SHA256 and AES-256-GCM); only the command-bus plumbing is bridged.
 *
 * Production symptom reproduced here: owner bootstrap on a fresh keystore fails with
 * "[owner-bootstrap] email protection failed: Encryption failed: ...". The blind-index half
 * succeeds because the protector lazily provisions "idfr:bi:&lt;tenantId&gt;"; the value-encryption
 * half must equally provision "idfr:enc:&lt;tenantId&gt;" before calling encrypt.
 */
class KmsBackedIdentifierProtectorKmsFlowTest {
    private val tenant = "application"
    private val emailType = IdentifierType("email")
    private val emailPolicy =
        IdentifierProtectionPolicy(
            identifierType = emailType,
            mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
            normalization = NormalizationProfile.EMAIL,
        )

    private fun newProtector(kms: InMemoryAesGcmKms): KmsBackedIdentifierProtector =
        KmsBackedIdentifierProtector(
            generateMacCommand = MapBackedGenerateMacCommand(kms),
            keyManagerService = kms,
            providerId = "software",
        )

    @Test
    fun protectOnFreshKmsProvisionsEncryptionKeyAndSucceeds() =
        runTest {
            val kms = InMemoryAesGcmKms()
            val protector = newProtector(kms)

            val result = protector.protect(tenant, "identity-1", emailType, "Owner@Example.com", emailPolicy)
            val protected =
                result.getOrNull()
                    ?: error("protect() must succeed on a fresh KMS (lazy key provisioning), got: ${result.errorOrNull()}")

            assertTrue(
                kms.storedKeys.containsKey("idfr:bi:$tenant"),
                "Blind-index key idfr:bi:$tenant must be provisioned lazily; provisioned: ${kms.storedKeys.keys}",
            )
            assertTrue(
                kms.storedKeys.containsKey("idfr:enc:$tenant"),
                "Encryption key idfr:enc:$tenant must be provisioned lazily before encrypt; provisioned: ${kms.storedKeys.keys}",
            )
            assertEquals("idfr:enc:$tenant", protected.encKeyRef)
            assertEquals("idfr:bi:$tenant", protected.hmacKeyRef)
        }

    @Test
    fun protectThenRevealRoundTripsThroughRealAesGcm() =
        runTest {
            val kms = InMemoryAesGcmKms()
            val protector = newProtector(kms)

            val protected =
                protector
                    .protect(tenant, "identity-1", emailType, "Owner@Example.com", emailPolicy)
                    .getOrNull() ?: error("protect failed")

            val revealed =
                protector
                    .reveal(protected, tenant, "identity-1", emailType)
                    .getOrNull() ?: error("reveal failed")

            assertEquals("owner@example.com", revealed, "reveal must return the normalized plaintext")
        }

    @Test
    fun repeatedProtectReusesProvisionedKeys() =
        runTest {
            val kms = InMemoryAesGcmKms()
            val protector = newProtector(kms)

            protector.protect(tenant, "identity-1", emailType, "a@example.com", emailPolicy).getOrNull()
                ?: error("first protect failed")
            val keysAfterFirst = kms.storedKeys.keys.toSet()
            val firstGenerateCount = kms.generateCount

            protector.protect(tenant, "identity-2", emailType, "b@example.com", emailPolicy).getOrNull()
                ?: error("second protect failed")

            assertEquals(keysAfterFirst, kms.storedKeys.keys.toSet(), "Key provisioning must be idempotent per tenant")
            assertEquals(firstGenerateCount, kms.generateCount, "Existing keys must be reused, not regenerated")
        }
}

/**
 * In-memory [KeyManagerService] bridge with REAL crypto and the production key-lifecycle
 * contract: encrypt/decrypt/MAC fail unless the key alias was provisioned first via
 * generateKey/storeKey. Mirrors the software KMS provider's behavior where
 * `resolveKeyIfNeeded` fails for unknown aliases.
 */
private class InMemoryAesGcmKms : KeyManagerService {
    val storedKeys = mutableMapOf<String, ResolvedKeyInfo<Jwk>>()
    var generateCount = 0
        private set

    private val random = SecureRandom()

    fun keyBytes(alias: String): ByteArray? = storedKeys[alias]?.key?.k?.decodeFrom(Encoding.BASE64URL)

    // ===== Methods exercised by KmsBackedIdentifierProtector (real behavior) =====

    override suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError> =
        try {
            Ok(GetKeyResult(getKey(keyInfo)))
        } catch (expected: Exception) {
            Err(IdkError.NOT_FOUND_ERROR(resource = "Key", message = expected.message ?: "Key not found"))
        }

    override suspend fun generateKeyResult(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?,
    ): IdkResult<GenerateKeyResult, IdkError> = Ok(GenerateKeyResult(generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)))

    override suspend fun generateKey(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?,
    ): ManagedKeyPair {
        requireNotNull(alias) { "This test KMS requires an alias for key generation" }
        require(alg == SignatureAlgorithm.HMAC_SHA256 || (use == JwkUse.enc && alg == null)) {
            "This test KMS only mints symmetric keys (HMAC-SHA256 or use=enc AES-256); requested use=$use alg=$alg"
        }
        generateCount++
        val rawKey = ByteArray(32).also { random.nextBytes(it) }
        val privateJwk =
            Jwk(
                kty = JwaKeyType.oct,
                k = rawKey.encodeToBase64Url(),
                alg = if (alg == SignatureAlgorithm.HMAC_SHA256) JwaAlgorithm.HS256 else null,
                use = (use ?: JwkUse.sig).value,
                kid = alias,
                generateKid = false,
            )
        storedKeys[alias] =
            ResolvedKeyInfo(
                key = privateJwk,
                keyVisibility = KeyVisibility.PRIVATE,
                keyType = KeyTypeMapping.Symmetric,
                alias = alias,
                providerId = providerId ?: "software",
                kid = alias,
                signatureAlgorithm = alg,
            )
        val publicJwk = privateJwk.copy(k = null)
        return ManagedKeyPair(
            kid = alias,
            providerId = providerId ?: "software",
            alias = alias,
            jose = JoseKeyPair(privateJwk, publicJwk),
            cose = CoseKeyPair(null, CoseJoseKeyMappingService.toCoseKey(publicJwk)),
        )
    }

    override suspend fun encryptResult(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): IdkResult<EncryptResult, IdkError> {
        val alias =
            keyInfo.alias ?: keyInfo.kid
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Encrypt requires an alias or kid"))
        val keyBytes =
            keyBytes(alias)
                ?: return Err(IdkError.fromString(message = "Encryption failed: Could not find key for alias $alias"))
        require(algorithm == ContentEncryptionAlgorithm.A256GCM) { "This test KMS only supports A256GCM" }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        additionalAuthenticatedData?.let { cipher.updateAAD(it) }
        val combined = cipher.doFinal(plaintext)
        return Ok(
            EncryptResult(
                ciphertext = combined.copyOfRange(0, combined.size - 16),
                iv = iv,
                authTag = combined.copyOfRange(combined.size - 16, combined.size),
            ),
        )
    }

    override suspend fun decryptResult(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
    ): IdkResult<DecryptResult, IdkError> {
        val alias =
            keyInfo.alias ?: keyInfo.kid
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Decrypt requires an alias or kid"))
        val keyBytes =
            keyBytes(alias)
                ?: return Err(IdkError.fromString(message = "Decryption failed: Could not find key for alias $alias"))
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            additionalAuthenticatedData?.let { cipher.updateAAD(it) }
            Ok(DecryptResult(cipher.doFinal(ciphertext + authTag)))
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "Decryption failed: ${expected::class.simpleName}: ${expected.message}"))
        }
    }

    // ===== Key store bookkeeping (real, map-backed) =====

    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyReference> =
        storedKeys.entries
            .map { (alias, resolved) -> ManagedKeyInfo(alias = alias, providerId = resolved.providerId ?: "software", resolvedKeyInfo = resolved).toKeyReference() }
            .toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Need alias or kid")
        val resolved =
            storedKeys[alias] ?: storedKeys.values.find { it.kid == alias }
                ?: throw IllegalArgumentException("Key not found: $alias")
        return ManagedKeyInfo(alias = resolved.alias ?: alias, providerId = resolved.providerId ?: "software", resolvedKeyInfo = resolved)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        storedKeys[alias] = ResolvedKeyInfo.fromDTO(keyInfo) as ResolvedKeyInfo<Jwk>
        return ManagedKeyInfo(alias = alias, providerId = providerId, resolvedKeyInfo = keyInfo)
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val alias = keyInfo.alias ?: return false
        return storedKeys.remove(alias) != null
    }

    override suspend fun listKeysResult(providerId: String?): IdkResult<ListKeysResult, IdkError> = Ok(ListKeysResult(listKeys()))

    override suspend fun storeKeyResult(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): IdkResult<StoreKeyResult, IdkError> = Ok(StoreKeyResult(storeKey(keyInfo, providerId, alias, certChain)))

    override suspend fun deleteKeyResult(keyInfo: KeyInfoType<*>): IdkResult<DeleteKeyResult, IdkError> = Ok(DeleteKeyResult(deleteKey(keyInfo)))

    override fun keyVisibility(): KeyVisibility = KeyVisibility.PRIVATE

    // ===== Not exercised by the protector =====

    override fun defaultProviderId(): String = "software"

    override fun defaultResolverId(): String = unused()

    override fun registerProvider(
        provider: KmsProvider,
        makeDefaultKms: Boolean?,
    ): Unit = unused()

    override fun getProviderIds(): Array<String> = unused()

    override fun getProviderById(id: String): KmsProvider = unused()

    override fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider = unused()

    override fun getResolverById(id: String): KeyResolverService = unused()

    override fun getResolverByKeyTypeOrIdentifier(
        identifierMethod: IdentifierMethod?,
        keyType: KeyTypeMapping?,
        resolverId: String?,
    ): KeyResolverService = unused()

    override fun registerResolver(
        resolver: KeyResolverService,
        makeDefaultResolver: Boolean?,
    ): Unit = unused()

    @Deprecated("Use generateKey instead", ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"))
    override suspend fun generateKeyAsync(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?,
    ): ManagedKeyPair = generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)

    override fun getResolverIds(): Array<String> = unused()

    override fun getProvider(
        providerId: String?,
        alg: SignatureAlgorithm?,
    ): KmsProvider = unused()

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray = unused()

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean = unused()

    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?,
    ): ResolvedKeyInfoType<KT> = unused()

    override val keyStore: KeyStoreService
        get() = unused()

    override suspend fun queryProvider(query: KmsProviderQuery): IdkResult<QueryProviderResult, IdkError> = unused()

    override suspend fun queryProviders(query: KmsProviderQuery): IdkResult<QueryProvidersResult, IdkError> = unused()

    override suspend fun getAllCapabilities(includeDisabled: Boolean): IdkResult<GetAllCapabilitiesResult, IdkError> = unused()

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): EncryptionResult = unused()

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
    ): ByteArray = unused()

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = unused()

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): ByteArray = unused()

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): ByteArray = unused()

    override suspend fun createRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): IdkResult<CreateRawSignatureResult, IdkError> = unused()

    override suspend fun verifyRawSignatureResult(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): IdkResult<VerifyRawSignatureResult, IdkError> = unused()

    override suspend fun wrapKeyResult(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): IdkResult<WrapKeyResult, IdkError> = unused()

    override suspend fun unwrapKeyResult(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: KeyWrapAlgorithm,
    ): IdkResult<UnwrapKeyResult, IdkError> = unused()

    override suspend fun performKeyAgreementResult(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): IdkResult<PerformKeyAgreementResult, IdkError> = unused()

    override suspend fun resolvePublicKeyResult(
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?,
    ): IdkResult<ResolvePublicKeyResult, IdkError> = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not exercised by KmsBackedIdentifierProtector")
}

/**
 * [GenerateMacCommand] bridge computing a REAL HMAC-SHA256 with the key bytes provisioned in
 * the shared [InMemoryAesGcmKms]. Enforces the production contract: the key alias must exist.
 */
private class MapBackedGenerateMacCommand(
    private val kms: InMemoryAesGcmKms,
) : GenerateMacCommand {
    override val isEnabled: Boolean = true
    override val inputTypeToken = typeToken<GenerateMacArgs>()
    override val outputTypeToken = typeToken<GenerateMacResult>()

    override suspend fun supports(args: Any): Boolean = args is GenerateMacArgs

    override suspend fun execute(args: GenerateMacArgs): IdkResult<GenerateMacResult, IdkError> {
        val keyBytes =
            kms.keyBytes(args.keyId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Key", message = "MAC key '${args.keyId}' not found"))
        val mac =
            Mac
                .getInstance("HmacSHA256")
                .apply { init(SecretKeySpec(keyBytes, "HmacSHA256")) }
                .doFinal(args.message)
        return Ok(
            GenerateMacResult(
                mac = mac,
                macMultibase = "f" + mac.joinToString("") { byte -> "%02x".format(byte) },
                digestAlgorithm = args.digestAlgorithm,
            ),
        )
    }
}
