/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.interop.derPublicKeyToJwk
import com.sphereon.crypto.core.interop.toPkcs8PrivateKeyInfo
import com.sphereon.crypto.core.interop.toSubjectPublicKeyInfo
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreLoaderOpts
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.providers.base.refToU
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import platform.CoreFoundation.CFBooleanRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberSInt32Type
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecAttrLabel
import platform.Security.kSecClass
import platform.Security.kSecClassCertificate
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecClassKey
import platform.Security.kSecReturnData
import platform.Security.kSecReturnRef
import platform.Security.kSecValueData
import platform.Security.kSecValueRef
import platform.darwin.OSStatus

@OptIn(ExperimentalForeignApi::class)
@AssistedInject
actual class SoftwareKeyStoreService actual constructor(
    @Assisted private val config: KeyStoreConfig,
) : KeyStore {
    actual override val id: String = config.id
    actual override val keyStoreType: String = config.keyStoreType

    actual override val keyTypesSupported: Array<KeyTypeMapping> =
        arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA, KeyTypeMapping.Symmetric)

    actual override val signatureAlgorithmsSupported: Array<SignatureAlgorithm> =
        arrayOf(
            SignatureAlgorithm.ECDSA_SHA256,
            SignatureAlgorithm.ECDSA_SHA384,
            SignatureAlgorithm.RSA_SHA256,
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
        )

    actual override val settings: KeyProviderSettings? = null

    init {
        with(this.config) {
            require(
                keyStoreType == PredefinedKeyStoreTypes.APPLE.keyStoreType,
            ) {
                "A software keystore needs to be of config type ${PredefinedKeyStoreTypes.APPLE.keyStoreType} got $keyStoreType"
            }
        }
    }

    actual override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    actual override fun exposesPrivateKeysForSigning(): Boolean = false

    actual override suspend fun listKeys(): Array<ManagedKeyReference> {
        val index = Keychain.loadKeyIndex()
        return index.keys
            .mapNotNull { alias ->
                try {
                    getKey(KeyInfo<Jwk>(alias = alias))
                } catch (_: Exception) {
                    null
                }
            }.map { it.toKeyReference() }
            .toTypedArray()
    }

    actual override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: throw PKIException("Key alias is required")

        return if (Keychain.isNativeKey(alias)) {
            getNativeKeyAsJwk(alias, keyInfo)
        } else {
            getImportedKeyAsJwk(alias, keyInfo)
        }
    }

    /**
     * Retrieve a native key (non-exportable) and return as JWK with public key only.
     */
    private fun getNativeKeyAsJwk(
        alias: String,
        keyInfo: KeyInfoType<*>,
    ): ManagedKeyInfoType<*> {
        val secKeyRef =
            Keychain.getNativeKey(alias)
                ?: throw PKIException("Native key not found: $alias")

        val publicKeyRef =
            platform.Security.SecKeyCopyPublicKey(secKeyRef)
                ?: throw PKIException("Failed to extract public key from native key: $alias")

        val publicKeyData =
            platform.Security.SecKeyCopyExternalRepresentation(publicKeyRef, null)
                ?: throw PKIException("Failed to export public key for native key: $alias")

        val pubKeyLength = platform.CoreFoundation.CFDataGetLength(publicKeyData).toInt()
        val pubKeyBytesPtr = platform.CoreFoundation.CFDataGetBytePtr(publicKeyData)
        val pubKeyBytes =
            pubKeyBytesPtr?.readBytes(pubKeyLength)
                ?: throw PKIException("Failed to read public key bytes for native key: $alias")

        // iOS SecKeyCopyExternalRepresentation returns raw EC bytes (04 || X || Y), not ASN.1 DER
        // We need to convert this to JWK format directly
        val jwk =
            if (pubKeyBytes.size == 65 && pubKeyBytes[0] == 0x04.toByte()) {
                // Uncompressed EC P-256 public key (04 || 32-byte X || 32-byte Y)
                rawEcPublicKeyToJwk(pubKeyBytes)
            } else if (pubKeyBytes.size == 97 && pubKeyBytes[0] == 0x04.toByte()) {
                // Uncompressed EC P-384 public key (04 || 48-byte X || 48-byte Y)
                rawEcPublicKeyToJwk(pubKeyBytes)
            } else if (pubKeyBytes.size == 133 && pubKeyBytes[0] == 0x04.toByte()) {
                // Uncompressed EC P-521 public key (04 || 66-byte X || 66-byte Y)
                rawEcPublicKeyToJwk(pubKeyBytes)
            } else {
                // Assume it's DER format (e.g., RSA keys)
                derPublicKeyToJwk(pubKeyBytes)
            }

        // Respect the requested encoding
        val requestedEncoding = keyInfo.keyEncoding ?: KeyEncoding.JOSE
        val key =
            when (requestedEncoding) {
                KeyEncoding.JOSE -> jwk
                KeyEncoding.COSE -> CoseJoseKeyMappingService.toCoseKey(jwk)
                else -> throw IllegalArgumentException("Unsupported key encoding: $requestedEncoding")
            }

        val resolvedKeyInfo =
            ResolvedKeyInfo(
                keyType = keyInfo.keyType,
                key = key,
                keyVisibility = KeyVisibility.PRIVATE, // Native key is private but not exportable
                alias = alias,
                keyEncoding = requestedEncoding,
            )

        return ManagedKeyInfo(
            alias = alias,
            providerId = id,
            resolvedKeyInfo = resolvedKeyInfo as ResolvedKeyInfoType<*>,
        )
    }

    /**
     * Retrieve an imported key (exportable) and return as JWK with full key material.
     */
    private fun getImportedKeyAsJwk(
        alias: String,
        keyInfo: KeyInfoType<*>,
    ): ManagedKeyInfoType<*> {
        val keyData =
            Keychain.findKeyByAlias(alias)
                ?: throw PKIException("Key not found: $alias")

        // Convert DER to JWK
        val jwk =
            if (keyData.isPrivate) {
                derPrivateKeyToJwk(keyData.keyDer)
            } else {
                derPublicKeyToJwk(keyData.keyDer)
            }

        // Respect the requested encoding
        val requestedEncoding = keyInfo.keyEncoding ?: KeyEncoding.JOSE
        val key =
            when (requestedEncoding) {
                KeyEncoding.JOSE -> jwk
                KeyEncoding.COSE -> CoseJoseKeyMappingService.toCoseKey(jwk)
                else -> throw IllegalArgumentException("Unsupported key encoding: $requestedEncoding")
            }

        val resolvedKeyInfo =
            ResolvedKeyInfo(
                keyType = keyInfo.keyType,
                key = key,
                keyVisibility = if (keyData.isPrivate) KeyVisibility.PRIVATE else KeyVisibility.PUBLIC,
                alias = alias,
                keyEncoding = requestedEncoding,
            )

        return ManagedKeyInfo(
            alias = alias,
            providerId = id,
            resolvedKeyInfo = resolvedKeyInfo as ResolvedKeyInfoType<*>,
        )
    }

    actual override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        val jwk = CoseJoseKeyMappingService.toJoseJwk(keyInfo.key)

        // Check if this is actually a private key (has 'd' graph in JWK)
        val isPrivate = jwk.d != null

        val keyType = keyInfo.keyType
        require(keyType == KeyTypeMapping.EC || keyType == KeyTypeMapping.RSA) {
            "Unsupported key type for iOS Keychain: $keyType"
        }

        // Check if key was already generated natively - if so, don't overwrite it
        val isNativeKey = Keychain.isNativeKey(alias)
        if (isNativeKey) {
            // Only store certificate chain if provided
            certChain?.forEachIndexed { idx, cert ->
                val certAlias = if (idx == 0) alias else "$alias-$idx"
                storeTrustedCertificate(certAlias, cert)
            }
            return ManagedKeyInfo(
                alias = alias,
                providerId = providerId,
                resolvedKeyInfo = keyInfo,
            )
        }

        // Import the key from JWK → DER using awesn1
        val keyDer =
            if (isPrivate) {
                jwk.toPkcs8PrivateKeyInfo().encodeToTlv().derEncoded
            } else {
                jwk.toSubjectPublicKeyInfo().encodeToTlv().derEncoded
            }

        // Idempotent store: remove prior alias then add
        Keychain.deleteKeyByAlias(alias)
        Keychain.addKey(
            alias = alias,
            keyDer = keyDer,
            keyType = keyType,
            isPrivate = isPrivate,
            keySizeBits = keySizeGuess(jwk, keyType),
        )

        // Persist certificate chain (optional)
        certChain?.forEachIndexed { idx, cert ->
            val certAlias = if (idx == 0) alias else "$alias-$idx"
            storeTrustedCertificate(certAlias, cert)
        }

        return ManagedKeyInfo(
            alias = alias,
            providerId = providerId,
            resolvedKeyInfo = keyInfo,
        )
    }

    actual override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val alias = keyInfo.alias ?: return false
        return Keychain.deleteKeyByAlias(alias)
    }

    // ---------------------------
    // Certificates and chains
    // ---------------------------

    actual override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?,
    ) {
        certificates.forEachIndexed { idx, cert ->
            val certAlias = if (idx == 0) alias else "$alias-$idx"
            storeTrustedCertificate(certAlias, cert)
        }
    }

    actual override suspend fun listCertificateChainAliases(): Array<String> {
        // Requires an index to group chain members; absent that, return all aliases we know (if indexed).
        return listCertificateAliases()
    }

    actual override suspend fun getCertificateChain(alias: String): Array<Certificate> {
        val all = Keychain.listCertificateAliases()
        val aliases =
            all
                .filter { it == alias || it.startsWith("$alias-") }
                .sortedWith(
                    compareBy {
                        if (it == alias) 0 else it.removePrefix("$alias-").toIntOrNull() ?: Int.MAX_VALUE
                    },
                )
        if (aliases.isEmpty()) {
            throw PKIException("Certificate chain not found for alias: $alias")
        }
        return aliases.map { getCertificate(it) }.toTypedArray()
    }

    actual override suspend fun deleteCertificateChain(alias: String): Boolean {
        val all = Keychain.listCertificateAliases()
        val aliases = all.filter { it == alias || it.startsWith("$alias-") }
        var ok = true
        aliases.forEach { a -> ok = Keychain.deleteCertificateByAlias(a) && ok }
        return ok
    }

    actual override suspend fun storeTrustedCertificate(
        alias: String,
        certificate: Certificate,
    ) {
        Keychain.deleteCertificateByAlias(alias) // idempotent
        Keychain.addCertificate(alias, certificate.der)
    }

    actual override suspend fun listCertificateAliases(): Array<String> = Keychain.listCertificateAliases().toTypedArray()

    actual override suspend fun getCertificate(alias: String): Certificate {
        val der =
            Keychain.findCertificateDERByAlias(alias)
                ?: throw PKIException("Certificate not found: $alias")
        return derToCertificate(der)
    }

    actual override suspend fun deleteCertificate(alias: String): Boolean = Keychain.deleteCertificateByAlias(alias)

    // ---------------------------
    // Mapping helpers
    // ---------------------------
    private fun decodeBase64(input: String): ByteArray =
        try {
            input.decodeFrom(Encoding.BASE64)
        } catch (expected: Throwable) {
            throw PKIException("Invalid Base64 ${expected.message}")
        }

    private fun keySizeGuess(
        jwk: Jwk,
        keyType: KeyTypeMapping,
    ): Int? =
        when (keyType) {
            KeyTypeMapping.EC -> {
                when (jwk.crv) {
                    JwaCurve.P_384 -> 384
                    JwaCurve.P_521 -> 521
                    else -> 256 // default P-256
                }
            }

            KeyTypeMapping.RSA -> {
                jwk.n?.length?.let { it * 6 / 8 }
            }

            // approximate from base64url length
            else -> {
                null
            }
        }

    // Convert DER -> Certificate using proper X.509 parsing
    private fun derToCertificate(der: ByteArray): Certificate = certificateFromDer(der)

    /**
     * Convert raw EC public key bytes (as returned by iOS SecKeyCopyExternalRepresentation)
     * to a JWK. iOS returns uncompressed EC point format: 04 || X || Y
     *
     * Supported key sizes:
     * - P-256: 65 bytes (1 + 32 + 32)
     * - P-384: 97 bytes (1 + 48 + 48)
     * - P-521: 133 bytes (1 + 66 + 66)
     */
    private fun rawEcPublicKeyToJwk(rawBytes: ByteArray): Jwk {
        require(rawBytes.isNotEmpty() && rawBytes[0] == 0x04.toByte()) {
            "Expected uncompressed EC public key starting with 0x04, got: ${rawBytes.firstOrNull()?.toString(16)}"
        }

        val coordLength = (rawBytes.size - 1) / 2
        val curve =
            when (coordLength) {
                32 -> JwaCurve.P_256
                48 -> JwaCurve.P_384
                66 -> JwaCurve.P_521
                else -> throw PKIException("Unsupported EC key size: ${rawBytes.size} bytes (coord length: $coordLength)")
            }

        val x = rawBytes.copyOfRange(1, 1 + coordLength)
        val y = rawBytes.copyOfRange(1 + coordLength, rawBytes.size)

        return Jwk
            .Builder()
            .withKty(JwaKeyType.EC)
            .withCrv(curve)
            .withX(x.encodeTo(Encoding.BASE64URL))
            .withY(y.encodeTo(Encoding.BASE64URL))
            .build()
    }
}

/**
 * Data class for storing key information retrieved from the Keychain.
 */
internal data class KeychainKeyData(
    val keyDer: ByteArray,
    val keyType: KeyTypeMapping,
    val isPrivate: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as KeychainKeyData
        if (!keyDer.contentEquals(other.keyDer)) return false
        if (keyType != other.keyType) return false
        if (isPrivate != other.isPrivate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = keyDer.contentHashCode()
        result = 31 * result + keyType.hashCode()
        result = 31 * result + isPrivate.hashCode()
        return result
    }
}

/**
 * Thin Keychain utility for iOS Security framework usage from Kotlin/Native.
 * Uses alias as kSecAttrApplicationTag and stores EC/RSA keys and certificates.
 *
 * IMPORTANT: This implementation uses software-based key storage WITHOUT hardware protection.
 * Private keys are fully accessible via SecKeyCopyExternalRepresentation for mTLS purposes.
 * Keys are stored with kSecAttrAccessibleAfterFirstUnlock accessibility.
 *
 * NOTE: Uses in-memory alias indexes. In production, these should be persisted to UserDefaults
 * or as separate keychain items to survive app restarts.
 */
@OptIn(ExperimentalForeignApi::class)
internal object Keychain {
    private const val TAG_PREFIX = "com.sphereon.privatekey"
    private const val INDEX_TAG_PREFIX = "com.sphereon.crypto.kms.index"
    private const val KEY_INDEX_ALIAS = "__sphereon_key_index__"
    private const val CERT_INDEX_ALIAS = "__sphereon_cert_index__"

    // Metadata stored in keychain index
    data class KeyMetadata(
        val keyType: KeyTypeMapping,
        val isPrivate: Boolean,
        val isNative: Boolean = false,
    )

    private fun tagFor(alias: String): ByteArray = "$TAG_PREFIX.$alias".encodeToByteArray()

    private fun indexTagFor(indexAlias: String): ByteArray = "$INDEX_TAG_PREFIX.$indexAlias".encodeToByteArray()

    // Persist key index to keychain
    private fun saveKeyIndex(index: Map<String, KeyMetadata>) {
        val indexData =
            index.entries
                .joinToString("\n") { (alias, meta) ->
                    val keyTypeStr =
                        when (meta.keyType) {
                            KeyTypeMapping.EC -> "EC"
                            KeyTypeMapping.RSA -> "RSA"
                            KeyTypeMapping.OKP -> "OKP"
                            else -> "UNKNOWN"
                        }
                    "$alias|$keyTypeStr|${meta.isPrivate}|${meta.isNative}"
                }.encodeToByteArray()

        // Delete old index
        val deleteQuery =
            cfDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to KEY_INDEX_ALIAS.toCFData(),
            )
        SecItemDelete(deleteQuery)

        // Store new index as a generic password item
        if (indexData.isNotEmpty()) {
            val addQuery =
                cfDict(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrAccount to KEY_INDEX_ALIAS.toCFData(),
                    kSecValueData to indexData.toCFData(),
                )
            SecItemAdd(addQuery, null)
        }
    }

    // Load key index from keychain
    fun loadKeyIndex(): MutableMap<String, KeyMetadata> {
        val query =
            cfDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to KEY_INDEX_ALIAS.toCFData(),
                kSecReturnData to true.toCFBoolean(),
            )

        return memScoped {
            val out = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, out.ptr)
            if (status != 0) return@memScoped mutableMapOf()

            val dataRef = out.value as CFDataRef? ?: return@memScoped mutableMapOf()
            val indexData = dataRef.toByteArray()
            val indexString = indexData.decodeToString()

            indexString
                .split("\n")
                .filter { it.isNotBlank() }
                .associate { line ->
                    val parts = line.split("|")
                    val alias = parts[0]
                    val keyType =
                        when (parts[1]) {
                            "EC" -> KeyTypeMapping.EC
                            "RSA" -> KeyTypeMapping.RSA
                            "OKP" -> KeyTypeMapping.OKP
                            else -> KeyTypeMapping.EC // default fallback
                        }
                    val isPrivate = parts[2].toBoolean()
                    val isNative = parts.getOrNull(3)?.toBoolean() ?: false
                    alias to KeyMetadata(keyType, isPrivate, isNative)
                }.toMutableMap()
        }
    }

    // Persist certificate index to keychain
    private fun saveCertIndex(index: Set<String>) {
        val indexData = index.joinToString("\n").encodeToByteArray()

        // Delete old index
        val deleteQuery =
            cfDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to CERT_INDEX_ALIAS.toCFData(),
            )
        SecItemDelete(deleteQuery)

        // Store new index
        if (indexData.isNotEmpty()) {
            val addQuery =
                cfDict(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrAccount to CERT_INDEX_ALIAS.toCFData(),
                    kSecValueData to indexData.toCFData(),
                )
            SecItemAdd(addQuery, null)
        }
    }

    // Load certificate index from keychain
    private fun loadCertIndex(): MutableSet<String> {
        val query =
            cfDict(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to CERT_INDEX_ALIAS.toCFData(),
                kSecReturnData to true.toCFBoolean(),
            )

        return memScoped {
            val out = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, out.ptr)
            if (status != 0) {
                return@memScoped mutableSetOf()
            }

            val dataRef = out.value as CFDataRef? ?: return@memScoped mutableSetOf()
            val indexData = dataRef.toByteArray()
            val indexString = indexData.decodeToString()

            indexString.split("\n").filter { it.isNotBlank() }.toMutableSet()
        }
    }

    fun addKey(
        alias: String,
        keyDer: ByteArray,
        keyType: KeyTypeMapping,
        isPrivate: Boolean,
        keySizeBits: Int?,
    ) {
        val tag = tagFor(alias)
        val tagData = tag.toCFData() ?: throw PKIException("Failed to create tag data")

        // Idempotent: delete existing key item
        val deleteQuery =
            cfDict(
                kSecClass to kSecClassKey,
                kSecAttrApplicationTag to tagData,
            )
        SecItemDelete(deleteQuery)

        // Store key as actual key item (kSecClassKey) with DER data
        // iOS will auto-compute kSecAttrApplicationLabel from the public key graph
        // This allows iOS to later auto-link with certificates for identity creation
        val keyTypeAttr =
            when (keyType) {
                KeyTypeMapping.EC -> kSecAttrKeyTypeECSECPrimeRandom
                KeyTypeMapping.RSA -> kSecAttrKeyTypeRSA
                else -> throw PKIException("Unsupported key type: $keyType")
            }

        val keyClassAttr = if (isPrivate) kSecAttrKeyClassPrivate else kSecAttrKeyClassPublic

        val addQuery =
            cfDict(
                kSecClass to kSecClassKey,
                kSecAttrKeyType to keyTypeAttr,
                kSecAttrKeyClass to keyClassAttr,
                kSecAttrApplicationTag to tagData,
                kSecAttrLabel to alias.toCFData(), // Use same label scheme as certificates
                kSecValueData to keyDer.toCFData(),
                kSecAttrIsPermanent to true.toCFBoolean(),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
            )

        val status: OSStatus = SecItemAdd(addQuery, null)
        // -25299 (errSecDuplicateItem) means key already exists - acceptable in idempotent operation
        if (status != 0 && status != -25299) {
            throw PKIException("SecItemAdd key failed: status=$status for alias=$alias, keyType=$keyType, isPrivate=$isPrivate")
        }

        // Update index
        val index = loadKeyIndex()
        index[alias] = KeyMetadata(keyType, isPrivate, isNative = false)
        saveKeyIndex(index)
    }

    fun findKeyByAlias(alias: String): KeychainKeyData? {
        val index = loadKeyIndex()
        val metadata = index[alias] ?: return null

        // For native keys, we cannot retrieve DER data - private key is not exportable
        // Return null so caller knows to use getNativeKey() instead
        if (metadata.isNative) {
            return null
        }

        // Retrieve key as DER data from key item (kSecClassKey)
        val query: CFDictionaryRef? =
            cfDict(
                kSecClass to kSecClassKey,
                kSecAttrApplicationTag to tagFor(alias).toCFData(),
                kSecReturnData to true.toCFBoolean(),
            )

        return memScoped {
            val out = alloc<CFTypeRefVar>()
            val status: OSStatus = SecItemCopyMatching(query, out.ptr)
            if (status != 0) {
                return@memScoped null
            }

            val dataRef = out.value as CFDataRef? ?: return@memScoped null
            val keyDer = dataRef.toByteArray()

            KeychainKeyData(keyDer, metadata.keyType, metadata.isPrivate)
        }
    }

    data class KeyWithAlias(
        val alias: String,
        val keyDer: ByteArray,
        val keyType: KeyTypeMapping,
        val isPrivate: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as KeyWithAlias
            if (alias != other.alias) return false
            if (!keyDer.contentEquals(other.keyDer)) return false
            if (keyType != other.keyType) return false
            if (isPrivate != other.isPrivate) return false
            return true
        }

        override fun hashCode(): Int {
            var result = alias.hashCode()
            result = 31 * result + keyDer.contentHashCode()
            result = 31 * result + keyType.hashCode()
            result = 31 * result + isPrivate.hashCode()
            return result
        }
    }

    fun listAllKeys(): List<KeyWithAlias> {
        val index = loadKeyIndex()
        return index.keys.mapNotNull { alias ->
            findKeyByAlias(alias)?.let { keyData ->
                KeyWithAlias(alias, keyData.keyDer, keyData.keyType, keyData.isPrivate)
            }
        }
    }

    fun deleteKeyByAlias(alias: String): Boolean {
        val query: CFDictionaryRef? =
            cfDict(
                kSecClass to kSecClassKey,
                kSecAttrApplicationTag to tagFor(alias).toCFData(),
            )
        val deleted = SecItemDelete(query) == 0
        if (deleted) {
            // Update and persist index
            val index = loadKeyIndex()
            index.remove(alias)
            saveKeyIndex(index)
        }
        return deleted
    }

    fun addCertificate(
        alias: String,
        der: ByteArray,
    ) {
        val certRef =
            SecCertificateCreateWithData(null, der.toCFData())
                ?: throw PKIException("Invalid certificate DER")

        // For certificates, use kSecAttrLabel instead of kSecAttrApplicationTag
        val addQuery: CFDictionaryRef? =
            cfDict(
                kSecClass to kSecClassCertificate,
                kSecAttrLabel to alias.toCFData(),
                kSecValueRef to certRef,
            )
        val status: OSStatus = SecItemAdd(addQuery, null)
        if (status != 0) {
            // If duplicate, delete and retry
            if (status.toInt() == -25299) {
                val deleteQuery: CFDictionaryRef? =
                    cfDict(
                        kSecClass to kSecClassCertificate,
                        kSecAttrLabel to alias.toCFData(),
                    )
                SecItemDelete(deleteQuery)
                val retryStatus = SecItemAdd(addQuery, null)
                if (retryStatus != 0) {
                    throw PKIException("SecItemAdd certificate failed on retry: status=$retryStatus")
                }
            } else {
                throw PKIException("SecItemAdd certificate failed: status=$status")
            }
        }

        // Update and persist index
        val index = loadCertIndex()
        index.add(alias)
        saveCertIndex(index)
    }

    fun deleteCertificateByAlias(alias: String): Boolean {
        val query: CFDictionaryRef? =
            cfDict(
                kSecClass to kSecClassCertificate,
                kSecAttrLabel to alias.toCFData(),
            )
        val deleted = SecItemDelete(query) == 0
        if (deleted) {
            // Update and persist index
            val index = loadCertIndex()
            index.remove(alias)
            saveCertIndex(index)
        }
        return deleted
    }

    fun findCertificateDERByAlias(alias: String): ByteArray? {
        val query: CFDictionaryRef? =
            cfDict(
                kSecClass to kSecClassCertificate,
                kSecAttrLabel to alias.toCFData(),
                kSecReturnData to true.toCFBoolean(),
            )

        return memScoped {
            val out = alloc<CFTypeRefVar>()
            val status: OSStatus = SecItemCopyMatching(query, out.ptr)
            if (status != 0) {
                return@memScoped null
            }
            val dataRef = out.value as CFDataRef?
            dataRef?.toByteArray()
        }
    }

    fun listCertificateAliases(): List<String> = loadCertIndex().toList()

    /**
     * Check if a key already exists in the keychain
     */
    fun keyExists(alias: String): Boolean {
        val index = loadKeyIndex()
        return index.containsKey(alias)
    }

    /**
     * Generate a new private key natively in iOS keychain using SecKeyCreateRandomKey.
     * This is the preferred method for iOS as it enables proper identity linking for mTLS.
     * Returns true if key was generated successfully, false if it already exists.
     */
    fun generateAndStoreNativeKey(
        alias: String,
        keyType: KeyTypeMapping,
        keySizeBits: Int,
    ): Boolean {
        val tag = tagFor(alias)
        val tagData = tag.toCFData() ?: throw PKIException("Failed to create tag data")

        val attributes =
            cfDict(
                platform.Security.kSecAttrKeyType to
                    when (keyType) {
                        KeyTypeMapping.EC -> platform.Security.kSecAttrKeyTypeECSECPrimeRandom
                        KeyTypeMapping.RSA -> platform.Security.kSecAttrKeyTypeRSA
                        else -> throw PKIException("Unsupported key type: $keyType")
                    },
                platform.Security.kSecAttrKeySizeInBits to keySizeBits.toCFNumber(),
                platform.Security.kSecAttrKeyClass to platform.Security.kSecAttrKeyClassPrivate,
                platform.Security.kSecAttrIsPermanent to true.toCFBoolean(),
                platform.Security.kSecAttrApplicationTag to tagData,
                platform.Security.kSecAttrAccessible to platform.Security.kSecAttrAccessibleAfterFirstUnlock,
            ) ?: throw PKIException("Failed to create key attributes")

        return memScoped {
            val error = alloc<platform.CoreFoundation.CFErrorRefVar>()
            val privateKey = platform.Security.SecKeyCreateRandomKey(attributes, error.ptr)

            if (privateKey == null) {
                val errorValue = error.value
                if (errorValue != null) {
                    val cfError = errorValue
                    val domain = platform.CoreFoundation.CFErrorGetDomain(cfError)
                    val code = platform.CoreFoundation.CFErrorGetCode(cfError)

                    // -25299 = errSecDuplicateItem - key already exists
                    if (code == -25299L) {
                        // Update index to track the key
                        val index = loadKeyIndex()
                        index[alias] = KeyMetadata(keyType, isPrivate = true)
                        saveKeyIndex(index)
                        return@memScoped false // Key exists, not newly generated
                    }

                    val errorDesc = platform.CoreFoundation.CFErrorCopyDescription(cfError)
                    throw PKIException("Failed to generate key: $errorDesc")
                } else {
                    throw PKIException("Failed to generate key: unknown error")
                }
            }

            // Update index to track the key
            val index = loadKeyIndex()
            index[alias] = KeyMetadata(keyType, isPrivate = true)
            saveKeyIndex(index)
            true // Key generated successfully
        }
    }

    /**
     * Mark a key as native (generated in keychain via SecKeyCreateRandomKey).
     */
    fun markAsNativeKey(
        alias: String,
        keyType: KeyTypeMapping,
    ) {
        val index = loadKeyIndex()
        index[alias] = KeyMetadata(keyType = keyType, isPrivate = true, isNative = true)
        saveKeyIndex(index)
    }

    /**
     * Check if a key was generated natively in the keychain.
     */
    fun isNativeKey(alias: String): Boolean {
        val index = loadKeyIndex()
        return index[alias]?.isNative == true
    }

    /**
     * Retrieve a native SecKeyRef from the keychain by alias.
     * Native keys are stored with the tag "com.sphereon.privatekey.$alias"
     */
    fun getNativeKey(alias: String): SecKeyRef? =
        memScoped {
            val tag = tagFor(alias)
            val tagData = tag.toCFData() ?: return@memScoped null

            val query =
                CFDictionaryCreateMutable(
                    null,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionaryAddValue(query, kSecClass, kSecClassKey)
            CFDictionaryAddValue(query, kSecAttrApplicationTag, tagData)
            CFDictionaryAddValue(query, kSecAttrKeyClass, kSecAttrKeyClassPrivate) // Specify we want private key
            CFDictionaryAddValue(query, kSecReturnRef, kCFBooleanTrue)

            val out = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, out.ptr)

            if (status != 0) {
                return@memScoped null
            }

            out.value as? SecKeyRef
        }
}

// ---------------------------
// Public accessor functions for keychain operations
// These can be called from the provider module
// ---------------------------

/**
 * Check if a key was generated natively in the keychain.
 */
fun isNativeKeychainKey(alias: String): Boolean = Keychain.isNativeKey(alias)

/**
 * Retrieve a native SecKeyRef from the keychain by alias.
 */
@OptIn(ExperimentalForeignApi::class)
fun getNativeKeychainKey(alias: String): SecKeyRef? = Keychain.getNativeKey(alias)

/**
 * Mark a key as native (generated in keychain).
 */
fun markAsNativeKeychainKey(
    alias: String,
    keyType: KeyTypeMapping,
) = Keychain.markAsNativeKey(alias, keyType)

// ---------------------------
// Small bridging utilities
// ---------------------------
@OptIn(ExperimentalForeignApi::class, CryptographyProviderApi::class)
private fun ByteArray.toCFData(): CFDataRef? = CFDataCreate(null, this.refToU(0), this.size.toLong())

@OptIn(ExperimentalForeignApi::class)
private fun CFDataRef.toByteArray(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    val out = ByteArray(length)
    if (length > 0) {
        val src = CFDataGetBytePtr(this)
        platform.posix.memcpy(out.refTo(0), src, length.convert())
    }
    return out
}

@OptIn(ExperimentalForeignApi::class, CryptographyProviderApi::class)
private fun String.toCFData(): CFDataRef? = this.encodeToByteArray().toCFData()

@OptIn(ExperimentalForeignApi::class)
private fun Boolean.toCFBoolean(): CFBooleanRef = if (this) kCFBooleanTrue!! else kCFBooleanFalse!!

@OptIn(ExperimentalForeignApi::class)
private fun Int.toCFNumber(): CFNumberRef? =
    memScoped {
        val v = alloc<IntVar>()
        v.value = this@toCFNumber
        CFNumberCreate(null, kCFNumberSInt32Type, v.ptr)
    }

@OptIn(ExperimentalForeignApi::class)
private fun cfDict(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef? =
    memScoped {
        val dict: CFMutableDictionaryRef? =
            CFDictionaryCreateMutable(
                allocator = null,
                capacity = pairs.size.toLong(),
                keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
                valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
            )
        for (p in pairs) {
            CFDictionaryAddValue(dict, p.first, p.second)
        }
        dict
    }
