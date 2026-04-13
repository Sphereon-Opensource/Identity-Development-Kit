/*
 * (c) 2026 Sphereon International B.V.
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

@file:OptIn(ExperimentalWasmJsInterop::class)

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop

// --- Runtime detection ---

@JsFun("() => typeof process !== 'undefined' && process.versions != null && process.versions.node != null")
private external fun detectNodeJs(): Boolean

@JsFun("() => typeof indexedDB !== 'undefined'")
private external fun detectIndexedDB(): Boolean

@JsFun("() => typeof crypto !== 'undefined' && typeof crypto.subtle !== 'undefined'")
private external fun detectSubtleCrypto(): Boolean

@JsFun("(msg) => console.error(msg)")
private external fun consoleError(msg: JsString)

@JsFun("(msg) => console.warn(msg)")
private external fun consoleWarn(msg: JsString)

// --- Node.js module loading ---

@JsFun("() => { try { return eval(\"require('node:fs')\"); } catch(e) { return null; } }")
private external fun loadNodeFs(): JsAny?

@JsFun("() => { try { return eval(\"require('node:path')\"); } catch(e) { return null; } }")
private external fun loadNodePath(): JsAny?

// --- Node.js file operations ---

@JsFun("(fs, path) => fs.existsSync(path)")
private external fun fsExistsSync(
    fs: JsAny,
    path: JsString,
): Boolean

@JsFun("(fs, path) => fs.readFileSync(path, 'utf8')")
private external fun fsReadFileSync(
    fs: JsAny,
    path: JsString,
): JsString

@JsFun("(fs, path, data) => fs.writeFileSync(path, data, 'utf8')")
private external fun fsWriteFileSync(
    fs: JsAny,
    path: JsString,
    data: JsString,
)

@JsFun("(fs, path) => fs.unlinkSync(path)")
private external fun fsUnlinkSync(
    fs: JsAny,
    path: JsString,
)

@JsFun("(fs, oldPath, newPath) => fs.renameSync(oldPath, newPath)")
private external fun fsRenameSync(
    fs: JsAny,
    oldPath: JsString,
    newPath: JsString,
)

@JsFun("(fs, dir) => { if (!fs.existsSync(dir)) { fs.mkdirSync(dir, { recursive: true }); } }")
private external fun fsEnsureDir(
    fs: JsAny,
    dir: JsString,
)

@JsFun("(pathMod, filePath) => pathMod.dirname(filePath)")
private external fun pathDirname(
    pathMod: JsAny,
    filePath: JsString,
): JsString

// --- Node.js crypto operations ---

@JsFun(
    """(data, password) => {
    const crypto = eval("require('node:crypto')");
    const algorithm = 'aes-256-gcm';
    const salt = crypto.randomBytes(32);
    const key = crypto.pbkdf2Sync(password, salt, 100000, 32, 'sha256');
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv(algorithm, key, iv);
    let encrypted = cipher.update(data, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    const authTag = cipher.getAuthTag();
    return salt.toString('hex') + ':' + iv.toString('hex') + ':' + authTag.toString('hex') + ':' + encrypted;
}""",
)
private external fun nodeEncryptImpl(
    data: JsString,
    password: JsString,
): JsString

@JsFun(
    """(encryptedData, password) => {
    const crypto = eval("require('node:crypto')");
    const algorithm = 'aes-256-gcm';
    const parts = encryptedData.split(':');
    if (parts.length !== 4) throw new Error('Invalid encrypted data format');
    const salt = Buffer.from(parts[0], 'hex');
    const iv = Buffer.from(parts[1], 'hex');
    const authTag = Buffer.from(parts[2], 'hex');
    const encrypted = parts[3];
    const key = crypto.pbkdf2Sync(password, salt, 100000, 32, 'sha256');
    const decipher = crypto.createDecipheriv(algorithm, key, iv);
    decipher.setAuthTag(authTag);
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
}""",
)
private external fun nodeDecryptImpl(
    encryptedData: JsString,
    password: JsString,
): JsString

// --- Browser IndexedDB operations (callback-based async) ---

@JsFun(
    """(dbName, storeId, onSuccess, onError) => {
    if (typeof indexedDB === 'undefined') {
        onError('IndexedDB not available');
        return;
    }
    const request = indexedDB.open(dbName, 1);
    request.onupgradeneeded = (event) => {
        const db = event.target.result;
        if (!db.objectStoreNames.contains(storeId)) {
            db.createObjectStore(storeId, { keyPath: 'id' });
        }
    };
    request.onsuccess = (event) => onSuccess(event.target.result);
    request.onerror = (event) => onError('Failed to open IndexedDB: ' + event.target.error);
}""",
)
private external fun idbOpenAsync(
    dbName: JsString,
    storeId: JsString,
    onSuccess: (JsAny) -> Unit,
    onError: (JsAny) -> Unit,
)

@JsFun(
    """(db, storeId, onSuccess, onError) => {
    try {
        const tx = db.transaction(storeId, 'readonly');
        const store = tx.objectStore(storeId);
        const request = store.get(storeId);
        request.onsuccess = () => onSuccess(request.result ? request.result.data : null);
        request.onerror = (e) => onError('IDB read error: ' + e.target.error);
    } catch (e) {
        onSuccess(null);
    }
}""",
)
private external fun idbGetAsync(
    db: JsAny,
    storeId: JsString,
    onSuccess: (JsAny?) -> Unit,
    onError: (JsAny) -> Unit,
)

@JsFun(
    """(db, storeId, data, onSuccess, onError) => {
    const tx = db.transaction(storeId, 'readwrite');
    const store = tx.objectStore(storeId);
    const request = store.put({ id: storeId, data: data });
    request.onsuccess = () => onSuccess();
    request.onerror = (e) => onError('IDB write error: ' + e.target.error);
}""",
)
private external fun idbPutAsync(
    db: JsAny,
    storeId: JsString,
    data: JsString,
    onSuccess: () -> Unit,
    onError: (JsAny) -> Unit,
)

@JsFun("(db) => db.close()")
private external fun idbClose(db: JsAny)

// --- Browser Web Crypto operations (callback-based async) ---

@JsFun(
    """(data, password, onSuccess, onError) => {
    (async () => {
        try {
            const enc = new TextEncoder();
            const passwordBytes = enc.encode(password);
            const dataBytes = enc.encode(data);
            const salt = crypto.getRandomValues(new Uint8Array(32));
            const iv = crypto.getRandomValues(new Uint8Array(16));
            const toHex = a => Array.from(a).map(b => b.toString(16).padStart(2, '0')).join('');
            const km = await crypto.subtle.importKey('raw', passwordBytes, 'PBKDF2', false, ['deriveKey']);
            const key = await crypto.subtle.deriveKey(
                { name: 'PBKDF2', salt, iterations: 100000, hash: 'SHA-256' },
                km, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
            const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, dataBytes);
            const arr = new Uint8Array(encrypted);
            const ct = arr.slice(0, arr.length - 16);
            const tag = arr.slice(arr.length - 16);
            onSuccess(toHex(salt) + ':' + toHex(iv) + ':' + toHex(tag) + ':' + toHex(ct));
        } catch (e) {
            onError('' + e);
        }
    })();
}""",
)
private external fun browserEncryptAsync(
    data: JsString,
    password: JsString,
    onSuccess: (JsAny) -> Unit,
    onError: (JsAny) -> Unit,
)

@JsFun(
    """(encryptedData, password, onSuccess, onError) => {
    (async () => {
        try {
            const parts = encryptedData.split(':');
            if (parts.length !== 4) throw new Error('Invalid encrypted data format');
            const fromHex = h => new Uint8Array(h.match(/.{2}/g).map(b => parseInt(b, 16)));
            const salt = fromHex(parts[0]);
            const iv = fromHex(parts[1]);
            const authTag = fromHex(parts[2]);
            const ciphertext = fromHex(parts[3]);
            const combined = new Uint8Array(ciphertext.length + authTag.length);
            combined.set(ciphertext, 0);
            combined.set(authTag, ciphertext.length);
            const enc = new TextEncoder();
            const passwordBytes = enc.encode(password);
            const km = await crypto.subtle.importKey('raw', passwordBytes, 'PBKDF2', false, ['deriveKey']);
            const key = await crypto.subtle.deriveKey(
                { name: 'PBKDF2', salt, iterations: 100000, hash: 'SHA-256' },
                km, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
            const decrypted = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, combined.buffer);
            onSuccess(new TextDecoder().decode(decrypted));
        } catch (e) {
            onError('' + e);
        }
    })();
}""",
)
private external fun browserDecryptAsync(
    encryptedData: JsString,
    password: JsString,
    onSuccess: (JsAny) -> Unit,
    onError: (JsAny) -> Unit,
)

// --- JsAny to/from String helper ---

@JsFun("(v) => '' + v")
private external fun jsToString(v: JsAny): JsString

// --- Runtime state ---

private val isNodeJs: Boolean = detectNodeJs()
private val canPersistInBrowser: Boolean by lazy { detectIndexedDB() && detectSubtleCrypto() }

// Lazy-loaded Node.js modules
private var nodeFs: JsAny? = null
private var nodePath: JsAny? = null

private fun getNodeFs(): JsAny {
    if (nodeFs == null) nodeFs = loadNodeFs()
    return nodeFs ?: throw PKIException("Node.js fs module not available")
}

private fun getNodePath(): JsAny {
    if (nodePath == null) nodePath = loadNodePath()
    return nodePath ?: throw PKIException("Node.js path module not available")
}

/**
 * Backend interface for keystore persistence operations.
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
        val fs = getNodeFs()

        if (!fsExistsSync(fs, filePath.toJsString())) return null

        val encryptedData = fsReadFileSync(fs, filePath.toJsString()).toString()
        val decrypted = nodeDecryptImpl(encryptedData.toJsString(), password.toJsString()).toString()
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
        val fs = getNodeFs()
        val pathMod = getNodePath()

        val jsonString = json.encodeToString(data)
        val encrypted = nodeEncryptImpl(jsonString.toJsString(), password.toJsString()).toString()

        // Ensure parent directory exists
        val dir = pathDirname(pathMod, filePath.toJsString())
        fsEnsureDir(fs, dir)

        // Atomic write: write to temp file, then rename
        val tmpPath = "$filePath.tmp"
        val bakPath = "$filePath.bak"

        fsWriteFileSync(fs, tmpPath.toJsString(), encrypted.toJsString())

        // Keep backup of previous version if it exists
        if (fsExistsSync(fs, filePath.toJsString())) {
            if (fsExistsSync(fs, bakPath.toJsString())) {
                fsUnlinkSync(fs, bakPath.toJsString())
            }
            fsRenameSync(fs, filePath.toJsString(), bakPath.toJsString())
        }

        // Atomic rename
        fsRenameSync(fs, tmpPath.toJsString(), filePath.toJsString())

        // Clean up backup if successful
        if (fsExistsSync(fs, bakPath.toJsString())) {
            fsUnlinkSync(fs, bakPath.toJsString())
        }
    }

    override suspend fun exists(config: SoftwareKeyStoreConfig): Boolean {
        val filePath = config.path ?: return false
        val fs = nodeFs ?: loadNodeFs() ?: return false
        return fsExistsSync(fs, filePath.toJsString())
    }
}

/**
 * Browser backend using IndexedDB for storage and Web Crypto API for AES-256-GCM encryption.
 * Falls back to in-memory only when IndexedDB or Web Crypto is unavailable.
 */
private object BrowserKeyStoreBackend : KeyStoreBackend {
    override suspend fun load(
        config: SoftwareKeyStoreConfig,
        json: Json,
    ): KeyStoreData? {
        if (!config.persist || !canPersistInBrowser) return null

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
            idbClose(db)
        }
    }

    override suspend fun save(
        config: SoftwareKeyStoreConfig,
        data: KeyStoreData,
        json: Json,
    ) {
        if (!config.persist) return
        if (!canPersistInBrowser) {
            consoleWarn("IndexedDB or Web Crypto not available; keystore data not persisted".toJsString())
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
            idbClose(db)
        }
    }

    override suspend fun exists(config: SoftwareKeyStoreConfig): Boolean {
        if (!config.persist || !canPersistInBrowser) return false

        val dbName = config.path ?: "sphereon-keystore"
        val storeId = config.id

        val db = openDatabase(dbName, storeId)
        try {
            return getData(db, storeId) != null
        } finally {
            idbClose(db)
        }
    }

    // --- Suspend wrappers for callback-based IndexedDB ops ---

    private suspend fun openDatabase(
        dbName: String,
        storeId: String,
    ): JsAny =
        suspendCoroutine { cont ->
            idbOpenAsync(
                dbName.toJsString(),
                storeId.toJsString(),
                onSuccess = { db -> cont.resume(db) },
                onError = { error ->
                    cont.resumeWithException(PKIException("IndexedDB open failed: ${jsToString(error)}"))
                },
            )
        }

    private suspend fun getData(
        db: JsAny,
        storeId: String,
    ): String? =
        suspendCoroutine { cont ->
            idbGetAsync(
                db,
                storeId.toJsString(),
                onSuccess = { result ->
                    if (result == null) {
                        cont.resume(null)
                    } else {
                        cont.resume(jsToString(result).toString())
                    }
                },
                onError = { error ->
                    cont.resumeWithException(PKIException("IndexedDB read failed: ${jsToString(error)}"))
                },
            )
        }

    private suspend fun putData(
        db: JsAny,
        storeId: String,
        data: String,
    ): Unit =
        suspendCoroutine { cont ->
            idbPutAsync(
                db,
                storeId.toJsString(),
                data.toJsString(),
                onSuccess = { cont.resume(Unit) },
                onError = { error ->
                    cont.resumeWithException(PKIException("IndexedDB write failed: ${jsToString(error)}"))
                },
            )
        }

    // --- Suspend wrappers for callback-based Web Crypto ops ---

    private suspend fun encrypt(
        data: String,
        password: String,
    ): String =
        suspendCoroutine { cont ->
            browserEncryptAsync(
                data.toJsString(),
                password.toJsString(),
                onSuccess = { result -> cont.resume(jsToString(result).toString()) },
                onError = { error ->
                    cont.resumeWithException(PKIException("Web Crypto encrypt failed: ${jsToString(error)}"))
                },
            )
        }

    private suspend fun decrypt(
        encryptedData: String,
        password: String,
    ): String =
        suspendCoroutine { cont ->
            browserDecryptAsync(
                encryptedData.toJsString(),
                password.toJsString(),
                onSuccess = { result -> cont.resume(jsToString(result).toString()) },
                onError = { error ->
                    cont.resumeWithException(PKIException("Web Crypto decrypt failed: ${jsToString(error)}"))
                },
            )
        }
}

/**
 * A service class for managing cryptographic keys, certificate chains, and trusted certificates
 * in a software-based keystore for wasmJs environments.
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
            consoleError("Failed to load keystore: ${expected.message}".toJsString())
            if (config.persist) {
                keyStoreData = KeyStoreData()
                persist()
            }
        }

        isInitialized = true
    }

    private suspend fun persist() {
        if (!config.persist) return

        try {
            backend.save(config, keyStoreData, json)
        } catch (expected: Throwable) {
            consoleError("Failed to persist keystore: ${expected.message}".toJsString())
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
                else -> throw IllegalArgumentException("Either certChain or keyInfo.x5c must be present and contain at least one certificate")
            }

        val entry =
            KeyStoreEntry(
                type = "key",
                jwk = json.encodeToString(keyInfo.key),
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
