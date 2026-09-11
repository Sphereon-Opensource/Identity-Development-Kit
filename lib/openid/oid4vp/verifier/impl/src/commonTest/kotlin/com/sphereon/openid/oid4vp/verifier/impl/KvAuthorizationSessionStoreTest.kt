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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
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
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import com.sphereon.data.store.kv.KvVersionedEntry
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.store.StoredEntry
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.dcql.store.model.DcqlQueryConfiguration
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionCallbackDispatcher
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionStatusUpdate
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Regression coverage for [KvAuthorizationSessionStore]: unlike [TestAuthorizationSessionStore]
 * (an in-memory map that stores the [AuthorizationSession] object BY REFERENCE and therefore
 * cannot catch a missing field in the persistence entry's mapping), this drives the REAL store
 * through its internal [KvAuthorizationSessionStore.AuthorizationSessionEntry] DTO — a genuine
 * encode/decode round trip via the same [com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec]
 * production code uses. It exists specifically to catch the class of bug where a new
 * [AuthorizationSession] field is added but never threaded through the entry's constructor,
 * `toPublic()`, or `put()` mapping.
 */
class KvAuthorizationSessionStoreTest {
    @Test
    fun `server verification evidence survives persistence without promotion of skipped checks`() = runTest {
        val store = createStore()
        val evidence = com.sphereon.openid.oid4vp.verifier.VerifiedCredentialEvidence(
            presentationSha256 = "actual-digest",
            temporalFacts = com.sphereon.openid.oid4vp.verifier.VerifiedCredentialTemporalFacts(1000, 2000, 3000),
            verifiedAtEpochMillis = 123L,
            issuer = com.sphereon.openid.oid4vp.verifier.CredentialIssuerRef(issuer = "did:example:issuer"),
            trust = com.sphereon.openid.oid4vp.verifier.CredentialTrustValidation(
                enabled = true, trusted = false,
                mode = com.sphereon.openid.oid4vp.verifier.CredentialTrustValidationMode.AUDIT,
            ),
            status = com.sphereon.openid.oid4vp.verifier.VerifiedCredentialStatus(
                com.sphereon.openid.oid4vp.verifier.VerifiedCredentialStatusOutcome.SKIPPED,
                required = false, rejectOnUnresolvable = true,
            ),
        )
        val correlation = "verification-evidence-roundtrip"
        assertEquals(true, store.put(correlation, session(null, correlation), 600).isOk)
        val matched = com.sphereon.openid.oid4vp.verifier.MatchedCredential(
            credentialQueryId = "identity_credential",
            credentialFormat = com.sphereon.openid.oid4vc.common.CredentialFormat.SD_JWT_VC,
            presentation = "verified-presentation",
            verificationEvidence = evidence,
        )
        assertEquals(true, store.storeValidationResult(correlation,
            com.sphereon.openid.oid4vp.verifier.ValidationResult(true, listOf(matched))).isOk)
        assertEquals(evidence, store.get(correlation).value?.validationResult?.matchedCredentials?.single()?.verificationEvidence)
    }

    private fun createStore(clock: Clock = Clock.System, claimed: Boolean = false): KvAuthorizationSessionStore =
        KvAuthorizationSessionStore(
            kvStoreManager = KvStoreTestKvStoreManager(clock),
            kvStoreService = KvStoreTestNoOpKvStoreService(claimed),
            dcqlQueryConfigurationStore = KvStoreTestNoOpDcqlQueryConfigurationStore(),
            callbackDispatcher = KvStoreTestNoOpCallbackDispatcher(),
            appLogManager = KvStoreTestAppLogManager(),
            execution = KvStoreTestSessionExecution(),
            clock = clock,
        )

    private fun session(
        templateId: String?,
        correlationId: String,
    ): AuthorizationSession {
        val now = Clock.System.now().toEpochMilliseconds()
        return AuthorizationSession(
            instanceId = "verifier-instance-kv-store-templateid",
            sessionId = "kv-store-session-$correlationId",
            correlationId = correlationId,
            dcqlQuery = DcqlQuery(credentials = listOf(DcqlCredentialQuery(id = "identity_credential", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:identity")))),
            verifierId = "verifier-a",
            templateId = templateId,
            authorizationRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example.com",
                    redirectUri = "https://verifier.example.com/callback",
                    state = correlationId,
                ),
            status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 600_000,
        )
    }

    @Test
    fun `claimed creation replays original and rejects conflicts and terminal overwrite`() = runTest {
        val store = createStore(claimed = true)
        val key = "oid4vp-claim:atomic-operation"
        val proposal = session("template-a", key)
        val first = store.createClaimedSession(proposal, "fingerprint", 600).getOrElse { error(it.toString()) }
        val replay = store.createClaimedSession(proposal.copy(sessionId = "losing-concurrent-session"), "fingerprint", 600)
        assertEquals(first, replay.value)
        assertIs<Err<*>>(store.createClaimedSession(proposal, "changed-fingerprint", 600))
        assertIs<Err<*>>(store.createClaimedSession(proposal.copy(templateId = "another-template"), "fingerprint", 600))
        val response = com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse(
            vpToken = com.sphereon.openid.oid4vp.common.vpTokenOf("identity_credential", "presentation"),
            rawVpToken = "{\"identity_credential\":[\"presentation\"]}", state = key,
        )
        assertEquals(true, store.storeResponse(key, response).isOk)
        assertIs<Err<*>>(store.storeResponse(key, response.copy(rawVpToken = "changed")))
        val validated = store.storeValidationResult(key, com.sphereon.openid.oid4vp.verifier.ValidationResult(true))
        assertEquals(true, validated.isOk)
        assertIs<Err<*>>(store.put(key, first, 600))
        assertIs<Err<*>>(store.storeValidationResult(key, com.sphereon.openid.oid4vp.verifier.ValidationResult(false, errors = listOf("changed"))))
        assertEquals(true, store.get(key).value?.validationResult?.valid)
        assertIs<Err<*>>(store.touch(key, 1200))
        assertIs<Err<*>>(store.delete(key))
        assertEquals(first.authorizationRequest, store.createClaimedSession(proposal, "fingerprint", 600).value.authorizationRequest)
    }

    @Test
    fun `claimed creation refuses implicit memory and ordinary put cannot reserve operation`() = runTest {
        val key = "oid4vp-claim:no-fallback"
        assertIs<Err<*>>(createStore().createClaimedSession(session(null, key), "fingerprint", 600))
        assertIs<Err<*>>(createStore(claimed = true).put(key, session(null, key), 600))
    }

    @Test
    fun `templateId survives a real put then get round trip`() =
        runTest {
            val store = createStore()
            val correlationId = "kv-store-round-trip-with-template"

            assertEquals(true, store.put(correlationId, session(templateId = "template-a", correlationId = correlationId), ttlSeconds = 600).isOk)

            val fetched = store.get(correlationId)
            assertEquals(true, fetched.isOk)
            assertEquals("template-a", fetched.value?.templateId)
        }

    @Test
    fun `templateId survives a real put then getByCorrelationId round trip`() =
        runTest {
            val store = createStore()
            val correlationId = "kv-store-round-trip-by-correlation-id"

            assertEquals(true, store.put(correlationId, session(templateId = "template-b", correlationId = correlationId), ttlSeconds = 600).isOk)

            val fetched = store.getByCorrelationId(correlationId)
            assertEquals(true, fetched.isOk)
            assertEquals("template-b", fetched.value?.templateId)
        }

    /**
     * A stored authorization session must not be able to name key material. The verifier resolves
     * its response-encryption key from the session's verifier instance plus its own server-side
     * binding, so an entry that carried an alias, a provider id, or any other key locator would put
     * that selection back within reach of whatever can write the session store.
     */
    @Test
    fun `the persisted session entry carries no key selector`() {
        val descriptor = KvAuthorizationSessionStore.AuthorizationSessionEntry.serializer().descriptor
        val fields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

        val selectors = fields.filter { field -> KEY_SELECTOR_MARKERS.any { field.contains(it, ignoreCase = true) } }
        assertEquals(emptyList(), selectors, "a persisted session must not carry a key selector")
    }

    @Test
    fun `absent templateId round trips as null through the real store`() =
        runTest {
            val store = createStore()
            val correlationId = "kv-store-round-trip-no-template"

            assertEquals(true, store.put(correlationId, session(templateId = null, correlationId = correlationId), ttlSeconds = 600).isOk)

            val fetched = store.get(correlationId)
            assertEquals(true, fetched.isOk)
            assertNull(fetched.value?.templateId)
        }

    @Test
    fun `real store keeps caller ttl aligned across session and kv metadata`() =
        runTest {
            val now = Instant.parse("2026-08-24T12:00:00Z")
            val clock = FixedClock(now)
            val store = createStore(clock)
            val correlationId = "kv-store-caller-ttl"
            val value =
                session(templateId = null, correlationId = correlationId).copy(
                    createdAt = now.toEpochMilliseconds(),
                    updatedAt = now.toEpochMilliseconds(),
                    expiresAt = 0L,
                )

            val metadata = store.put(correlationId, value, ttlSeconds = 42)
            val storedMetadata = metadata.getOrNull()
            requireNotNull(storedMetadata)
            assertEquals(now.toEpochMilliseconds() + 42_000L, storedMetadata.expiresAt)
            val stored = store.getEntry(correlationId).getOrNull()
            requireNotNull(stored)
            assertEquals(now.toEpochMilliseconds() + 42_000L, stored.expiresAt)
            assertEquals(stored.expiresAt, stored.value.expiresAt)
        }

    @Test
    fun `real store rejects invalid ttl values`() =
        runTest {
            val store = createStore(FixedClock(Instant.parse("2026-08-24T12:00:00Z")))
            listOf(0L, -1L, Long.MAX_VALUE).forEachIndexed { index, ttlSeconds ->
                val result = store.put("invalid-ttl-$index", session(null, "invalid-ttl-$index"), ttlSeconds)
                assertEquals("ILLEGAL_ARGUMENT_ERROR", assertIs<Err<IdkError>>(result).error.code)
            }
        }

    private companion object {
        /** Anything that could name key material, however it is spelled. */
        val KEY_SELECTOR_MARKERS = listOf("alias", "providerId", "keyId", "kid", "locator", "jarm")
    }
}

// ---------------------------------------------------------------------------
// Minimal, byte-encoding-faithful test support (mirrors ResponseCodeProtectionTest /
// KvCredentialOfferSessionStoreTest in sibling modules: a real in-memory KvStore that
// round-trips through namespace.codec.encode/decode, not a pass-through-by-reference fake).
// ---------------------------------------------------------------------------

private class KvStoreTestKvStore(
    private val clock: Clock,
) : KvStoreVersioning {
    override val config: KvStoreConfigBase =
        InMemoryKvStoreConfig(id = "test-auth-sessions", scopeBinding = KvStoreScopeBinding.TENANT)

    private data class Stored(
        val bytes: ByteArray,
        val createdAt: Long,
        val expiresAt: Long,
    )

    private val entries = mutableMapOf<String, Stored>()
    private val versions = mutableMapOf<String, MutableList<Pair<String, Stored>>>()
    private val versionMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun <V : Any> getHead(namespace: KvNamespace<V>, key: String): IdkResult<KvVersionedEntry<V>?, IdkError> {
        val chain = versions[compositeKey(namespace, key)] ?: return Ok(null)
        val (id, stored) = chain.last()
        return Ok(KvVersionedEntry(id, chain.dropLast(1).lastOrNull()?.first, namespace.codec.decode(stored.bytes), KvEntryMetadata(stored.createdAt, stored.expiresAt)))
    }

    override suspend fun <V : Any> getVersion(namespace: KvNamespace<V>, key: String, versionId: String): IdkResult<KvVersionedEntry<V>?, IdkError> {
        val chain = versions[compositeKey(namespace, key)] ?: return Ok(null)
        val index = chain.indexOfFirst { it.first == versionId }
        if (index < 0) return Ok(null)
        val (id, stored) = chain[index]
        return Ok(KvVersionedEntry(id, chain.getOrNull(index - 1)?.first, namespace.codec.decode(stored.bytes), KvEntryMetadata(stored.createdAt, stored.expiresAt)))
    }

    override suspend fun <V : Any> append(namespace: KvNamespace<V>, key: String, expectedPreviousVersionId: String?, value: V, ttl: Duration): IdkResult<KvVersionAppendResult<V>, IdkError> {
        versionMutex.lock()
        try {
            val head = getHead(namespace, key).value
            if (head?.versionId != expectedPreviousVersionId) return Ok(KvVersionAppendResult.Conflict(head))
            val chain = versions.getOrPut(compositeKey(namespace, key)) { mutableListOf() }
            val now = clock.now().toEpochMilliseconds()
            chain += (chain.size + 1).toString() to Stored(namespace.codec.encode(value), now, now + ttl.inWholeMilliseconds)
            return Ok(KvVersionAppendResult.Applied(getHead(namespace, key).value!!))
        } finally { versionMutex.unlock() }
    }

    override suspend fun deleteVersioned(namespace: KvNamespaceId, key: String): IdkResult<Boolean, IdkError> = Ok(versions.remove(compositeKey(namespace, key)) != null)

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
        val now = clock.now().toEpochMilliseconds()
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
        val now = clock.now().toEpochMilliseconds()
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
        val now = clock.now().toEpochMilliseconds()
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
        val now = clock.now().toEpochMilliseconds()
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

private class KvStoreTestKvStoreManager(
    clock: Clock,
) : KvStoreManager {
    private val store = KvStoreTestKvStore(clock)

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

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class KvStoreTestNoOpKvStoreService(private val claimed: Boolean = false) : KvStoreService {
    override fun getStoreIds(): Array<String> = emptyArray()

    override fun getStoreConfig(storeId: String): KvStoreConfigBase = if (claimed)
        com.sphereon.data.store.kv.KvStoreConfig(storeId, backendId = "test-atomic-persistence")
        else throw UnsupportedOperationException("no store config in test")

    override fun getStore(storeId: String): KvStore = throw UnsupportedOperationException("no store in test")
}

private class KvStoreTestSessionExecution : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw UnsupportedOperationException("not used in store tests")
    override val sessionContext: SessionContext
        get() = throw UnsupportedOperationException("not used in store tests")
    override val log: SessionLogService
        get() = throw UnsupportedOperationException("not used in store tests")
    override val conf: ContextConfig
        get() = throw UnsupportedOperationException("not used in store tests")
}

private class KvStoreTestAppLogManager :
    AbstractLogManager(scope = IdkScope.APP, loggers = emptySet()),
    AppLogManager

private class KvStoreTestNoOpCallbackDispatcher : AuthorizationSessionCallbackDispatcher {
    override suspend fun dispatch(
        url: String,
        update: AuthorizationSessionStatusUpdate,
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}

/**
 * Never exercised by put/get/getByCorrelationId (only [KvAuthorizationSessionStore.createSession]
 * reads it, which this test does not call) — every member throws to make that explicit.
 */
private class KvStoreTestNoOpDcqlQueryConfigurationStore : DcqlQueryConfigurationStore {
    private fun notUsed(): Nothing = throw UnsupportedOperationException("not used in store tests")

    override suspend fun getByQueryId(queryId: String): IdkResult<DcqlQueryConfiguration?, IdkError> = notUsed()

    override suspend fun put(
        key: String,
        value: DcqlQueryConfiguration,
        ttlSeconds: Long,
    ): IdkResult<com.sphereon.openid.oid4vp.common.store.StoreMetadata, IdkError> = notUsed()

    override suspend fun get(key: String): IdkResult<DcqlQueryConfiguration?, IdkError> = notUsed()

    override suspend fun getEntry(key: String): IdkResult<StoredEntry<DcqlQueryConfiguration>?, IdkError> = notUsed()

    override suspend fun delete(key: String): IdkResult<Boolean, IdkError> = notUsed()

    override suspend fun exists(key: String): IdkResult<Boolean, IdkError> = notUsed()

    override suspend fun touch(
        key: String,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError> = notUsed()

    override suspend fun cleanupExpired(): IdkResult<Int, IdkError> = notUsed()

    override suspend fun putPersistent(
        key: String,
        value: DcqlQueryConfiguration,
    ): IdkResult<com.sphereon.openid.oid4vp.common.store.StoreMetadata, IdkError> = notUsed()

    override suspend fun listIds(): IdkResult<List<String>, IdkError> = notUsed()

    override suspend fun getAll(): IdkResult<Map<String, DcqlQueryConfiguration>, IdkError> = notUsed()
}
