/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.statuslist.impl

import com.sphereon.core.api.cache.BackendCapabilities
import com.sphereon.core.api.cache.CacheBackend
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.DefaultCacheManager
import com.sphereon.core.api.cache.DefaultCacheService
import com.sphereon.core.api.cache.MapCacheBackend
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.CryptoServices
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.UpdateEntryStatusArgs
import com.sphereon.statuslist.impl.driver.InMemoryStatusListDriver
import com.sphereon.statuslist.impl.driver.InMemoryStatusListStore
import com.sphereon.statuslist.impl.resolve.StatusListResolverImpl
import com.sphereon.statuslist.impl.sign.CwtStatusListSigner
import com.sphereon.statuslist.impl.sign.JwsStatusListSigner
import com.sphereon.statuslist.impl.sign.LocalStatusListJwsSigningService
import com.sphereon.statuslist.impl.sign.MdocCwtStatusListSigner
import com.sphereon.statuslist.spi.StatusListSigner
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.di.context.PrincipalType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import java.util.concurrent.atomic.AtomicInteger

/** Real signed status-list resolution tests for the tenant-scoped HTTP cache. */
class StatusListHttpCacheIntegrationTest {
    private val app = createJvmStatusListTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("status-http-cache", principalType = PrincipalType.USER)
    private lateinit var execution: SessionExecution
    private lateinit var jwtService: JwtService
    private lateinit var coseCrypto: com.sphereon.crypto.core.CoseCryptoService
    private lateinit var kms: KeyManagerService
    private lateinit var token: String
    private lateinit var cacheService: DefaultCacheService

    @BeforeTest
    fun setUp() = runBlocking {
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
                SoftwareKmsProviderConfig(id = "status-http-cache-provider"),
                session.asCoreApiServiceGraph().serviceExecution,
            )
        kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
        execution = (session.graph as SessionExecution.Graph).sessionExecution
        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
        coseCrypto = (session.graph as CryptoServices.Graph).cryptoServices.cose
        val manager = DefaultCacheManager().apply { registerBackend(MapCacheBackend()) }
        cacheService = DefaultCacheService(manager)

        val key = kms.generateKey(
            alias = "status-http-cache-signing",
            alg = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PRIVATE,
        )
        val alias = key.joseToManagedKeyInfo(KeyVisibility.PRIVATE).alias
            ?: fail("generated key has no alias")
        val driver = InMemoryStatusListDriver(InMemoryStatusListStore(), signer(), execution)
        val uri = "https://issuer.example/statuslists/cache-main"
        driver.createStatusList(
            CreateStatusListArgs(
                correlationId = "cache-main",
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                purposes = listOf(StatusPurpose.REVOCATION),
                proofFormat = StatusProofFormat.JWT,
                issuer = "https://issuer.example",
                statusListUri = uri,
                length = 256,
                bitsPerStatus = 1,
                signingKeyAlias = alias,
                signingKeyMode = "jwk",
            ),
        ).getOrElse { fail("create status list: $it") }
        driver.allocateEntry(
            AllocateEntryArgs(StatusListRef(correlationId = "cache-main"), explicitIndex = 1, credentialId = "cache-invalid"),
        ).getOrElse { fail("allocate status entry: $it") }
        driver.updateEntryStatus(
            UpdateEntryStatusArgs(EntryRef(correlationId = "cache-main", credentialId = "cache-invalid"), StatusValues.INVALID),
        ).getOrElse { fail("revoke status entry: $it") }
        token = driver.getStatusListToken(StatusListRef(correlationId = "cache-main"))
            .getOrElse { fail("publish status list: $it") }?.token ?: fail("missing status-list token")
    }

    @Test
    fun validatedSequentialReusePerformsOneGetAndRevalidatesSignature() = runBlocking {
        val gets = AtomicInteger()
        val uri = "https://issuer.example/statuslists/cache-sequential"
        val resolver = resolver(uri, gets)
        assertTrue(resolver.resolveStatus(args(uri)).isOk)
        assertTrue(resolver.resolveStatus(args(uri)).isOk)
        assertEquals(1, gets.get(), "the second resolve should use the validated cache envelope")
    }

    @Test
    fun malformedCachedEnvelopeIsEvictedAndRefetched() = runBlocking {
        val gets = AtomicInteger()
        val uri = "https://issuer.example/statuslists/cache-malformed"
        val resolver = resolver(uri, gets)
        assertTrue(resolver.resolveStatus(args(uri)).isOk)
        val cache = cacheService.getCache(CacheRequirements.localOnly("statuslist.resolver.http"))
        val key = "$uri|expected-spec=token_status_list|expected-format=jwt"
        val raw = cache.getTenant(execution.tenantId, key) ?: fail("expected cache envelope")
        cache.putTenant(execution.tenantId, key, raw.replace(token.encodeToByteArray().encodeToBase64(), "not-a-signed-token"), null)
        assertTrue(resolver.resolveStatus(args(uri)).isOk)
        assertEquals(2, gets.get(), "malformed cached data must never bypass crypto")
    }

    @Test
    fun expectedFormatAndTenantAreCacheIsolated() = runBlocking {
        val gets = AtomicInteger()
        val uri = "https://issuer.example/statuslists/cache-isolation"
        val resolver = resolver(uri, gets)
        assertTrue(resolver.resolveStatus(args(uri)).isOk)
        assertTrue(resolver.resolveStatus(args(uri, expectedFormat = StatusProofFormat.CWT)).isErr)
        val otherTenant = object : SessionExecution by execution { override val tenantId: String = "other-tenant" }
        val otherResolver = resolver(uri, gets, otherTenant)
        assertTrue(otherResolver.resolveStatus(args(uri)).isOk)
        assertEquals(3, gets.get(), "format and tenant scopes must not alias")
    }

    @Test
    fun concurrentMissesShareOneValidatedFetch() = runBlocking {
        val gets = AtomicInteger()
        val uri = "https://issuer.example/statuslists/cache-concurrent"
        val resolver = resolver(uri, gets)
        val results = (0 until 8).map { index -> async { resolver.resolveStatus(args(uri, index = index % 2)) } }.awaitAll()
        assertTrue(results.all { it.isOk })
        assertEquals(StatusValues.VALID, results[0].getOrElse { fail("valid result missing: $it") }.value)
        assertEquals(StatusValues.INVALID, results[1].getOrElse { fail("invalid result missing: $it") }.value)
        assertEquals(1, gets.get(), "concurrent misses must use one per-tenant/key flight")
    }

    @Test
    fun noStoreResponseIsNotReusedAndUnavailableRefetchFailsClosed() = runBlocking {
        val gets = AtomicInteger()
        val uri = "https://issuer.example/statuslists/cache-no-store"
        var available = true
        val resolver = resolver(uri, gets, execution, { available }, "no-store")
        assertTrue(resolver.resolveStatus(args(uri)).isOk)
        available = false
        assertTrue(resolver.resolveStatus(args(uri)).isErr)
        assertEquals(2, gets.get())
    }

    @Test
    fun cancelledOwnerAllowsAnActiveWaiterToRetry() = runBlocking {
        val gets = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val uri = "https://issuer.example/statuslists/cache-owner-cancel"
        val resolver = resolver(uri, gets, requestStarted = started, requestGate = release)
        val owner = async(start = CoroutineStart.UNDISPATCHED) { resolver.resolveStatus(args(uri)) }
        try {
            started.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { resolver.resolveStatus(args(uri)) }
            try {
                // The owner has claimed the flight and is gated in HTTP. Starting this waiter
                // undispatched reaches the shared await immediately; active state proves it did
                // not become a second owner or merely queue behind an unrelated suspension.
                assertTrue(waiter.isActive, "the waiter must be suspended in the shared flight")
                assertEquals(1, gets.get(), "the waiter must join the existing HTTP flight")

                owner.cancel()
                release.complete(Unit)
                owner.cancelAndJoin()

                val result = waiter.await()
                assertTrue(result.isOk, "an active waiter must retry after its owner is cancelled")
                assertEquals(2, gets.get(), "the waiter must be able to take ownership after cancellation")
            } finally {
                waiter.cancel()
            }
        } finally {
            release.complete(Unit)
            owner.cancelAndJoin()
        }
    }

    @Test
    fun cancelledOwnerDuringCachePublicationAllowsAnActiveWaiterToRetry() = runBlocking {
        val gets = AtomicInteger()
        val cacheSetCalls = AtomicInteger()
        val cacheSetEntered = CompletableDeferred<Unit>()
        val releaseCacheSet = CompletableDeferred<Unit>()
        val uri = "https://issuer.example/statuslists/cache-owner-cache-cancel"
        val blockedCacheService =
            DefaultCacheService(
                DefaultCacheManager().apply {
                    registerBackend(BlockingCacheBackend(cacheSetCalls, cacheSetEntered, releaseCacheSet))
                },
            )
        val resolver = resolver(uri, gets, cacheServiceOverride = blockedCacheService)
        val owner = async(start = CoroutineStart.UNDISPATCHED) { resolver.resolveStatus(args(uri)) }
        try {
            cacheSetEntered.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { resolver.resolveStatus(args(uri)) }
            try {
                assertTrue(waiter.isActive, "the waiter must be suspended in the shared flight")
                assertEquals(1, cacheSetCalls.get(), "the waiter must not publish the owner's cache artifact")
                // Keep the owner suspended in cancellable cache publication. Releasing this gate
                // before cancellation would allow the owner to publish an artifact and turn the
                // test into a timing race instead of proving Retry-before-publication.
                owner.cancel()
                owner.cancelAndJoin()

                val result = waiter.await()
                assertTrue(result.isOk, "an active waiter must retry after cache publication cancellation")
                assertEquals(2, gets.get(), "the waiter must fetch after taking ownership")
            } finally {
                waiter.cancel()
            }
        } finally {
            releaseCacheSet.complete(Unit)
            owner.cancelAndJoin()
        }
    }

    @Test
    fun oversizedUriFailsClosedBeforeAnyHttpRequest() = runBlocking {
        val gets = AtomicInteger()
        val resolver = resolver("https://issuer.example/statuslists/cache-bounded", gets)
        val oversized = "https://issuer.example/statuslists/" + "x".repeat(5000)

        val result = resolver.resolveStatus(args(oversized))

        assertTrue(result.isErr, "an untrusted URI above the configured bound must be rejected")
        assertEquals(0, gets.get(), "bounded input must fail before reaching HTTP")
    }

    @Test
    fun httpFetchTimeoutFailsClosed() = runTest {
        val gets = AtomicInteger()
        val uri = "https://issuer.example/statuslists/cache-timeout"
        val resolver = resolver(uri, gets, requestDelayMillis = 31_000)

        val result = resolver.resolveStatus(args(uri))

        assertTrue(result.isErr, "an HTTP fetch beyond the resolver deadline must fail closed")
        assertTrue(
            (result as com.sphereon.core.api.Err).error.message.defaultMessage.contains("timed out"),
            "the timeout must remain a resolution failure",
        )
    }

    @Test
    fun activeFlightLimitRejectsExcessDifferentKeys() = runBlocking {
        val gets = AtomicInteger()
        val first64Started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val resolver = resolver(
            "https://issuer.example/statuslists/cache-flight-limit",
            gets,
            requestGate = release,
            requestStarted = first64Started,
            requestStartedTarget = 64,
        )
        val first64 =
            (0 until 64).map { index ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    resolver.resolveStatus(args("https://issuer.example/statuslists/cache-flight-$index"))
                }
            }
        first64Started.await()
        assertEquals(64, gets.get(), "the first 64 distinct owners must be gated before release")
        val excess =
            (64 until 80).map { index ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    resolver.resolveStatus(args("https://issuer.example/statuslists/cache-flight-$index"))
                }
            }
        release.complete(Unit)
        val results = (first64 + excess).awaitAll()

        assertTrue(
            results.any { result ->
                result.isErr && (result as com.sphereon.core.api.Err).error.message.defaultMessage.contains("active status-list fetch limit")
            },
            "excess distinct keys must be rejected while the active-flight bound is occupied",
        )
        assertTrue(gets.get() <= 64, "the HTTP boundary must not exceed the active-flight bound")
    }

    private fun args(
        uri: String,
        expectedFormat: StatusProofFormat = StatusProofFormat.JWT,
        index: Int = 0,
    ) = ResolveStatusArgs(uri = uri, index = index, expectedSpec = StatusListSpec.TOKEN_STATUS_LIST, expectedFormat = expectedFormat)

    private fun resolver(
        uri: String,
        gets: AtomicInteger,
        sessionExecution: SessionExecution = execution,
        available: () -> Boolean = { true },
        cacheControl: String = "max-age=300",
        requestStarted: CompletableDeferred<Unit>? = null,
        requestStartedTarget: Int = 1,
        requestGate: CompletableDeferred<Unit>? = null,
        requestDelayMillis: Long = 0,
        cacheServiceOverride: DefaultCacheService? = null,
    ): StatusListResolverImpl =
        StatusListResolverImpl(
            httpClientFactory = TestHttpFactory(token, gets, available, cacheControl, requestStarted, requestStartedTarget, requestGate, requestDelayMillis),
            jwtService = jwtService,
            coseSign1Codec = CoseSign1CborCodecImpl(),
            coseCryptoService = coseCrypto,
            x509VerifyService = com.sphereon.crypto.core.x509.X509VerifyServiceImpl(),
            cacheService = cacheServiceOverride ?: cacheService,
            execution = sessionExecution,
        )

    private fun signer(): StatusListSigner =
        JwsStatusListSigner(
            LocalStatusListJwsSigningService(jwtService, kms),
            NoopDidProviderRegistry,
            NoopDidResolverRegistry,
            CwtStatusListSigner(coseCrypto, CoseSign1CborCodecImpl(), kms, NoopDidProviderRegistry),
            MdocCwtStatusListSigner(coseCrypto, CoseSign1CborCodecImpl(), kms),
        )

    private class TestHttpFactory(
        private val token: String,
        private val gets: AtomicInteger,
        private val available: () -> Boolean,
        private val cacheControl: String,
        private val requestStarted: CompletableDeferred<Unit>?,
        private val requestStartedTarget: Int,
        private val requestGate: CompletableDeferred<Unit>?,
        private val requestDelayMillis: Long,
    ) : HttpClientFactory {
        override fun createClient(options: HttpClientOptions): HttpClient =
            HttpClient(MockEngine { request ->
                if (gets.incrementAndGet() == requestStartedTarget) requestStarted?.complete(Unit)
                requestGate?.await()
                if (requestDelayMillis > 0) kotlinx.coroutines.delay(requestDelayMillis)
                if (!available()) {
                    respond("unavailable", io.ktor.http.HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(
                        token,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.parse(StatusListContentTypes.STATUSLIST_JWT).toString()),
                            HttpHeaders.CacheControl to listOf(cacheControl),
                        ),
                    )
                }
            })

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()
        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private class BlockingCacheBackend(
        private val setCalls: AtomicInteger,
        private val setEntered: CompletableDeferred<Unit>,
        private val releaseSet: CompletableDeferred<Unit>,
    ) : CacheBackend {
        private val delegate = MapCacheBackend()

        override val id: String = "blocking-map"
        override val capabilities: BackendCapabilities = BackendCapabilities.IN_MEMORY

        override suspend fun get(key: String): ByteArray? = delegate.get(key)

        override suspend fun set(key: String, value: ByteArray, ttlMs: Long?) {
            if (setCalls.incrementAndGet() == 1) {
                setEntered.complete(Unit)
                releaseSet.await()
            }
            delegate.set(key, value, ttlMs)
        }

        override suspend fun delete(key: String): Boolean = delegate.delete(key)

        override suspend fun exists(key: String): Boolean = delegate.exists(key)

        override suspend fun getMany(keys: Collection<String>): Map<String, ByteArray> = delegate.getMany(keys)

        override suspend fun setMany(entries: Map<String, ByteArray>, ttlMs: Long?) = delegate.setMany(entries, ttlMs)

        override suspend fun deleteByPattern(pattern: String): Int = delegate.deleteByPattern(pattern)

        override suspend fun keys(pattern: String): List<String> = delegate.keys(pattern)

        override suspend fun clear() = delegate.clear()

        override suspend fun size(): Long = delegate.size()

        override suspend fun isHealthy(): Boolean = delegate.isHealthy()
    }

}
