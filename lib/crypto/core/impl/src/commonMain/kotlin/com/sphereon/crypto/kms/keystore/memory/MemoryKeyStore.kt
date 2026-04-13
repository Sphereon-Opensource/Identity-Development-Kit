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
 *
 */

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject

class PublicFromPrivateKeyStore(
    private val privateKeyStore: KeyStoreService,
) : KeyStoreService {
    // KIWA-43: Legacy property from KeyStoreService interface - return null until interface is refactored
    override val settings: KeyProviderSettings?
        get() = null

    init {
        require(privateKeyStore.keyVisibility() === KeyVisibility.PRIVATE) { "A public from private key store needs to have a private key store exposing private keys" }
        // TODO: Is that true? If the "private" keystore is using hardware, we simply could delegate as that would only expose pub keys anyway
    }

    override suspend fun listKeys(): Array<ManagedKeyReference> = privateKeyStore.listKeys()

    override suspend fun getKey(keyInfo: KeyInfoType<*>) = privateKeyStore.getKey(keyInfo).toManagedPublicKeyInfo()

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        require(keyInfo.keyVisibility === KeyVisibility.PRIVATE) {
            "Public key to private key store adapter is backed by a private key store. This means for storing you can only use private keys, as the key would otherwise not be backed"
        }
        return privateKeyStore.storeKey(keyInfo, providerId, alias).toManagedPublicKeyInfo()
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean = privateKeyStore.deleteKey(keyInfo)

    override fun keyVisibility() = KeyVisibility.PUBLIC
}

@JsExportCompat
@dev.zacsweers.metro.AssistedFactory
interface MemoryKeyStoreServiceFactory {
    fun create(
        @Assisted config: KeyStoreConfig,
        @Assisted backingStorage: MemoryKeyStoreBackingStorage?,
        @Assisted partitionKey: StoragePartitionKey?,
    ): MemoryKeyStoreService
}

@AssistedInject
class MemoryKeyStoreService(
    @Assisted private val config: KeyStoreConfig,
    @Assisted private val backingStorage: MemoryKeyStoreBackingStorage? = null,
    @Assisted private val partitionKey: StoragePartitionKey? = null,
) : KeyStore {
    override val id = config.id
    override val keyStoreType = config.keyStoreType
    override val keyTypesSupported = KeyTypeMapping.asList.toTypedArray()
    override val signatureAlgorithmsSupported = SignatureAlgorithm.asList.toTypedArray() // config.signatureAlgorithmsSupported

    // KIWA-43: Legacy property from KeyStoreService interface - return null until interface is refactored
    override val settings: KeyProviderSettings?
        get() = null

    // Legacy: direct storage (session-scoped behavior when instantiated directly)
    private val directKeys = mutableMapOf<String, ManagedKeyInfoType<*>>()
    private val directCertificateChains = mutableMapOf<String, Array<Certificate>>()
    private val directCertificates = mutableMapOf<String, Certificate>()

    // New: partitioned storage accessed via backing storage
    private val partition: StoragePartition?
        get() = backingStorage?.let { storage -> partitionKey?.let { key -> storage.getPartition(key) } }

    private val keys: MutableMap<String, ManagedKeyInfoType<*>>
        get() = partition?.keys ?: directKeys

    private val certificateChains: MutableMap<String, Array<Certificate>>
        get() = partition?.certificateChains ?: directCertificateChains

    private val certificates: MutableMap<String, Certificate>
        get() = partition?.certificates ?: directCertificates

    init {
        require(config.keyStoreType === PredefinedKeyStoreTypes.MEMORY.keyStoreType) { "A memory keystore needs to be of config type MEMORY" }
    }

    override fun exposesPrivateKeysForSigning(): Boolean = true

    override fun keyVisibility() = KeyVisibility.fromValue(config.keyVisibility)

    override suspend fun listKeys(): Array<ManagedKeyReference> = keys.map { it.value.toKeyReference() }.toTypedArray()

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        var managedKeyInfo = keyInfo
        if (managedKeyInfo.alias === null) {
            val matchingKey =
                keys.values.find { (keyInfo.kid !== null && it.kid == keyInfo.kid) || (keyInfo.key?.getXAsString() == it.key.getXAsString() && keyInfo.key?.getYAsString() == it.key.getYAsString()) }
            if (matchingKey != null) {
                managedKeyInfo = matchingKey
            }
        }

        require(managedKeyInfo.key !== null || managedKeyInfo.kid !== null || managedKeyInfo.alias !== null) { "Either a kid needs to be provided or a key needs to be passed in" }
        val visibility = managedKeyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot get private key info for a public key store")
        }

        val kid = managedKeyInfo.kid ?: managedKeyInfo.key?.kid

        val alias = managedKeyInfo.alias
        require(alias !== null) { "Need to provide a alias" }
        val keyInfoResult = keys[alias]
        require(keyInfoResult !== null) { "Could not find key for alias $alias, kid $kid" }
        return if (visibility === KeyVisibility.PUBLIC) {
            ManagedKeyInfo(
                alias = alias,
                providerId = keyInfoResult.providerId,
                resolvedKeyInfo = keyInfoResult.toResolvedPublicKeyInfo(),
            )
        } else {
            require(keyInfoResult.keyVisibility === KeyVisibility.PRIVATE) {
                "Key with alias $alias is not a private key, whilst a private key is requested"
            }
            keyInfoResult
        }
    }

    /**
     * Delete a key from the store.
     * @return true if the key was found and deleted, false if the key was not found
     */
    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean =
        try {
            val storedKeyInfo = getKey(keyInfo)
            keys.remove(storedKeyInfo.alias) != null
        } catch (_: NotFoundException) {
            // Ignored: key not found is expected for delete
            false
        } catch (_: IllegalArgumentException) {
            // Ignored: key lookup failed, nothing to delete
            false
        }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        val visibility = keyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot get private key info for a public key store")
        }
        val managedKeyInfo = ManagedKeyInfo(providerId = providerId, alias = alias, resolvedKeyInfo = keyInfo)
        if (!config.overwriteAlias) {
            check(!this.keys.containsKey(alias)) { "Cannot overwrite key alias $alias, as alias already exists in keystore and overwriting is not enabled" }
        }
        this.keys[alias] = managedKeyInfo
        return managedKeyInfo
    }

    override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?,
    ) {
        if (!config.overwriteAlias) {
            check(!this.certificateChains.containsKey(alias)) { "Cannot overwrite certificate chain alias $alias, as alias already exists in keystore and overwriting is not enabled" }
        }
        if (certificates.size == 1) {
            // Let's also store the cert itself
            this.certificates[alias] = certificates[0]
        }
        certificateChains[alias] = certificates
    }

    override suspend fun listCertificateChainAliases(): Array<String> = certificateChains.keys.toTypedArray()

    override suspend fun getCertificateChain(alias: String): Array<Certificate> =
        certificateChains[alias] ?: certificates[alias]?.let {
            arrayOf(it)
        } ?: throw NotFoundException("Could not find certificate chain for alias $alias")

    /**
     * Delete a certificate chain from the store.
     * @return true if the chain was found and deleted, false if not found
     */
    override suspend fun deleteCertificateChain(alias: String): Boolean = certificateChains.remove(alias) != null

    override suspend fun storeTrustedCertificate(
        alias: String,
        certificate: Certificate,
    ) {
        if (!config.overwriteAlias) {
            check(!this.certificates.containsKey(alias)) { "Cannot overwrite certificate alias $alias, as alias already exists in keystore and overwriting is not enabled" }
        }
        certificates[alias] = certificate
    }

    override suspend fun listCertificateAliases(): Array<String> = certificates.keys.toTypedArray()

    override suspend fun getCertificate(alias: String): Certificate = certificates[alias] ?: certificateChains[alias]?.get(0) ?: throw NotFoundException("Could not find certificate for alias $alias")

    /**
     * Delete a certificate from the store.
     * @return true if the certificate was found and deleted, false if not found
     */
    override suspend fun deleteCertificate(alias: String): Boolean = certificates.remove(alias) != null
}
