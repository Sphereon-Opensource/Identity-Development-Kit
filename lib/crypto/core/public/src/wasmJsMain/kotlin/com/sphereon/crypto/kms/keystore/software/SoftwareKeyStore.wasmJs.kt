/*
 * (c) 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.CoseJoseKeyMappingService.toJoseJwk
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyStoreAccessMode
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.certificateJwkDecode
import com.sphereon.crypto.core.x509.certificateJwkEncode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject

/**
 * A service class for managing cryptographic keys, certificate chains, and trusted certificates
 * in a software-based keystore for wasmJs environments.
 *
 * This implementation uses pure in-memory storage. File persistence and encryption are not
 * available in wasmJs contexts.
 */
@AssistedInject
actual class SoftwareKeyStoreService actual constructor(
    @Assisted config: KeyStoreConfig
) : KeyStore {
    private val config: SoftwareKeyStoreConfig = config as SoftwareKeyStoreConfig
    actual override val id = config.id
    actual override val keyStoreType = config.keyStoreType
    actual override val keyTypesSupported: Array<KeyTypeMapping> = arrayOf(
        KeyTypeMapping.RSA,
        KeyTypeMapping.EC,
        KeyTypeMapping.Symmetric
    )
    actual override val signatureAlgorithmsSupported: Array<SignatureAlgorithm> = arrayOf(
        SignatureAlgorithm.ECDSA_SHA256,
        SignatureAlgorithm.ECDSA_SHA384,
        SignatureAlgorithm.ECDSA_SHA512,
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
        SignatureAlgorithm.RSA_SHA256,
        SignatureAlgorithm.RSA_SHA384,
        SignatureAlgorithm.RSA_SHA512
    )

    actual override val settings: KeyProviderSettings?
        get() = null

    // In-memory storage for the keystore data
    private val keys: MutableMap<String, KeyStoreEntry> = mutableMapOf()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        if (this.config.persist) {
            println("WARNING: SoftwareKeyStoreService persistence is not available on wasmJs. Keys will be stored in-memory only.")
        }
    }

    actual override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list keys in WRITE mode" }

        return keys.entries.mapNotNull { (alias, entry) ->
            if (entry.type == "key") {
                ManagedKeyInfo(
                    alias = alias,
                    providerId = config.id,
                    resolvedKeyInfo = entryToResolvedKeyInfo(alias, entry)
                )
            } else null
        }.toTypedArray()
    }

    actual override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get keys in WRITE mode" }

        val managedKeyInfo = matchKey(keyInfo)
        val alias = managedKeyInfo.alias
        require(alias != null) { "Need to provide an alias" }

        val visibility = managedKeyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot get private key info for a public key store")
        }

        val kid = managedKeyInfo.kid ?: managedKeyInfo.key?.kid

        val entry = keys[alias]
            ?: throw NotFoundException("Could not find key for alias $alias, kid $kid")

        val managedKeyInfoResult = ManagedKeyInfo(
            alias = alias,
            providerId = config.id,
            resolvedKeyInfo = entryToResolvedKeyInfo(alias, entry)
        )

        return if (visibility === KeyVisibility.PUBLIC) {
            ManagedKeyInfo(
                alias = alias,
                providerId = managedKeyInfoResult.providerId,
                resolvedKeyInfo = managedKeyInfoResult.toResolvedPublicKeyInfo()
            )
        } else {
            managedKeyInfoResult
        }
    }

    actual override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store keys in READ mode" }

        val visibility = keyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot store private key info for a public key store")
        }

        if (!config.overwriteAlias) {
            check(keys[alias] == null) {
                "Cannot overwrite key alias $alias, as alias already exists in keystore and overwriting is not enabled"
            }
        }

        val certificates = when {
            !certChain.isNullOrEmpty() -> certChain.map { certificateJwkEncode(it.der) }.toTypedArray()
            !keyInfo.x5c.isNullOrEmpty() -> keyInfo.x5c!!
            else -> throw IllegalArgumentException("Either certChain or keyInfo.x5c must be present and contain at least one certificate")
        }

        val entry = KeyStoreEntry(
            type = "key",
            jwk = json.encodeToString(keyInfo.key),
            certChain = certificates.toList(),
            keyType = keyInfo.keyType?.let { it::class.simpleName },
            signatureAlgorithm = keyInfo.signatureAlgorithm?.jose?.value
        )

        keys[alias] = entry

        return ManagedKeyInfo(
            providerId = providerId,
            alias = alias,
            resolvedKeyInfo = keyInfo
        )
    }

    actual override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete keys in READ mode" }

        val storedKeyInfo = getKey(keyInfo)
        return deleteEntry(storedKeyInfo.alias)
    }

    private fun deleteEntry(alias: String): Boolean {
        return keys.remove(alias) != null
    }

    actual override fun keyVisibility() = KeyVisibility.fromValue(config.keyVisibility)

    actual override fun exposesPrivateKeysForSigning(): Boolean = true

    actual override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?
    ) {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store certificate chains in READ mode" }
        require(keyInfo != null) { "Storing a certificate chain requires keyInfo" }
        require(certificates.isNotEmpty()) { "Storing a certificate chain requires certificates to be present" }
        require(alias == keyInfo.alias) { "Alias '${alias}' need to match key alias '${keyInfo.alias}'" }

        val certChain = certificates.map { certificateJwkEncode(it.der) }.toTypedArray()
        val resolvedKeyInfo = ResolvedKeyInfo(
            key = keyInfo.key,
            keyVisibility = keyInfo.keyVisibility,
            keyType = keyInfo.keyType,
            alias = keyInfo.alias,
            kid = keyInfo.kid,
            signatureAlgorithm = keyInfo.signatureAlgorithm,
            x5c = certChain
        )

        storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = keyInfo.providerId ?: config.id,
            alias = alias
        )
    }

    actual override suspend fun listCertificateChainAliases(): Array<String> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list certificate chains in WRITE mode" }

        return keys.entries
            .filter { (_, entry) -> entry.type == "key" && !entry.certChain.isNullOrEmpty() }
            .map { it.key }
            .toTypedArray()
    }

    actual override suspend fun getCertificateChain(alias: String): Array<Certificate> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get certificate chains in WRITE mode" }

        val entry = keys[alias]
            ?: throw NotFoundException("Could not find certificate chain for alias $alias")

        val certChain = entry.certChain
            ?: throw NotFoundException("Could not find certificate chain for alias $alias")

        return certChain.map { cert ->
            val derBytes = certificateJwkDecode(cert)
            certificateFromDer(derBytes)
        }.toTypedArray()
    }

    actual override suspend fun deleteCertificateChain(alias: String): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete certificate chains in READ mode" }
        return deleteKey(KeyInfo<Jwk>(alias = alias))
    }

    actual override suspend fun storeTrustedCertificate(alias: String, certificate: Certificate) {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store certificates in READ mode" }

        if (!config.overwriteAlias) {
            check(keys[alias] == null) {
                "Cannot overwrite certificate alias $alias, as alias already exists in keystore and overwriting is not enabled"
            }
        }

        val entry = KeyStoreEntry(
            type = "certificate",
            certificate = certificateJwkEncode(certificate.der)
        )

        keys[alias] = entry
    }

    actual override suspend fun listCertificateAliases(): Array<String> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list certificates in WRITE mode" }

        return keys.entries
            .filter { (_, entry) ->
                entry.type == "certificate" || (entry.type == "key" && !entry.certChain.isNullOrEmpty())
            }
            .map { it.key }
            .toTypedArray()
    }

    actual override suspend fun getCertificate(alias: String): Certificate {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get certificates in WRITE mode" }

        val entry = keys[alias]
            ?: throw NotFoundException("Could not find certificate for alias $alias")

        val certData = when (entry.type) {
            "certificate" -> entry.certificate
            "key" -> entry.certChain?.firstOrNull()
            else -> null
        } ?: throw NotFoundException("Could not find certificate for alias $alias")

        val derBytes = certificateJwkDecode(certData)
        return certificateFromDer(derBytes)
    }

    actual override suspend fun deleteCertificate(alias: String): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete certificates in READ mode" }

        require(keys[alias] != null) { "Could not find certificate for alias $alias" }
        return deleteEntry(alias)
    }

    private suspend fun matchKey(keyInfo: KeyInfoType<*>): KeyInfoType<*> {
        var managedKeyInfo = keyInfo
        if (managedKeyInfo.alias == null) {
            val matchingKey = listKeys().find {
                if (keyInfo.kid != null) {
                    it.kid == keyInfo.kid
                } else {
                    when (keyInfo.key?.getKeyType()) {
                        KeyTypeMapping.EC -> {
                            val providedKey = keyInfo.key
                            val storedKey = it.key
                            val xMatches = !providedKey?.getXAsString().isNullOrEmpty() &&
                                    !storedKey.getXAsString().isNullOrEmpty() &&
                                    providedKey.getXAsString() == storedKey.getXAsString()
                            val yMatches = !providedKey?.getYAsString().isNullOrEmpty() &&
                                    !storedKey.getYAsString().isNullOrEmpty() &&
                                    providedKey.getYAsString() == storedKey.getYAsString()
                            xMatches && yMatches
                        }

                        KeyTypeMapping.RSA -> {
                            val providedKey = keyInfo.key?.let { toJoseJwk(it) }
                            val storedKey = toJoseJwk(it.key)
                            val nMatches = !providedKey?.n.isNullOrEmpty() &&
                                    !storedKey.n.isNullOrEmpty() &&
                                    providedKey.n == storedKey.n
                            val eMatches = !providedKey?.e.isNullOrEmpty() &&
                                    !storedKey.e.isNullOrEmpty() &&
                                    providedKey.e == storedKey.e
                            nMatches && eMatches
                        }

                        else -> false
                    }
                }
            }
            if (matchingKey != null) {
                managedKeyInfo = matchingKey
            }
        }

        return managedKeyInfo
    }

    private fun entryToResolvedKeyInfo(alias: String, entry: KeyStoreEntry): ResolvedKeyInfoType<*> {
        val jwk = json.decodeFromString<Jwk>(entry.jwk ?: throw PKIException("No JWK found in entry"))
        val jwaAlgorithm = entry.signatureAlgorithm?.let { JwaAlgorithm.fromValue(it) }

        return ResolvedKeyInfo(
            key = jwk,
            alias = alias,
            providerId = config.id,
            keyVisibility = KeyVisibility.fromValue(config.keyVisibility),
            keyType = entry.keyType?.let { KeyTypeMapping.fromValue(it) },
            x5c = entry.certChain?.toTypedArray(),
            signatureAlgorithm = jwaAlgorithm?.let { SignatureAlgorithm.fromJose(it) }
        )
    }
}

/**
 * Internal data structure for storing keystore entries
 */
@Serializable
private data class KeyStoreEntry(
    val type: String, // "key" or "certificate"
    val jwk: String? = null,
    val certChain: List<String>? = null,
    val certificate: String? = null,
    val keyType: String? = null,
    val signatureAlgorithm: String? = null
)
