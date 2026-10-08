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

package com.sphereon.crypto.kms.keystore.software

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
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyStoreAccessMode
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.core.toKeyReference
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.certificateJwkDecode
import com.sphereon.crypto.core.x509.certificateJwkEncode
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise

// Runtime platform detection: true when running in Node.js, false in browser
private val isNodeJs: Boolean =
    js(
        "typeof process !== 'undefined' && process.versions != null && process.versions.node != null",
    ) as Boolean

// Lazy Node.js module loading — returns null in browser environments.
// Using eval() to prevent webpack from statically analyzing the require() calls.
private val nodeFs: dynamic by lazy {
    try {
        js("eval(\"require('node:fs')\")")
    } catch (_: dynamic) {
        null
    }
}

private val nodePath: dynamic by lazy {
    try {
        js("eval(\"require('node:path')\")")
    } catch (_: dynamic) {
        null
    }
}

private val nodeCrypto: dynamic by lazy {
    try {
        js("eval(\"require('node:crypto')\")")
    } catch (_: dynamic) {
        null
    }
}

/**
 * Backend interface for keystore persistence operations.
 * Implementations handle platform-specific storage and encryption.
 */
private interface KeyStoreBackend {
    suspend fun load(
        config: SoftwareKeyStoreConfig,
        json: Json,
    ): KeyStoreData?

    suspend fun save(
        config: SoftwareKeyStoreConfig,
        data: KeyStoreData,
        json: Json,
    )

    suspend fun exists(config: SoftwareKeyStoreConfig): Boolean
}

/**
 * Node.js backend using filesystem storage with AES-256-GCM encryption via node:crypto.
 */
private object NodeJsKeyStoreBackend : KeyStoreBackend {
    override suspend fun load(
        config: SoftwareKeyStoreConfig,
        json: Json,
    ): KeyStoreData? {
        val filePath = config.path ?: return null
        val password =
            config.password
                ?: throw IllegalArgumentException("Password required for Node.js keystore")
        val fs = nodeFs ?: throw PKIException("Node.js fs module not available")

        if (!(fs.existsSync(filePath) as Boolean)) return null

        val encryptedData = fs.readFileSync(filePath, "utf8") as String
        val decrypted = decrypt(encryptedData, password)
        return json.decodeFromString<KeyStoreData>(decrypted)
    }

    override suspend fun save(
        config: SoftwareKeyStoreConfig,
        data: KeyStoreData,
        json: Json,
    ) {
        val filePath = config.path ?: return
        val password =
            config.password
                ?: throw IllegalArgumentException("Password required for Node.js keystore")
        val fs = nodeFs ?: throw PKIException("Node.js fs module not available")
        val path = nodePath ?: throw PKIException("Node.js path module not available")

        val jsonString = json.encodeToString(data)
        val encrypted = encrypt(jsonString, password)

        // Ensure parent directory exists
        val dir = path.dirname(filePath)
        if (!(fs.existsSync(dir) as Boolean)) {
            fs.mkdirSync(dir, js("{ recursive: true }"))
        }

        // Atomic write: write to temp file, then rename
        val tmpPath = "$filePath.tmp"
        val bakPath = "$filePath.bak"

        fs.writeFileSync(tmpPath, encrypted, "utf8")

        // Keep backup of previous version if it exists
        if (fs.existsSync(filePath) as Boolean) {
            if (fs.existsSync(bakPath) as Boolean) {
                fs.unlinkSync(bakPath)
            }
            fs.renameSync(filePath, bakPath)
        }

        // Atomic rename
        fs.renameSync(tmpPath, filePath)

        // Clean up backup if successful
        if (fs.existsSync(bakPath) as Boolean) {
            fs.unlinkSync(bakPath)
        }
    }

    override suspend fun exists(config: SoftwareKeyStoreConfig): Boolean {
        val filePath = config.path ?: return false
        val fs = nodeFs ?: return false
        return fs.existsSync(filePath) as Boolean
    }

    private fun encrypt(
        data: String,
        password: String,
    ): String {
        val crypto = nodeCrypto ?: throw PKIException("Node.js crypto module not available")
        val algorithm = "aes-256-gcm"

        val salt = crypto.randomBytes(32)
        val key = crypto.pbkdf2Sync(password, salt, 100000, 32, "sha256")
        val iv = crypto.randomBytes(16)

        val cipher = crypto.createCipheriv(algorithm, key, iv)
        var encrypted = cipher.update(data, "utf8", "hex") as String
        encrypted += cipher.final("hex") as String
        val authTag = cipher.getAuthTag()

        // Return salt:iv:authTag:encrypted
        return "${salt.toString("hex")}:${iv.toString("hex")}:${authTag.toString("hex")}:$encrypted"
    }

    private fun decrypt(
        encryptedData: String,
        password: String,
    ): String {
        val crypto = nodeCrypto ?: throw PKIException("Node.js crypto module not available")
        val algorithm = "aes-256-gcm"

        val parts = encryptedData.split(":")
        require(parts.size == 4) { "Invalid encrypted data format" }

        val buffer = js("Buffer")
        val salt = buffer.from(parts[0], "hex")
        val iv = buffer.from(parts[1], "hex")
        val authTag = buffer.from(parts[2], "hex")
        val encrypted = parts[3]

        val key = crypto.pbkdf2Sync(password, salt, 100000, 32, "sha256")

        val decipher = crypto.createDecipheriv(algorithm, key, iv)
        decipher.setAuthTag(authTag)

        var decrypted = decipher.update(encrypted, "hex", "utf8") as String
        decrypted += decipher.final("utf8") as String

        return decrypted
    }
}

/**
 * Browser backend using IndexedDB for storage and Web Crypto API for AES-256-GCM encryption.
 * Falls back to in-memory only when IndexedDB or Web Crypto is unavailable.
 */
private object BrowserKeyStoreBackend : KeyStoreBackend {
    private val hasIndexedDB: Boolean =
        js("typeof indexedDB !== 'undefined'") as Boolean
    private val hasSubtleCrypto: Boolean =
        js("typeof crypto !== 'undefined' && typeof crypto.subtle !== 'undefined'") as Boolean
    private val canPersist = hasIndexedDB && hasSubtleCrypto

    override suspend fun load(
        config: SoftwareKeyStoreConfig,
        json: Json,
    ): KeyStoreData? {
        if (!config.persist || !canPersist) return null

        val dbName = config.path ?: "sphereon-keystore"
        val storeId = config.id
        val password =
            config.password
                ?: throw IllegalArgumentException("Password required for persistent browser keystore")

        val db = openDatabase(dbName, storeId)
        try {
            val encryptedData = getData(db, storeId) ?: return null
            val decrypted = decrypt(encryptedData, password)
            return json.decodeFromString<KeyStoreData>(decrypted)
        } finally {
            db.close()
        }
    }

    override suspend fun save(
        config: SoftwareKeyStoreConfig,
        data: KeyStoreData,
        json: Json,
    ) {
        if (!config.persist) return
        if (!canPersist) {
            console.warn("IndexedDB or Web Crypto not available; keystore data not persisted")
            return
        }

        val dbName = config.path ?: "sphereon-keystore"
        val storeId = config.id
        val password =
            config.password
                ?: throw IllegalArgumentException("Password required for persistent browser keystore")

        val jsonString = json.encodeToString(data)
        val encrypted = encrypt(jsonString, password)

        val db = openDatabase(dbName, storeId)
        try {
            putData(db, storeId, encrypted)
        } finally {
            db.close()
        }
    }

    override suspend fun exists(config: SoftwareKeyStoreConfig): Boolean {
        if (!config.persist || !canPersist) return false

        val dbName = config.path ?: "sphereon-keystore"
        val storeId = config.id

        val db = openDatabase(dbName, storeId)
        try {
            return getData(db, storeId) != null
        } finally {
            db.close()
        }
    }

    // --- IndexedDB helpers ---

    private suspend fun openDatabase(
        dbName: String,
        storeId: String,
    ): dynamic =
        suspendCancellableCoroutine { cont ->
            val indexedDB: dynamic = js("indexedDB")
            val request: dynamic = indexedDB.open(dbName, 1)

            request.onupgradeneeded =

                fun(event: dynamic) {
                    val db = event.target.result
                    if (!(db.objectStoreNames.contains(storeId) as Boolean)) {
                        db.createObjectStore(storeId, js("({ keyPath: 'id' })"))
                    }
                }
            request.onsuccess =

                fun(event: dynamic) {
                    cont.resume(event.target.result)
                }
            request.onerror =

                fun(event: dynamic) {
                    cont.resumeWithException(
                        PKIException("Failed to open IndexedDB '$dbName': ${event.target.error}"),
                    )
                }
        }

    private suspend fun getData(
        db: dynamic,
        storeId: String,
    ): String? =
        suspendCancellableCoroutine { cont ->
            try {
                val transaction = db.transaction(storeId, "readonly")
                val store = transaction.objectStore(storeId)
                val request = store.get(storeId)

                request.onsuccess =

                    fun(_: dynamic) {
                        val result = request.result
                        if (result == null) {
                            cont.resume(null)
                        } else {
                            cont.resume(result.data as? String)
                        }
                    }
                request.onerror =

                    fun(event: dynamic) {
                        cont.resumeWithException(
                            PKIException("Failed to read from IndexedDB: ${event.target.error}"),
                        )
                    }
            } catch (_: Throwable) {
                cont.resume(null)
            }
        }

    private suspend fun putData(
        db: dynamic,
        storeId: String,
        data: String,
    ): Unit =
        suspendCancellableCoroutine { cont ->
            val transaction = db.transaction(storeId, "readwrite")
            val store = transaction.objectStore(storeId)
            val entry: dynamic = js("({})")
            entry.id = storeId
            entry.data = data

            val request = store.put(entry)
            request.onsuccess =

                fun(_: dynamic) {
                    cont.resume(Unit)
                }
            request.onerror =

                fun(event: dynamic) {
                    cont.resumeWithException(
                        PKIException("Failed to write to IndexedDB: ${event.target.error}"),
                    )
                }
        }

    // --- Web Crypto encryption helpers ---

    private suspend fun encrypt(
        data: String,
        password: String,
    ): String {
        val subtle: dynamic = js("crypto.subtle")
        val cryptoGlobal: dynamic = js("crypto")
        val encoder: dynamic = js("new TextEncoder()")

        val passwordBytes: dynamic = encoder.encode(password)
        val dataBytes: dynamic = encoder.encode(data)

        val salt: dynamic = cryptoGlobal.getRandomValues(js("new Uint8Array(32)"))
        val iv: dynamic = cryptoGlobal.getRandomValues(js("new Uint8Array(16)"))

        // Import password as PBKDF2 key material
        val keyMaterial: dynamic =
            (
                subtle.importKey(
                    "raw",
                    passwordBytes,
                    "PBKDF2",
                    false,
                    js("['deriveKey']"),
                ) as Promise<dynamic>
            ).await()

        // Build PBKDF2 params
        val pbkdf2Params: dynamic = js("({})")
        pbkdf2Params.name = "PBKDF2"
        pbkdf2Params.salt = salt
        pbkdf2Params.iterations = 100000
        pbkdf2Params.hash = "SHA-256"

        val aesKeyParams: dynamic = js("({})")
        aesKeyParams.name = "AES-GCM"
        aesKeyParams.length = 256

        // Derive AES-256 key
        val key: dynamic =
            (
                subtle.deriveKey(
                    pbkdf2Params,
                    keyMaterial,
                    aesKeyParams,
                    false,
                    js("['encrypt', 'decrypt']"),
                ) as Promise<dynamic>
            ).await()

        // Encrypt
        val encryptParams: dynamic = js("({})")
        encryptParams.name = "AES-GCM"
        encryptParams.iv = iv

        val encryptedBuffer: dynamic =
            (
                subtle.encrypt(
                    encryptParams,
                    key,
                    dataBytes,
                ) as Promise<dynamic>
            ).await()

        // Web Crypto AES-GCM output: ciphertext || authTag (last 16 bytes)
        val encryptedArr: dynamic = js("new Uint8Array(encryptedBuffer)")
        val totalLen = encryptedArr.length as Int
        val ciphertextArr: dynamic = encryptedArr.slice(0, totalLen - 16)
        val authTagArr: dynamic = encryptedArr.slice(totalLen - 16)

        return "${uint8ArrayToHex(salt)}:${uint8ArrayToHex(iv)}:${uint8ArrayToHex(authTagArr)}:${uint8ArrayToHex(ciphertextArr)}"
    }

    private suspend fun decrypt(
        encryptedData: String,
        password: String,
    ): String {
        val subtle: dynamic = js("crypto.subtle")
        val encoder: dynamic = js("new TextEncoder()")

        val parts = encryptedData.split(":")
        require(parts.size == 4) { "Invalid encrypted data format" }

        val salt = hexToUint8Array(parts[0])
        val iv = hexToUint8Array(parts[1])
        val authTag = hexToUint8Array(parts[2])
        val ciphertext = hexToUint8Array(parts[3])

        val passwordBytes: dynamic = encoder.encode(password)

        // Import password as PBKDF2 key material
        val keyMaterial: dynamic =
            (
                subtle.importKey(
                    "raw",
                    passwordBytes,
                    "PBKDF2",
                    false,
                    js("['deriveKey']"),
                ) as Promise<dynamic>
            ).await()

        // Build PBKDF2 params
        val pbkdf2Params: dynamic = js("({})")
        pbkdf2Params.name = "PBKDF2"
        pbkdf2Params.salt = salt
        pbkdf2Params.iterations = 100000
        pbkdf2Params.hash = "SHA-256"

        val aesKeyParams: dynamic = js("({})")
        aesKeyParams.name = "AES-GCM"
        aesKeyParams.length = 256

        // Derive AES-256 key
        val key: dynamic =
            (
                subtle.deriveKey(
                    pbkdf2Params,
                    keyMaterial,
                    aesKeyParams,
                    false,
                    js("['encrypt', 'decrypt']"),
                ) as Promise<dynamic>
            ).await()

        // Reconstruct combined buffer (ciphertext || authTag) for Web Crypto
        val ctLen = ciphertext.length as Int
        val tagLen = authTag.length as Int
        val combined: dynamic = js("new Uint8Array(ctLen + tagLen)")
        combined.set(ciphertext, 0)
        combined.set(authTag, ctLen)

        // Decrypt
        val decryptParams: dynamic = js("({})")
        decryptParams.name = "AES-GCM"
        decryptParams.iv = iv

        val decryptedBuffer: dynamic =
            (
                subtle.decrypt(
                    decryptParams,
                    key,
                    combined,
                ) as Promise<dynamic>
            ).await()

        val decoder: dynamic = js("new TextDecoder()")
        return decoder.decode(decryptedBuffer) as String
    }

    private fun uint8ArrayToHex(arr: dynamic): String {
        val len = arr.length as Int
        val sb = StringBuilder(len * 2)
        for (i in 0 until len) {
            val byte = (arr[i] as Number).toInt() and 0xff
            sb.append(byte.toString(16).padStart(2, '0'))
        }
        return sb.toString()
    }

    private fun hexToUint8Array(hex: String): dynamic {
        val len = hex.length / 2
        val result: dynamic = js("new Uint8Array(len)")
        for (i in 0 until len) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16)
        }
        return result
    }
}

/**
 * A service class for managing cryptographic keys, certificate chains, and trusted certificates
 * in a software-based keystore.
 *
 * Detects the runtime environment at construction time:
 * - **Node.js**: Uses encrypted JSON files on the filesystem (node:fs, node:crypto).
 * - **Browser**: Uses IndexedDB for storage and Web Crypto API for AES-256-GCM encryption.
 *   Falls back to in-memory when IndexedDB or Web Crypto is unavailable.
 */
@AssistedInject
actual class SoftwareKeyStoreService actual constructor(
    @Assisted config: KeyStoreConfig,
) : KeyStore {
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

    // In-memory storage for the keystore data
    private var keyStoreData: KeyStoreData = KeyStoreData()
    private var isInitialized = false
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val backend: KeyStoreBackend = if (isNodeJs) NodeJsKeyStoreBackend else BrowserKeyStoreBackend

    init {
        require(
            keyStoreType == PredefinedKeyStoreTypes.FILE.keyStoreType,
        ) {
            "A JS software keystore needs to be of config type ${PredefinedKeyStoreTypes.FILE.keyStoreType} currently"
        }

        // Password required for Node.js (always uses encryption) or browser with persistence
        if (isNodeJs || this.config.persist) {
            require(this.config.password != null) {
                "Password is required for software keystore with persistence"
            }
        }
    }

    private suspend fun ensureInitialized() {
        if (isInitialized) return

        try {
            val loaded = backend.load(config, json)
            if (loaded != null) {
                keyStoreData = loaded
            } else if (config.persist) {
                persist()
            }
        } catch (expected: Throwable) {
            console.error("Failed to load keystore: ${expected.message}")
            throw PKIException("Failed to load encrypted keystore without modifying it: ${expected.message}")
        }

        isInitialized = true
    }

    private suspend fun persist() {
        if (!config.persist) return

        try {
            backend.save(config, keyStoreData, json)
        } catch (expected: Throwable) {
            console.error("Failed to persist keystore: ${expected.message}")
            throw PKIException("Failed to persist keystore: ${expected.message}")
        }
    }

    actual override suspend fun listKeys(): Array<ManagedKeyReference> = listKeysInternal().map { it.toKeyReference() }.toTypedArray()

    /**
     * Internal version of listKeys that returns full ManagedKeyInfoType objects (with key material).
     * Used by matchKey() which needs access to the actual key data for comparison.
     */
    private suspend fun listKeysInternal(): Array<ManagedKeyInfoType<*>> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list keys in WRITE mode" }
        ensureInitialized()

        return keyStoreData.keys.entries
            .mapNotNull { (alias, entry) ->
                if (entry.type == "key") {
                    ManagedKeyInfo(
                        alias = alias,
                        providerId = config.id,
                        resolvedKeyInfo = entryToResolvedKeyInfo(alias, entry),
                    )
                } else {
                    null
                }
            }.toTypedArray()
    }

    actual override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get keys in WRITE mode" }
        ensureInitialized()

        val managedKeyInfo = matchKey(keyInfo)
        val alias = managedKeyInfo.alias
        require(alias != null) { "Need to provide an alias" }

        val visibility = managedKeyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot get private key info for a public key store")
        }

        val kid = managedKeyInfo.kid ?: managedKeyInfo.key?.kid

        val entry =
            keyStoreData.keys[alias]
                ?: throw NotFoundException("Could not find key for alias $alias, kid $kid")

        val managedKeyInfoResult =
            ManagedKeyInfo(
                alias = alias,
                providerId = config.id,
                resolvedKeyInfo = entryToResolvedKeyInfo(alias, entry),
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

    actual override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store keys in READ mode" }
        ensureInitialized()

        val visibility = keyInfo.keyVisibility ?: config.keyVisibility
        if (config.keyVisibility === KeyVisibility.PUBLIC.keyVisibility && visibility === KeyVisibility.PRIVATE) {
            throw PKIException("Cannot store private key info for a public key store")
        }

        if (!config.overwriteAlias) {
            check(keyStoreData.keys[alias] == null) {
                "Cannot overwrite key alias $alias, as alias already exists in keystore and overwriting is not enabled"
            }
        }

        val certificates =
            when {
                !certChain.isNullOrEmpty() -> certChain.map { certificateJwkEncode(it.der) }.toTypedArray()
                !keyInfo.x5c.isNullOrEmpty() -> keyInfo.x5c!!
                // The encrypted JS keystore persists JWK key material directly. A certificate
                // chain is optional metadata, not a prerequisite for an asymmetric holder key.
                // OID4VCI mdoc device keys are intentionally holder-generated bare EC keys.
                else -> emptyArray()
            }
        val jwk = keyInfo.key as? Jwk ?: throw PKIException("Encrypted file keystore requires JWK key material")

        val entry =
            KeyStoreEntry(
                type = "key",
                jwk = json.encodeToString(Jwk.serializer(), jwk),
                certChain = certificates.toList(),
                keyType = keyInfo.keyType?.let { it::class.simpleName },
                signatureAlgorithm = keyInfo.signatureAlgorithm?.jose?.value,
            )

        keyStoreData.keys[alias] = entry

        // Persist after modification
        if (config.persist) {
            persist()
        }

        return ManagedKeyInfo(
            providerId = providerId,
            alias = alias,
            resolvedKeyInfo = keyInfo,
        )
    }

    actual override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete keys in READ mode" }
        ensureInitialized()

        val storedKeyInfo = getKey(keyInfo)
        return deleteEntry(storedKeyInfo.alias)
    }

    private suspend fun deleteEntry(alias: String): Boolean {
        ensureInitialized()

        val existed = keyStoreData.keys.remove(alias) != null
        if (existed && config.persist) {
            persist()
        }
        return existed
    }

    actual override fun keyVisibility() = KeyVisibility.fromValue(config.keyVisibility)

    actual override fun exposesPrivateKeysForSigning(): Boolean = true

    actual override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?,
    ) {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store certificate chains in READ mode" }
        require(keyInfo != null) { "Storing a certificate chain requires keyInfo" }
        require(certificates.isNotEmpty()) { "Storing a certificate chain requires certificates to be present" }
        require(alias == keyInfo.alias) { "Alias '$alias' need to match key alias '${keyInfo.alias}'" }
        ensureInitialized()

        val certChain = certificates.map { certificateJwkEncode(it.der) }.toTypedArray()
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

        storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = keyInfo.providerId ?: config.id,
            alias = alias,
        )
    }

    actual override suspend fun listCertificateChainAliases(): Array<String> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list certificate chains in WRITE mode" }
        ensureInitialized()

        return keyStoreData.keys.entries
            .filter { (_, entry) -> entry.type == "key" && !entry.certChain.isNullOrEmpty() }
            .map { it.key }
            .toTypedArray()
    }

    actual override suspend fun getCertificateChain(alias: String): Array<Certificate> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get certificate chains in WRITE mode" }
        ensureInitialized()

        val entry =
            keyStoreData.keys[alias]
                ?: throw NotFoundException("Could not find certificate chain for alias $alias")

        val certChain =
            entry.certChain
                ?: throw NotFoundException("Could not find certificate chain for alias $alias")

        return certChain
            .map { cert ->
                val derBytes = certificateJwkDecode(cert)
                certificateFromDer(derBytes)
            }.toTypedArray()
    }

    actual override suspend fun deleteCertificateChain(alias: String): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete certificate chains in READ mode" }
        return deleteKey(KeyInfo<Jwk>(alias = alias))
    }

    actual override suspend fun storeTrustedCertificate(
        alias: String,
        certificate: Certificate,
    ) {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot store certificates in READ mode" }
        ensureInitialized()

        if (!config.overwriteAlias) {
            check(keyStoreData.keys[alias] == null) {
                "Cannot overwrite certificate alias $alias, as alias already exists in keystore and overwriting is not enabled"
            }
        }

        val entry =
            KeyStoreEntry(
                type = "certificate",
                certificate = certificateJwkEncode(certificate.der),
            )

        keyStoreData.keys[alias] = entry

        // Persist after modification
        if (config.persist) {
            persist()
        }
    }

    actual override suspend fun listCertificateAliases(): Array<String> {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot list certificates in WRITE mode" }
        ensureInitialized()

        return keyStoreData.keys.entries
            .filter { (_, entry) ->
                entry.type == "certificate" || (entry.type == "key" && !entry.certChain.isNullOrEmpty())
            }.map { it.key }
            .toTypedArray()
    }

    actual override suspend fun getCertificate(alias: String): Certificate {
        require(config.accessMode != KeyStoreAccessMode.WRITE.accessMode) { "Cannot get certificates in WRITE mode" }
        ensureInitialized()

        val entry =
            keyStoreData.keys[alias]
                ?: throw NotFoundException("Could not find certificate for alias $alias")

        val certData =
            when (entry.type) {
                "certificate" -> entry.certificate
                "key" -> entry.certChain?.firstOrNull()
                else -> null
            } ?: throw NotFoundException("Could not find certificate for alias $alias")

        val derBytes = certificateJwkDecode(certData)
        return certificateFromDer(derBytes)
    }

    actual override suspend fun deleteCertificate(alias: String): Boolean {
        require(config.accessMode != KeyStoreAccessMode.READ.accessMode) { "Cannot delete certificates in READ mode" }
        ensureInitialized()

        require(keyStoreData.keys[alias] != null) { "Could not find certificate for alias $alias" }
        return deleteEntry(alias)
    }

    private suspend fun matchKey(keyInfo: KeyInfoType<*>): KeyInfoType<*> {
        if (keyInfo.alias != null) return keyInfo

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
            if (materialMatch != null) return materialMatch
        }

        return keyInfo
    }

    private fun entryToResolvedKeyInfo(
        alias: String,
        entry: KeyStoreEntry,
    ): ResolvedKeyInfoType<*> {
        val jwk = json.decodeFromString<Jwk>(entry.jwk ?: throw PKIException("No JWK found in entry"))
        val jwaAlgorithm = entry.signatureAlgorithm?.let { JwaAlgorithm.fromValue(it) }

        return ResolvedKeyInfo(
            key = jwk,
            alias = alias,
            providerId = config.id,
            keyVisibility = KeyVisibility.fromValue(config.keyVisibility),
            keyType = entry.keyType?.let { KeyTypeMapping.fromValue(it) },
            x5c = entry.certChain?.toTypedArray(),
            signatureAlgorithm = jwaAlgorithm?.let { SignatureAlgorithm.fromJose(it) },
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
    val signatureAlgorithm: String? = null,
)

/**
 * Internal data structure for the keystore
 */
@Serializable
private data class KeyStoreData(
    val keys: MutableMap<String, KeyStoreEntry> = mutableMapOf(),
)
