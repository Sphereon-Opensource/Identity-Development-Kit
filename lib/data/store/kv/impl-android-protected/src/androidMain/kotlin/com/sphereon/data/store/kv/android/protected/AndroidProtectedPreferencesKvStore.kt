/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.data.store.kv.android.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import com.sphereon.data.store.kv.KvVersionedEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class AndroidProtectedPreferencesKvStoreFactory(
    private val application: Any,
) : KvStoreFactory {
    override val backendId: String = AndroidProtectedPreferencesKvStoreConfig.BACKEND_ID
    private val mutationMutex = Mutex()

    override fun create(
        config: KvStoreConfigBase,
        execution: SessionExecution?
    ): KvStore {
        val typed =
            config as? AndroidProtectedPreferencesKvStoreConfig
                ?: error("Android protected preferences requires AndroidProtectedPreferencesKvStoreConfig")
        val context =
            (application as? Context)?.applicationContext
                ?: error("wallet_android_application_context_unavailable")
        val partition = partitionName(typed, execution)
        return AndroidProtectedPreferencesKvStore(
            config = typed,
            preferences = context.getSharedPreferences("sphereon.wallet.kv.${sha256(partition)}", Context.MODE_PRIVATE),
            crypto = AndroidPreferenceCrypto(typed.keyAlias),
            mutationMutex = mutationMutex,
        )
    }

    private fun partitionName(
        config: AndroidProtectedPreferencesKvStoreConfig,
        execution: SessionExecution?
    ): String {
        val context = execution?.sessionContext?.context
        return when (config.scopeBinding) {
            KvStoreScopeBinding.APP -> config.id
            KvStoreScopeBinding.TENANT -> "${config.id}:${requireNotNull(context?.tenant?.tenantId)}"
            KvStoreScopeBinding.PRINCIPAL_TENANT -> "${config.id}:${requireNotNull(context?.tenant?.tenantId)}:${requireNotNull(context?.principal)}"
            KvStoreScopeBinding.SESSION -> "${config.id}:${requireNotNull(context?.tenant?.tenantId)}:${requireNotNull(context?.principal)}:${requireNotNull(execution?.sessionContext?.sessionId)}"
        }
    }
}

private class AndroidProtectedPreferencesKvStore(
    override val config: AndroidProtectedPreferencesKvStoreConfig,
    private val preferences: SharedPreferences,
    private val crypto: AndroidPreferenceCrypto,
    private val mutationMutex: Mutex,
) : KvStoreListing,
    KvStoreVersioning {
    private val json = Json { ignoreUnknownKeys = false }

    override suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration
    ): IdkResult<KvPutResult, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_PUT_FAILED") {
                val now = Clock.System.now().toEpochMilliseconds()
                val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
                val envelope = StoredEnvelope(namespace.name, key, Base64.encodeToString(namespace.codec.encode(value), Base64.NO_WRAP), now, expiresAt)
                check(preferences.edit().putString(entryId(namespace, key), crypto.encrypt(json.encodeToString(StoredEnvelope.serializer(), envelope))).commit())
                KvPutResult(KvEntryMetadata(now, expiresAt))
            }
        }

    override suspend fun <V : Any> get(
        namespace: KvNamespace<V>,
        key: String
    ): IdkResult<V?, IdkError> = getEntry(namespace, key).map { it?.value }

    override suspend fun <V : Any> getEntry(
        namespace: KvNamespace<V>,
        key: String
    ): IdkResult<KvEntry<V>?, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_GET_FAILED") {
                val envelope = read(namespace, key) ?: return@result null
                KvEntry(
                    namespace.codec.decode(Base64.decode(envelope.value, Base64.NO_WRAP)),
                    KvEntryMetadata(envelope.createdAtEpochMillis, envelope.expiresAtEpochMillis),
                )
            }
        }

    override suspend fun delete(
        namespace: KvNamespaceId,
        key: String
    ): IdkResult<Boolean, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_DELETE_FAILED") {
                val id = entryId(namespace, key)
                val existed = preferences.contains(id)
                check(preferences.edit().remove(id).commit())
                existed
            }
        }

    override suspend fun exists(
        namespace: KvNamespaceId,
        key: String
    ): IdkResult<Boolean, IdkError> = mutationMutex.withLock { result("KV_ANDROID_PROTECTED_EXISTS_FAILED") { read(namespace, key) != null } }

    override suspend fun touch(
        namespace: KvNamespaceId,
        key: String,
        ttl: Duration
    ): IdkResult<Boolean, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_TOUCH_FAILED") {
                val current = read(namespace, key) ?: return@result false
                val now = Clock.System.now().toEpochMilliseconds()
                val updated = current.copy(expiresAtEpochMillis = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds)
                check(preferences.edit().putString(entryId(namespace, key), crypto.encrypt(json.encodeToString(StoredEnvelope.serializer(), updated))).commit())
                true
            }
        }

    override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_CLEANUP_FAILED") {
                val expired =
                    decodedEntries().filter { (_, value) ->
                        (namespace == null || value.namespace == namespace.name) && value.expiresAtEpochMillis <= Clock.System.now().toEpochMilliseconds()
                    }
                val editor = preferences.edit()
                expired.forEach { (id, _) -> editor.remove(id) }
                check(editor.commit())
                expired.size
            }
        }

    override suspend fun listKeys(namespace: KvNamespaceId): IdkResult<List<String>, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_LIST_FAILED") {
                val now = Clock.System.now().toEpochMilliseconds()
                val entries = decodedEntries().filter { (_, value) -> value.namespace == namespace.name }
                val expired = entries.filter { (_, value) -> value.expiresAtEpochMillis <= now }
                if (expired.isNotEmpty()) {
                    val editor = preferences.edit()
                    expired.forEach { (id, _) -> editor.remove(id) }
                    check(editor.commit())
                }
                entries.mapNotNull { (_, value) -> value.key.takeIf { value.expiresAtEpochMillis > now } }.sorted()
            }
        }

    override suspend fun <V : Any> getHead(
        namespace: KvNamespace<V>,
        key: String
    ): IdkResult<KvVersionedEntry<V>?, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_VERSION_GET_HEAD_FAILED") {
                val chain = readVersionChain(namespace, key) ?: return@result null
                chain.entries.firstOrNull { it.versionId == chain.headVersionId }?.decode(namespace)
            }
        }

    override suspend fun <V : Any> getVersion(
        namespace: KvNamespace<V>,
        key: String,
        versionId: String,
    ): IdkResult<KvVersionedEntry<V>?, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_VERSION_GET_FAILED") {
                val now = Clock.System.now().toEpochMilliseconds()
                val chain = readVersionChain(namespace, key, now) ?: return@result null
                chain.entries.firstOrNull { it.versionId == versionId && it.expiresAtEpochMillis > now }?.decode(namespace)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun <V : Any> append(
        namespace: KvNamespace<V>,
        key: String,
        expectedPreviousVersionId: String?,
        value: V,
        ttl: Duration,
    ): IdkResult<KvVersionAppendResult<V>, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_VERSION_APPEND_FAILED") {
                val now = Clock.System.now().toEpochMilliseconds()
                val chain = readVersionChain(namespace, key, now)
                val currentHead = chain?.entries?.firstOrNull { it.versionId == chain.headVersionId }
                if (currentHead?.versionId != expectedPreviousVersionId || (chain != null && expectedPreviousVersionId == null)) {
                    return@result KvVersionAppendResult.Conflict(currentHead = currentHead?.decode(namespace))
                }

                val stored =
                    StoredVersionEnvelope(
                        versionId = Uuid.random().toString(),
                        previousVersionId = expectedPreviousVersionId,
                        value = Base64.encodeToString(namespace.codec.encode(value), Base64.NO_WRAP),
                        createdAtEpochMillis = now,
                        expiresAtEpochMillis = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds,
                    )
                val updated =
                    StoredVersionChainEnvelope(
                        headVersionId = stored.versionId,
                        entries = (chain?.entries ?: emptyList()) + stored,
                    )
                val encoded = json.encodeToString(StoredVersionChainEnvelope.serializer(), updated)
                check(preferences.edit().putString(versionChainId(namespace, key), crypto.encrypt(encoded)).commit())
                KvVersionAppendResult.Applied(entry = stored.decode(namespace))
            }
        }

    override suspend fun deleteVersioned(
        namespace: KvNamespaceId,
        key: String
    ): IdkResult<Boolean, IdkError> =
        mutationMutex.withLock {
            result("KV_ANDROID_PROTECTED_VERSION_DELETE_FAILED") {
                val id = versionChainId(namespace, key)
                val existed = preferences.contains(id)
                check(preferences.edit().remove(id).commit())
                existed
            }
        }

    private fun read(
        namespace: KvNamespaceId,
        key: String
    ): StoredEnvelope? {
        val id = entryId(namespace, key)
        val encoded = preferences.getString(id, null) ?: return null
        val envelope = decode(encoded)
        check(envelope.namespace == namespace.name && envelope.key == key) { "wallet_android_kv_entry_binding_mismatch" }
        if (envelope.expiresAtEpochMillis <= Clock.System.now().toEpochMilliseconds()) {
            check(preferences.edit().remove(id).commit())
            return null
        }
        return envelope
    }

    private fun decodedEntries(): List<Pair<String, StoredEnvelope>> =
        preferences.all
            .filterKeys { it.startsWith(ENTRY_PREFIX) }
            .map { (id, value) -> id to decode(value as? String ?: error("wallet_android_kv_entry_not_string")) }

    private fun decode(encoded: String): StoredEnvelope = json.decodeFromString(StoredEnvelope.serializer(), crypto.decrypt(encoded))

    private fun entryId(
        namespace: KvNamespaceId,
        key: String
    ): String = "$ENTRY_PREFIX${sha256("${namespace.name}\u0000$key")}"

    private fun versionChainId(
        namespace: KvNamespaceId,
        key: String
    ): String = "$VERSION_PREFIX${sha256("${namespace.name}\u0000$key")}"

    private fun readVersionChain(
        namespace: KvNamespaceId,
        key: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): StoredVersionChainEnvelope? {
        val id = versionChainId(namespace, key)
        val encoded = preferences.getString(id, null) ?: return null
        val chain = json.decodeFromString(StoredVersionChainEnvelope.serializer(), crypto.decrypt(encoded))
        val head = chain.entries.firstOrNull { it.versionId == chain.headVersionId }
        if (head == null || head.expiresAtEpochMillis <= now) {
            check(preferences.edit().remove(id).commit())
            return null
        }
        return chain
    }

    private fun <V : Any> StoredVersionEnvelope.decode(namespace: KvNamespace<V>): KvVersionedEntry<V> =
        KvVersionedEntry(
            versionId = versionId,
            previousVersionId = previousVersionId,
            value = namespace.codec.decode(Base64.decode(value, Base64.NO_WRAP)),
            metadata = KvEntryMetadata(createdAtEpochMillis, expiresAtEpochMillis),
        )

    private inline fun <T> result(
        code: String,
        block: () -> T
    ): IdkResult<T, IdkError> =
        runCatching(block).fold({ Ok(it) }, { expected ->
            Err(IdkError.fromString(code = code, category = ErrorCategory.UNAVAILABLE, message = expected.message ?: code, exception = expected as? Exception))
        })

    private companion object {
        const val ENTRY_PREFIX = "entry."
        const val VERSION_PREFIX = "version."
    }
}

@Serializable
private data class StoredEnvelope(
    val namespace: String,
    val key: String,
    val value: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

@Serializable
private data class StoredVersionEnvelope(
    val versionId: String,
    val previousVersionId: String?,
    val value: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

@Serializable
private data class StoredVersionChainEnvelope(
    val headVersionId: String,
    val entries: List<StoredVersionEnvelope>,
)

private class AndroidPreferenceCrypto(
    private val keyAlias: String
) {
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plainText.encodeToByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_BYTES) { "wallet_android_kv_ciphertext_invalid" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, combined.copyOfRange(0, IV_BYTES)))
        return cipher.doFinal(combined.copyOfRange(IV_BYTES, combined.size)).decodeToString()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
