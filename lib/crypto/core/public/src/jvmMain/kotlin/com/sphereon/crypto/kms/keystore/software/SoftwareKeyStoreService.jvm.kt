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

@file:Suppress("TooGenericExceptionCaught") // Keystore operations can fail with various JVM security exceptions

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.CoseJoseKeyMappingService.toJoseJwk
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
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
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreLoaderOpts
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyStoreAccessMode
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.certificateJwkDecode
import com.sphereon.crypto.core.x509.certificateJwkEncode
import com.sphereon.crypto.core.x509.convertToJavaPrivateKey
import com.sphereon.crypto.core.x509.javaX509CertificateFromDer
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.KeyManagerFactory
import com.sphereon.crypto.core.kms.KeyStore as KeyStoreService

/**
 * A service class for managing cryptographic keys, certificate chains, and trusted certificates
 * in software-based keystores such as PKCS12 and JKS.
 *
 * Supported keystore types:
 * - **PKCS12**: A widely used keystore format supporting both private and trusted entries.
 * - **JKS**: Java's traditional keystore format (less portable, but still in use).
 */
@AssistedInject
actual class SoftwareKeyStoreService actual constructor(
    @Assisted config: KeyStoreConfig,
) : KeyStoreService {
    private val config: SoftwareKeyStoreConfig = config as SoftwareKeyStoreConfig
    actual override val id = config.id
    actual override val keyStoreType = config.keyStoreType
    actual override val keyTypesSupported: Array<KeyTypeMapping> =
        arrayOf(
            KeyTypeMapping.RSA,
            KeyTypeMapping.EC,
            KeyTypeMapping.Symmetric,
        )
    actual override val signatureAlgorithmsSupported: Array<SignatureAlgorithm> =
        arrayOf(
            SignatureAlgorithm.ECDSA_SHA256,
            SignatureAlgorithm.ECDSA_SHA384,
            SignatureAlgorithm.ECDSA_SHA512,
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
            SignatureAlgorithm.RSA_SHA256,
            SignatureAlgorithm.RSA_SHA384,
            SignatureAlgorithm.RSA_SHA512,
        )

    // KIWA-43: Legacy property from KeyStoreService interface - return null until interface is refactored
    actual override val settings: KeyProviderSettings?
        get() = null
    private val source: KeyStoreLoaderOpts.Source
    private val password: CharArray = this.config.password?.toCharArray() ?: throw IllegalArgumentException("Password must be provided")

    // Performance: Reuse password protection object to avoid repeated allocation
    private val passwordProtection by lazy { KeyStore.PasswordProtection(password) }

    // Performance: Cache resolved key info to avoid expensive repeated conversions (DER->JWK, cert encoding)
    // Cache is invalidated on write operations (store, delete)
    private val resolvedKeyCache = ConcurrentHashMap<String, ResolvedKeyInfoType<*>>()

    // Performance: Track cache validity - set to false on any write operation
    @Volatile
    private var isCacheValid = true

    // Background scope for async I/O operations
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    // Atomic counter to track pending changes and ensure no updates are lost
    private val pendingChanges = AtomicLong(0)
    private val lastPersistedVersion = AtomicLong(0)

    // Mutex to ensure consistency for critical read operations
    private val persistenceMutex = Mutex()

    // Flag to track if persistence coroutine is currently running
    @Volatile
    private var isPersistenceRunning = false

    // Deferred that completes when all pending persistence is done
    @Volatile
    private var currentPersistenceJob: Deferred<Unit>? = null

    init {
        with(this.config) {
            require(
                keyStoreType == PredefinedKeyStoreTypes.PKCS12.keyStoreType ||
                    keyStoreType == PredefinedKeyStoreTypes.JKS.keyStoreType,
            ) {
                "A software keystore needs to be of config type ${PredefinedKeyStoreTypes.PKCS12.keyStoreType} or ${PredefinedKeyStoreTypes.JKS.keyStoreType}"
            }

            val providerPath = path
            val providerBytes = bytes
            source =
                when {
                    providerPath != null -> KeyStoreLoaderOpts.Source.File(providerPath, autoCreate = persist)
                    providerBytes != null -> KeyStoreLoaderOpts.Source.Bytes(providerBytes)
                    else -> error("Either a keystore 'path' or 'bytes' must be provided in the config")
                }
        }
    }

    private data class LoadedKeyStoreData(
        val keyStore: KeyStore,
        val resolvedSource: KeyStoreLoaderOpts.Source,
    )

    private val keyStoreData: Deferred<LoadedKeyStoreData> by lazy {
        backgroundScope.async {
            val loadedKeyStore =
                withContext(Dispatchers.IO) {
                    KeyStoreLoaderFactory.load(
                        KeyStoreLoaderOpts(
                            type = config.keyStoreType,
                            source = source,
                            keyStorePassword = String(password),
                        ),
                    )
                }
            // The KeyStoreLoaderFactory resolves path placeholders internally, but we need to capture
            // the resolved source for later use in persistence. We use the same PathPlaceholderInterpreter
            // that the KeyStoreLoaderFactory uses to ensure consistent resolution.
            val resolvedSource =
                when (val src = source) {
                    is KeyStoreLoaderOpts.Source.File -> {
                        val resolvedFile = PathPlaceholderInterpreter.resolve(src.path) ?: java.io.File(src.path)
                        src.copy(path = resolvedFile.absolutePath)
                    }

                    else -> {
                        src
                    }
                }
            LoadedKeyStoreData(loadedKeyStore, resolvedSource)
        }
    }

    val platformKeyStore by lazy { runBlocking { keyStoreData.await().keyStore } }
    val platformKeyManagerFactory by lazy {
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(platformKeyStore, password)
        }
    }

    /**
     * Lists all managed private keys stored in the underlying keystore.
     *
     * Performance optimizations:
     * - Uses cached password protection object
     * - Early filtering with isKeyEntry() before expensive getEntry() calls
     * - Parallel processing of entries using coroutines
     * - Utilizes cached resolved key info when available
     *
     * @return An array of [ManagedKeyInfoType] objects representing the private keys found in the keystore.
     * @throws IllegalStateException If the current access mode is `KeyProviderAccessMode.WRITE`.
     */
    actual override suspend fun listKeys(): Array<ManagedKeyReference> = listKeysInternal().map { it.toKeyReference() }.toTypedArray()

    /**
     * Internal version of listKeys that returns full ManagedKeyInfoType objects (with key material).
     * Used by matchKey() which needs access to the actual key data for comparison.
     */
    private suspend fun listKeysInternal(): Array<ManagedKeyInfoType<*>> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list keys in WRITE mode" }

        // Wait for any pending persistence to ensure consistency
        awaitPendingPersistence()

        val ks = keyStoreData.await().keyStore

        return withContext(Dispatchers.Default) {
            // Performance: First filter to only key entries before expensive operations
            val keyAliases = ks.aliases().toList().filter { ks.isKeyEntry(it) }

            // Performance: Process entries in parallel using async
            // Use chunked processing to avoid creating too many coroutines
            @Suppress("MagicNumber")
            val chunkSize = maxOf(1, keyAliases.size / 10) // Adaptive chunk size

            keyAliases
                .chunked(chunkSize)
                .flatMap { aliasChunk ->
                    aliasChunk
                        .map { alias ->
                            async {
                                try {
                                    // Performance: Reuse password protection object
                                    val entry = ks.getEntry(alias, passwordProtection)
                                    when (entry) {
                                        is KeyStore.PrivateKeyEntry -> {
                                            ManagedKeyInfo(
                                                alias = alias,
                                                providerId = config.id,
                                                ManagedKeyInfo(
                                                    alias,
                                                    providerId = config.id,
                                                    resolvedKeyInfo = getCachedOrResolveKeyInfo(alias, entry),
                                                ),
                                            )
                                        }

                                        is KeyStore.SecretKeyEntry -> {
                                            ManagedKeyInfo(
                                                alias = alias,
                                                providerId = config.id,
                                                ManagedKeyInfo(
                                                    alias,
                                                    providerId = config.id,
                                                    resolvedKeyInfo = resolvedKeyInfoFromSecretEntry(alias, entry),
                                                ),
                                            )
                                        }

                                        else -> {
                                            null
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Log error but don't fail the entire listing
                                    null
                                }
                            }
                        }.mapNotNull { it.await() }
                }.toTypedArray()
        }
    }

    /**
     * Retrieves a managed private key from the keystore based on the given key info.
     *
     * Performance optimizations:
     * - Uses cached password protection object
     * - Utilizes cached resolved key info to avoid expensive DER->JWK conversions
     * - Only performs keystore access when necessary
     *
     * @param keyInfo The metadata identifying the key to retrieve.
     * @return The corresponding [ManagedKeyInfoType] containing the resolved key.
     *
     * @throws NotFoundException If the key cannot be found.
     * @throws PKIException If the keystore only contains public keys and cannot provide private key info.
     * @throws IllegalArgumentException If an alias cannot be found.
     * @throws IllegalArgumentException If no kid or key has been passed in.
     */
    actual override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get keys in WRITE mode" }

        // Wait for any pending persistence to ensure consistency
        awaitPendingPersistence()

        val ks = keyStoreData.await().keyStore

        val managedKeyInfo = matchKey(keyInfo)
        val alias = managedKeyInfo.alias
        require(alias != null) { "Need to provide a alias" }

        val visibility = managedKeyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot get private key info for a public key store")
        }

        val kid = managedKeyInfo.kid ?: managedKeyInfo.key?.kid

        // Performance: Try cache first before expensive keystore access
        val cachedResolvedKeyInfo =
            if (isCacheValid) {
                resolvedKeyCache[alias]
            } else {
                null
            }
        val resolvedKeyInfo =
            if (cachedResolvedKeyInfo != null) {
                cachedResolvedKeyInfo
            } else {
                // Performance: Reuse password protection object
                val entry = ks.getEntry(alias, passwordProtection)
                when (entry) {
                    is KeyStore.PrivateKeyEntry -> getCachedOrResolveKeyInfo(alias, entry)
                    is KeyStore.SecretKeyEntry -> resolvedKeyInfoFromSecretEntry(alias, entry)
                    else -> throw NotFoundException("Could not find key for alias $alias, kid $kid")
                }
            }

        val managedKeyInfoResult =
            ManagedKeyInfo(
                alias,
                providerId = config.id,
                resolvedKeyInfo = resolvedKeyInfo,
            )

        return if (visibility === KeyVisibility.PUBLIC) {
            ManagedKeyInfo(
                alias = alias,
                providerId = managedKeyInfoResult.providerId,
                resolvedKeyInfo = managedKeyInfoResult.toResolvedPublicKeyInfo(),
            )
        } else {
            managedKeyInfoResult
        }
    }

    /**
     * Stores a resolved private key and its certificate chain in the keystore under the given alias.
     *
     * @param keyInfo The resolved key information, including the private key and certificate chain.
     * @param providerId The ID of the provider storing the key.
     * @param alias The alias under which the key will be stored.
     * @return The [ManagedKeyInfoType] representing the stored key.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.READ` mode.
     * @throws PKIException If storing a private key is attempted in a public-only keystore.
     * @throws IllegalArgumentException If the alias already exists or the certificate chain (`x5c`) is missing.
     */
    actual override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store keys in READ mode" }
        val ks = keyStoreData.await().keyStore

        val visibility = keyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot get private key info for a public key store")
        }

        if (!config.overwriteAlias) {
            check(!ks.containsAlias(alias)) { "Cannot overwrite key alias $alias, as alias already exists in keystore and overwriting is not enabled" }
        }

        val managedKeyInfo =
            ManagedKeyInfo(
                providerId = providerId,
                alias = alias,
                resolvedKeyInfo = keyInfo,
            )

        // Symmetric keys use SecretKeyEntry (no certificate chain needed)
        if (keyInfo.keyType == KeyTypeMapping.Symmetric) {
            val jwk =
                keyInfo.key as? Jwk
                    ?: throw IllegalArgumentException("Symmetric key must be a JWK")
            val kValue =
                jwk.k
                    ?: throw IllegalArgumentException("Symmetric JWK must have a 'k' field")
            val keyBytes = kValue.decodeFrom(Encoding.BASE64URL)
            val algorithmName =
                when {
                    jwk.alg?.value?.startsWith("HS") == true -> "HmacSHA${jwk.alg!!.value!!.removePrefix("HS")}"
                    else -> "HmacSHA256"
                }
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, algorithmName)
            ks.setEntry(alias, KeyStore.SecretKeyEntry(secretKey), passwordProtection)

            // Performance: Invalidate cache on write
            invalidateCache()

            if (config.persist) {
                scheduleAsyncPersistence()
            }

            return managedKeyInfo
        }

        // Perform key conversion and validation on Default dispatcher to avoid blocking I/O
        val (privateKey, certificates) =
            withContext(Dispatchers.Default) {
                val privateKey = convertToJavaPrivateKey(keyInfo)

                val certChain: MutableList<X509Certificate> =
                    when {
                        !certChain.isNullOrEmpty() -> {
                            certChain.map { it -> javaX509CertificateFromDer(it.der) }.toMutableList()
                        }

                        !keyInfo.x5c.isNullOrEmpty() -> {
                            val x5c = keyInfo.x5c!!
                            x5c
                                .map { it ->
                                    val derBytes = certificateJwkDecode(it)
                                    javaX509CertificateFromDer(derBytes)
                                }.toMutableList()
                        }

                        else -> {
                            mutableListOf()
                        }
                    }

                if (certChain.isEmpty()) {
                    throw IllegalArgumentException("Either certChain or keyInfo.x5c must be present and contain at least one certificate")
                }

                Pair(privateKey, certChain)
            }

        // Store in keystore (this is typically fast as it's in-memory)
        ks.setKeyEntry(alias, privateKey, password, certificates.toTypedArray())

        // Performance: Invalidate cache on write
        invalidateCache()

        if (config.persist) {
            scheduleAsyncPersistence()
        }

        return managedKeyInfo
    }

    /**
     * Deletes a key from the keystore based on the provided key info.
     *
     * @param keyInfo The metadata identifying the key to delete.
     * @return `true` if the key was successfully deleted, `false` otherwise.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.READ` mode.
     * @throws PKIException If the key cannot be retrieved for deletion.
     */
    actual override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete keys in READ mode" }

        val storedKeyInfo = getKey(keyInfo)

        return deleteEntry(storedKeyInfo.alias)
    }

    /**
     * Deletes an entry from the keystore by its alias.
     *
     * @param alias The alias of the entry to delete.
     * @return `true` if the entry was successfully deleted, `false` otherwise.
     */
    private suspend fun deleteEntry(alias: String): Boolean {
        val ks = keyStoreData.await().keyStore
        try {
            ks.deleteEntry(alias)

            // Performance: Invalidate cache on write
            invalidateCache()

            if (config.persist) {
                scheduleAsyncPersistence()
            }
            return true
        } catch (_: Exception) {
            // Ignored: keystore entry deletion failed for alias
        }

        return false
    }

    /**
     * Stores a certificate chain for the given alias and associated private key in the keystore.
     *
     * @param alias The alias under which the certificate chain will be stored.
     * @param certificates The array of [Certificate]s representing the certificate chain.
     * @param keyInfo The resolved key info associated with the private key; must not be null.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.READ` mode.
     * @throws IllegalArgumentException If the key info is null, the certificate array is empty, or the alias does not match the key alias.
     * @throws PKIException If storing the key or certificate chain fails.
     */
    actual override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?,
    ) {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store certificate chains in READ mode" }
        require(keyInfo != null) { "Storing a certificate chain requires keyInfo" }
        require(certificates.isNotEmpty()) { "Storing a certificate chain requires certificates to be present" }
        require(alias == keyInfo.alias) { "Alias '$alias' need to match key alias '$keyInfo.alias'" }

        val (certChain, resolvedKeyInfo) =
            withContext(Dispatchers.Default) {
                val certChain = certificates.map { it -> certificateJwkEncode(it.der) }.toTypedArray()
                val resolvedKeyInfo =
                    ResolvedKeyInfo(
                        key = keyInfo.key,
                        keyVisibility = keyInfo.keyVisibility,
                        keyType = keyInfo.keyType,
                        alias = keyInfo.alias,
                        kid = keyInfo.kid,
                        signatureAlgorithm = keyInfo.signatureAlgorithm,
                        x5c = certChain,
                    )
                Pair(certChain, resolvedKeyInfo)
            }

        storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = keyInfo.providerId ?: config.id,
            alias = alias,
        )

        if (config.persist) {
            scheduleAsyncPersistence()
        }
    }

    /**
     * Stores a single certificate in the keystore under the specified alias.
     *
     * @param alias The alias under which to store the certificate.
     * @param certificate The certificate to be stored.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.READ` mode.
     * @throws IllegalArgumentException If the alias already exists in the keystore.
     */
    actual override suspend fun storeTrustedCertificate(
        alias: String,
        certificate: Certificate,
    ) {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store certificates in READ mode" }
        val ks = keyStoreData.await().keyStore
        val exists = ks.containsAlias(alias)

        if (!config.overwriteAlias) {
            check(!exists) { "Cannot overwrite certificate alias $alias, as alias already exists in keystore and overwriting is not enabled" }
        }

        if (exists) {
            ks.deleteEntry(alias)
        }

        // Convert certificate on Default dispatcher
        val javaCert =
            withContext(Dispatchers.Default) {
                javaX509CertificateFromDer(certificate.der)
            }

        ks.setCertificateEntry(alias, javaCert)
        if (config.persist) {
            scheduleAsyncPersistence()
        }
    }

    /**
     * Lists all aliases in the keystore that have associated certificate chains.
     * Only aliases linked to private key entries are included.
     *
     * Performance optimization:
     * - Uses isKeyEntry() check to avoid unnecessary getEntry() calls when possible
     * - Uses cached password protection object
     *
     * @return An array of aliases with certificate chains.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.WRITE` mode.
     */
    actual override suspend fun listCertificateChainAliases(): Array<String> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list certificate chains in WRITE mode" }

        // Wait for any pending persistence to ensure consistency
        awaitPendingPersistence()

        val ks = keyStoreData.await().keyStore

        return ks
            .aliases()
            .toList()
            .filter { alias ->
                // Performance: Early exit if not a key entry
                if (!ks.isKeyEntry(alias)) {
                    false
                } else {
                    // Performance: Reuse password protection object
                    ks.getEntry(alias, passwordProtection) is KeyStore.PrivateKeyEntry
                }
            }.toTypedArray()
    }

    /**
     * Retrieves the certificate chain associated with the specified alias.
     *
     * @param alias The alias of the certificate chain to retrieve.
     * @return An array of [Certificate] objects forming the certificate chain.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.WRITE` mode.
     * @throws NotFoundException If no certificate chain is found for the given alias.
     */
    actual override suspend fun getCertificateChain(alias: String): Array<Certificate> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get certificate chains in WRITE mode" }

        // Wait for any pending persistence to ensure consistency
        awaitPendingPersistence()

        val ks = keyStoreData.await().keyStore

        val certChain = ks.getCertificateChain(alias)
        if (certChain === null) {
            throw NotFoundException("Could not find certificate chain for alias $alias")
        }

        return certChain.map { cert -> certificateFromDer(cert.encoded) }.toTypedArray()
    }

    /**
     * Deletes the certificate chain associated with the given alias.
     *
     * @param alias The alias of the certificate chain to delete.
     * @return `true` if the certificate chain was successfully deleted, `false` otherwise.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.READ` mode.
     */
    actual override suspend fun deleteCertificateChain(alias: String): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete certificate chains in READ mode" }
        return deleteKey(KeyInfo<Jwk>(alias = alias))
    }

    /**
     * Lists all aliases in the keystore that correspond to trusted certificates.
     *
     * @return An array of aliases for certificate entries.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.WRITE` mode.
     */
    actual override suspend fun listCertificateAliases(): Array<String> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list certificates in WRITE mode" }

        // Wait for any pending persistence to ensure consistency
        awaitPendingPersistence()

        val ks = keyStoreData.await().keyStore

        return ks
            .aliases()
            .toList()
            .filter { alias ->
                ks.isCertificateEntry(alias) || (ks.isKeyEntry(alias) && ks.getCertificateChain(alias).isNotEmpty())
            }.toTypedArray()
    }

    /**
     * Retrieves a trusted certificate by its alias from the keystore.
     *
     * @param alias The alias of the certificate to retrieve.
     * @return The [Certificate] associated with the given alias.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.WRITE` mode.
     * @throws NotFoundException If no certificate is found for the given alias.
     */
    actual override suspend fun getCertificate(alias: String): Certificate {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get certificates in WRITE mode" }

        // Wait for any pending persistence to ensure consistency
        awaitPendingPersistence()

        val ks = keyStoreData.await().keyStore

        val cert = ks.getCertificate(alias)
        if (cert === null) {
            throw NotFoundException("Could not find certificate for alias $alias")
        }

        return certificateFromDer(cert.encoded)
    }

    /**
     * Deletes a trusted certificate from the keystore by its alias.
     *
     * @param alias The alias of the certificate to delete.
     * @return `true` if the certificate was successfully deleted, `false` otherwise.
     *
     * @throws IllegalStateException If the keystore is in `KeyProviderAccessMode.READ` mode.
     * @throws IllegalArgumentException If the alias does not exist in the keystore.
     */
    actual override suspend fun deleteCertificate(alias: String): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete certificates in READ mode" }
        val ks = keyStoreData.await().keyStore

        require(ks.containsAlias(alias)) { "Could not find certificate for alias $alias" }
        return deleteEntry(alias)
    }

    actual override fun keyVisibility() = KeyVisibility.fromValue(config.keyVisibility)

    actual override fun exposesPrivateKeysForSigning(): Boolean = true

    private suspend fun matchKey(keyInfo: KeyInfoType<*>): KeyInfoType<*> {
        if (keyInfo.alias != null) {
            return keyInfo
        }

        // Step 1: metadata matching via listKeys() (no key material needed)
        val refs = listKeys()
        val metadataMatch =
            if (keyInfo.kid != null) {
                refs.find { it.alias == keyInfo.kid } ?: refs.find { it.kid == keyInfo.kid }
            } else {
                null
            }
        if (metadataMatch != null) {
            return KeyInfo<KeyType>(alias = metadataMatch.alias, kid = metadataMatch.kid, providerId = metadataMatch.providerId)
        }

        // Step 2: key-material comparison fallback (only when caller provided key material)
        if (keyInfo.key != null) {
            val allKeys = listKeysInternal()
            val materialMatch =
                allKeys.find {
                    when (keyInfo.key?.getKeyType()) {
                        KeyTypeMapping.EC -> {
                            val providedKey = keyInfo.key
                            val storedKey = it.key
                            val xMatches =
                                !providedKey?.getXAsString().isNullOrEmpty() &&
                                    !storedKey?.getXAsString().isNullOrEmpty() &&
                                    providedKey.getXAsString() == storedKey?.getXAsString()
                            val yMatches =
                                !providedKey?.getYAsString().isNullOrEmpty() &&
                                    !storedKey?.getYAsString().isNullOrEmpty() &&
                                    providedKey.getYAsString() == storedKey?.getYAsString()
                            xMatches && yMatches
                        }

                        KeyTypeMapping.RSA -> {
                            val providedKey = keyInfo.key?.let { toJoseJwk(it) }
                            val storedKey = it.key?.let { toJoseJwk(it) }
                            val nMatches =
                                !providedKey?.n.isNullOrEmpty() &&
                                    !storedKey?.n.isNullOrEmpty() &&
                                    providedKey.n == storedKey?.n
                            val eMatches =
                                !providedKey?.e.isNullOrEmpty() &&
                                    !storedKey?.e.isNullOrEmpty() &&
                                    providedKey.e == storedKey?.e
                            nMatches && eMatches
                        }

                        else -> {
                            false
                        }
                    }
                }
            if (materialMatch != null) {
                return materialMatch
            }
        }

        return keyInfo
    }

    /**
     * Waits for all pending persistence operations to complete to ensure data consistency.
     * This should be called before read operations to avoid phantom entries.
     */
    private suspend fun awaitPendingPersistence() {
        if (!config.persist) {
            return // No persistence configured, no need to wait
        }

        // Get the current persistence job if any
        val job = currentPersistenceJob

        // If there's an active job, wait for it
        job?.await()
    }

    /**
     * Schedules asynchronous persistence of the keystore to avoid blocking the calling thread.
     * This method uses an atomic counter to ensure no updates are lost, even when multiple
     * modifications happen during an ongoing persistence operation.
     */
    private fun scheduleAsyncPersistence() {
        // Increment the change counter atomically
        pendingChanges.incrementAndGet()

        // If persistence is already running, it will pick up this change
        if (isPersistenceRunning) {
            return
        }

        // Start persistence in background and track the job
        val job =
            backgroundScope.async {
                persistenceLoop()
            }
        currentPersistenceJob = job

        // Launch in background to handle cleanup
        backgroundScope.launch {
            job.await()
            // Clear the job reference when done
            if (currentPersistenceJob == job) {
                currentPersistenceJob = null
            }
        }
    }

    /**
     * Persistence loop that continues until all pending changes are persisted.
     * This ensures that no updates are lost even if they occur during persistence.
     */
    private suspend fun persistenceLoop() {
        persistenceMutex.withLock {
            // Prevent multiple persistence loops from running simultaneously
            if (isPersistenceRunning) {
                return@withLock
            }

            isPersistenceRunning = true
            try {
                while (true) {
                    val currentChanges = pendingChanges.get()
                    val lastPersisted = lastPersistedVersion.get()

                    // No new changes since last persistence
                    if (currentChanges <= lastPersisted) {
                        break
                    }

                    // Persist the current state
                    persistKeyStoreAsync()

                    // Update the last persisted version to the changes we just handled
                    lastPersistedVersion.set(currentChanges)

                    // Check if new changes arrived during persistence
                    // If so, continue the loop to persist them
                }
            } finally {
                isPersistenceRunning = false
            }
        }
    }

    /**
     * Persists the current state of the keystore asynchronously on the IO dispatcher.
     *
     * This function waits for the keystore to be initialized, then writes its contents
     * to the file specified in the `source.path`, encrypting it using the configured password.
     *
     * This operation only occurs if the keystore source is of type [KeyStoreLoaderOpts.Source.File].
     */
    private suspend fun persistKeyStoreAsync() {
        withContext(Dispatchers.IO) {
            val ks = keyStoreData.await().keyStore
            val resolvedSource = keyStoreData.await().resolvedSource
            if (resolvedSource is KeyStoreLoaderOpts.Source.File) {
                val targetFile = java.io.File(resolvedSource.path)
                val dir = targetFile.parentFile ?: java.io.File(".")
                val tmpFile = java.io.File(dir, "${targetFile.name}.tmp")
                val bakFile = java.io.File(dir, "${targetFile.name}.bak")

                // Write to temp file first and fsync
                java.io.FileOutputStream(tmpFile).use { fos ->
                    val channel = fos.channel
                    ks.store(fos, password)
                    fos.flush()
                    try {
                        channel.force(true) // durability
                    } catch (_: Throwable) {
                        // Ignored: filesystem may not support force/fsync
                    }
                }

                // Keep a backup of previous good state, then atomically replace
                if (targetFile.exists()) {
                    // Best effort backup
                    if (bakFile.exists()) {
                        bakFile.delete()
                    }
                    targetFile.renameTo(bakFile)
                }

                // Move temp -> final atomically if possible
                val moved = tmpFile.renameTo(targetFile)
                if (!moved && tmpFile.exists()) {
                    // Fallback to NIO move only if tmp file still exists
                    try {
                        java.nio.file.Files.move(
                            tmpFile.toPath(),
                            targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: Throwable) {
                        // Ignored: atomic move failed, falling back to copy/replace
                        if (tmpFile.exists()) {
                            java.nio.file.Files
                                .copy(tmpFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            tmpFile.delete()
                        }
                    }
                }

                // Cleanup backup if final is present
                if (targetFile.exists() && bakFile.exists()) {
                    bakFile.delete()
                }
            }
        }
    }

    /**
     * Persists the current state of the keystore synchronously.
     *
     * This method is kept for backward compatibility but internally delegates to
     * the async version while ensuring it completes.
     *
     * @deprecated Use scheduleAsyncPersistence() for better performance
     */
    private suspend fun persistKeyStore() {
        persistKeyStoreAsync()
    }

    private fun getSignatureAlgorithm(entry: KeyStore.PrivateKeyEntry): String? {
        val chain: Array<java.security.cert.Certificate> = entry.certificateChain
        if (chain.isEmpty()) {
            return null
        }

        val cert = chain[0]
        if (cert !is X509Certificate) {
            return null
        }

        return cert.sigAlgName
    }

    private fun resolvedKeyInfoFromSecretEntry(
        alias: String,
        entry: KeyStore.SecretKeyEntry,
    ): ResolvedKeyInfoType<*> {
        val secretKey = entry.secretKey
        val keyBytes = secretKey.encoded
        val kValue = keyBytes.encodeToBase64Url()
        val algName = secretKey.algorithm // e.g. "HmacSHA256"
        val jwaAlg =
            when {
                algName.contains("256") -> JwaAlgorithm.HS256
                algName.contains("384") -> JwaAlgorithm.HS384
                algName.contains("512") -> JwaAlgorithm.HS512
                else -> null
            }
        val jwk =
            Jwk(
                kty = JwaKeyType.oct,
                k = kValue,
                alg = jwaAlg,
                kid = alias,
            )
        return ResolvedKeyInfo(
            key = jwk,
            alias = alias,
            providerId = config.id,
            keyVisibility = KeyVisibility.fromValue(config.keyVisibility),
            keyType = KeyTypeMapping.Symmetric,
            signatureAlgorithm = jwaAlg?.let { SignatureAlgorithm.fromJose(it) },
        )
    }

    private fun resolvedKeyInfoFrom(
        alias: String,
        entry: KeyStore.PrivateKeyEntry,
    ): ResolvedKeyInfoType<*> {
        var jwk = derPrivateKeyToJwk(entry.privateKey.encoded)
        val sigAlgName = getSignatureAlgorithm(entry)
        val jwaAlgorithm = JwaAlgorithm.fromValue(sigAlgName)
        val certChain = entry.certificateChain.map { cert -> certificateJwkEncode(cert.encoded) }.toTypedArray()
        val certChainOrNull = certChain.takeIf { it.isNotEmpty() }

        // EC keys from PKCS#8 may lack the optional public key component.
        // Derive x/y from the certificate's public key when missing.
        if (jwk.kty == JwaKeyType.EC && jwk.x == null && entry.certificate != null) {
            val pubJwk = derPublicKeyToJwk(entry.certificate.publicKey.encoded)
            jwk =
                Jwk
                    .Builder()
                    .withKty(jwk.kty)
                    .withCrv(jwk.crv ?: pubJwk.crv)
                    .withD(jwk.d)
                    .withX(pubJwk.x)
                    .withY(pubJwk.y)
                    // Cert chain comes from the PKCS12 entry, not the private-key DER.
                    // `derPrivateKeyToJwk(entry.privateKey.encoded)` only sees the key
                    // material, so the original `jwk.x5c` is always null here.
                    .withX5c(certChainOrNull)
                    .build()
        } else if (jwk.x5c.isNullOrEmpty() && certChainOrNull != null) {
            // Same issue for non-EC and EC-with-x-already-present paths: the JWK derived
            // from the private-key DER carries no certificate chain. Consumers reading
            // `jwk.x5c` directly (OID4VP x509_san_dns / x509_hash signing,
            // CoseSign1 with `requireX5Chain=true`, etc.) need it on the JWK itself —
            // the outer `ResolvedKeyInfo.x5c` field isn't read by every consumer.
            jwk = jwk.copy(x5c = certChainOrNull)
        }

        return ResolvedKeyInfo(
            key = jwk,
            alias = alias,
            providerId = config.id,
            keyVisibility = KeyVisibility.fromValue(config.keyVisibility),
            keyType = KeyTypeMapping.fromValue(entry.privateKey.algorithm),
            x5c = certChain,
            signatureAlgorithm = jwaAlgorithm?.let { SignatureAlgorithm.fromJose(it) },
        )
    }

    /**
     * Performance helper: Gets cached resolved key info or resolves it from the entry.
     * This avoids expensive DER->JWK conversions and certificate chain encoding when possible.
     *
     * @param alias The alias of the key
     * @param entry The private key entry from the keystore
     * @return The resolved key info, either from cache or freshly resolved
     */
    private fun getCachedOrResolveKeyInfo(
        alias: String,
        entry: KeyStore.PrivateKeyEntry,
    ): ResolvedKeyInfoType<*> {
        // Check cache first (only if cache is valid)
        if (isCacheValid) {
            val cached = resolvedKeyCache[alias]
            if (cached != null) {
                return cached
            }
        }

        // Cache miss or invalid - resolve and cache
        val resolved = resolvedKeyInfoFrom(alias, entry)
        resolvedKeyCache[alias] = resolved
        return resolved
    }

    /**
     * Performance helper: Invalidates the resolved key info cache.
     * Called on any write operation (store, delete) to ensure cache consistency.
     */
    private fun invalidateCache() {
        isCacheValid = false
        resolvedKeyCache.clear()
        // Reset cache validity for future operations
        isCacheValid = true
    }

    /**
     * Waits for all pending persistence operations to complete.
     * This method is primarily intended for testing scenarios where you need to ensure
     * that all changes have been written to disk before verification.
     *
     * For production code, this should rarely be needed as read operations automatically
     * wait for consistency when required.
     */
    suspend fun awaitPendingPersistenceCompletion() {
        awaitPendingPersistence()
    }
}
