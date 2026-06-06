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

package com.sphereon.openid.oid4vci.rest.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AbstractLogManager
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvEntry
import com.sphereon.data.store.kv.KvEntryMetadata
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvNamespaceId
import com.sphereon.data.store.kv.KvPutResult
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferTemplate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration

class KvCredentialOfferSessionStoreTest {
    private fun createStore(): KvCredentialOfferSessionStore {
        val kvStoreManager = StoreTestKvStoreManager()
        val kvStoreService = NoOpKvStoreService()
        val execution = StoreTestSessionExecution()
        val logManager = StoreTestAppLogManager()
        return KvCredentialOfferSessionStore(
            kvStoreManager = kvStoreManager,
            kvStoreService = kvStoreService,
            execution = execution,
            appLogManager = logManager,
        )
    }

    @Test
    fun getByOfferIdReturnsSessionAfterCreate() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().toEpochMilliseconds()
            val session =
                CredentialOfferSession(
                    correlationId = "corr-abc",
                    offerId = "offer-xyz",
                    status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                    createdAt = now,
                    lastUpdatedAt = now,
                )

            val createResult = store.create(session)
            assertTrue(createResult.isOk, "create should succeed")

            val lookupResult = store.getByOfferId("offer-xyz")
            assertTrue(lookupResult.isOk, "getByOfferId should succeed")
            assertNotNull(lookupResult.value)
            assertEquals("corr-abc", lookupResult.value!!.correlationId)
            assertEquals("offer-xyz", lookupResult.value!!.offerId)
        }

    @Test
    fun getByOfferIdReturnsNullForNonexistentOfferId() =
        runTest {
            val store = createStore()

            val lookupResult = store.getByOfferId("nonexistent-offer")
            assertTrue(lookupResult.isOk, "getByOfferId should succeed for missing id")
            assertNull(lookupResult.value, "value should be null for nonexistent offerId")
        }

    @Test
    fun offerTemplateRoundTripsIntact() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().toEpochMilliseconds()
            val template =
                CredentialOfferTemplate(
                    issuerId = "https://issuer.example.com/oid4vci",
                    credentialConfigurationIds = listOf("TestCred", "OtherCred"),
                    preAuthorizedCodeGrant = true,
                    authorizationCodeGrant = false,
                    txCodeRequired = true,
                    preSeededAttributes = mapOf("given_name" to JsonPrimitive("Ada")),
                    offerTtlSeconds = 1200,
                    scheme = "haip://",
                )
            val session =
                CredentialOfferSession(
                    correlationId = "corr-tmpl",
                    offerId = "offer-tmpl",
                    status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                    createdAt = now,
                    lastUpdatedAt = now,
                    offerTemplate = template,
                )

            assertTrue(store.create(session).isOk, "create should succeed")

            val loaded = store.getByOfferId("offer-tmpl")
            assertTrue(loaded.isOk, "getByOfferId should succeed")
            assertNotNull(loaded.value)
            assertEquals(template, loaded.value!!.offerTemplate)
        }

    @Test
    fun nullOfferTemplateRoundTripsAsNull() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().toEpochMilliseconds()
            val session =
                CredentialOfferSession(
                    correlationId = "corr-no-tmpl",
                    offerId = "offer-no-tmpl",
                    status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                    createdAt = now,
                    lastUpdatedAt = now,
                )

            assertTrue(store.create(session).isOk, "create should succeed")

            val loaded = store.getByOfferId("offer-no-tmpl")
            assertTrue(loaded.isOk, "getByOfferId should succeed")
            assertNotNull(loaded.value)
            assertNull(loaded.value!!.offerTemplate, "absent template must round-trip as null")
        }

    @Test
    fun getByOfferIdReturnsNullAfterDelete() =
        runTest {
            val store = createStore()
            val now = Clock.System.now().toEpochMilliseconds()
            val session =
                CredentialOfferSession(
                    correlationId = "corr-del",
                    offerId = "offer-del",
                    status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                    createdAt = now,
                    lastUpdatedAt = now,
                )

            store.create(session)
            store.delete("corr-del")

            val lookupResult = store.getByOfferId("offer-del")
            assertTrue(lookupResult.isOk, "getByOfferId should succeed after delete")
            assertNull(lookupResult.value, "value should be null after delete")
        }
}

// ---------------------------------------------------------------------------
// Minimal test support types
// ---------------------------------------------------------------------------

private class StoreTestKvStore : KvStore {
    override val config: KvStoreConfigBase =
        InMemoryKvStoreConfig(id = "test-sessions", scopeBinding = KvStoreScopeBinding.APP)

    private data class Stored(
        val bytes: ByteArray,
        val createdAt: Long,
        val expiresAt: Long
    )

    private val entries = mutableMapOf<String, Stored>()

    private fun compositeKey(
        namespace: KvNamespaceId,
        key: String,
    ) = "${namespace.name}:$key"

    override suspend fun <V : Any> put(
        namespace: KvNamespace<V>,
        key: String,
        value: V,
        ttl: Duration,
    ): IdkResult<KvPutResult, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val expiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
        entries[compositeKey(namespace, key)] = Stored(bytes = namespace.codec.encode(value), createdAt = now, expiresAt = expiresAt)
        return Ok(KvPutResult(metadata = KvEntryMetadata(createdAtEpochMillis = now, expiresAtEpochMillis = expiresAt)))
    }

    override suspend fun <V : Any> get(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<V?, IdkError> = getEntry(namespace, key).map { it?.value }

    override suspend fun <V : Any> getEntry(
        namespace: KvNamespace<V>,
        key: String,
    ): IdkResult<KvEntry<V>?, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stored = entries[compositeKey(namespace, key)] ?: return Ok(null)
        if (stored.expiresAt <= now) {
            entries.remove(compositeKey(namespace, key))
            return Ok(null)
        }
        val value = namespace.codec.decode(stored.bytes)
        return Ok(KvEntry(value = value, metadata = KvEntryMetadata(createdAtEpochMillis = stored.createdAt, expiresAtEpochMillis = stored.expiresAt)))
    }

    override suspend fun delete(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> = Ok(entries.remove(compositeKey(namespace, key)) != null)

    override suspend fun exists(
        namespace: KvNamespaceId,
        key: String,
    ): IdkResult<Boolean, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stored = entries[compositeKey(namespace, key)] ?: return Ok(false)
        if (stored.expiresAt <= now) {
            entries.remove(compositeKey(namespace, key))
            return Ok(false)
        }
        return Ok(true)
    }

    override suspend fun touch(
        namespace: KvNamespaceId,
        key: String,
        ttl: Duration,
    ): IdkResult<Boolean, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stored = entries[compositeKey(namespace, key)] ?: return Ok(false)
        if (stored.expiresAt <= now) {
            entries.remove(compositeKey(namespace, key))
            return Ok(false)
        }
        val newExpiresAt = if (ttl.isInfinite()) Long.MAX_VALUE else now + ttl.inWholeMilliseconds
        entries[compositeKey(namespace, key)] = stored.copy(expiresAt = newExpiresAt)
        return Ok(true)
    }

    override suspend fun cleanupExpired(namespace: KvNamespaceId?): IdkResult<Int, IdkError> {
        val now = Clock.System.now().toEpochMilliseconds()
        val keysToRemove =
            entries
                .filter { (k, stored) ->
                    (namespace == null || k.startsWith("${namespace.name}:")) && stored.expiresAt <= now
                }.keys
                .toList()
        keysToRemove.forEach { entries.remove(it) }
        return Ok(keysToRemove.size)
    }
}

private class StoreTestKvStoreManager : KvStoreManager {
    private val store = StoreTestKvStore()

    override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore = store

    override fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore = store

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore> = setOf(store)
}

private class NoOpKvStoreService : KvStoreService {
    override fun getStoreIds(): Array<String> = emptyArray()

    override fun getStoreConfig(storeId: String): KvStoreConfigBase = throw UnsupportedOperationException("no store config in test")

    override fun getStore(storeId: String): KvStore = throw UnsupportedOperationException("no store in test")
}

private class StoreTestSessionExecution : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw UnsupportedOperationException("not used in store tests")
    override val sessionContext: SessionContext
        get() = throw UnsupportedOperationException("not used in store tests")
    override val log: SessionLogService
        get() = throw UnsupportedOperationException("not used in store tests")
    override val conf: ContextConfig
        get() = throw UnsupportedOperationException("not used in store tests")
}

private class StoreTestAppLogManager :
    AbstractLogManager(scope = IdkScope.APP, loggers = emptySet()),
    AppLogManager
