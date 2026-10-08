/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.provider

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyManagerServiceGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.did.methods.webvh.WebvhDidUrlBuilder
import com.sphereon.did.methods.webvh.command.CreateWebvhDidInput
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import com.sphereon.did.methods.webvh.resolver.WebvhDidResolverImpl
import com.sphereon.did.methods.webvh.resolver.WebvhLogReplayer
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Direct unit test of [WebvhDidResolverImpl.resolve] using a Ktor MockEngine
 * to serve a captured `did.jsonl` (built via the create command + Ed25519
 * KMS at setup time). Covers the HTTP-fetch + log-parse + replay-verify path
 * without depending on a network or any externally-hosted DID document.
 */
class WebvhDidResolverHttpMockTest {
    private lateinit var app: AppGraph
    private lateinit var registry: SessionScopedCommandRegistry
    private lateinit var keyManager: KeyManagerService
    private lateinit var replayer: WebvhLogReplayer

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
                "kms.providers.softwaretest.persistKeysDuringGeneration" to "true",
                "kms.providers.softwaretest.exposePrivateKeysDuringGeneration" to "true",
            ),
        )
        app = createWebvhProviderTestAppGraph(testInstance = this)
        app.userContextManager.destroyAll()
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("webvh-resolver-mock", principalType = com.sphereon.di.context.PrincipalType.USER)
        val sessionGraph = session.graph
        registry = (sessionGraph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
        keyManager = (sessionGraph as KeyManagerServiceGraph).keyManagerService
        replayer = (sessionGraph as WebvhLogReplayerGraph).webvhLogReplayer
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun resolveFetchesAndVerifiesDidJsonlOverHttp() =
        runTest {
            // 1. Build a real signed genesis entry via the create command.
            val alias = "resolver-mock-${kotlin.random.Random.nextLong()}"
            val keyPair =
                keyManager.generateKey(
                    providerId = "softwaretest",
                    alias = alias,
                    alg = SignatureAlgorithm.ED25519,
                )
            val rawPublic =
                keyPair.jose.publicJwk.x!!
                    .decodeFrom(Encoding.BASE64URL)
            val multikey =
                Multibase.encode(
                    byteArrayOf(MULTICODEC_ED25519_BYTE_0, MULTICODEC_ED25519_BYTE_1) + rawPublic,
                    MultibaseEncoding.BASE58BTC,
                )
            val createCmd = registry.get(CreateWebvhDidServiceCommand.COMMAND_ID) as CreateWebvhDidServiceCommand
            val genesis =
                createCmd
                    .execute(
                        CreateWebvhDidInput(
                            domain = "example.com",
                            updateKeyRefs = listOf(alias),
                            updateMultikeys = listOf(multikey),
                        ),
                    ).also { assertTrue(it.isOk) }
                    .value

            // 2. Build a MockEngine-backed HttpClientFactory that serves the JSONL at the resolver URL.
            val expectedLogUrl = WebvhDidUrlBuilder.toLogUrl(genesis.did).also { assertTrue(it.isOk) }.value
            val fakeFactory =
                MockHttpClientFactory(
                    handler = { request ->
                        when (request.url.toString()) {
                            expectedLogUrl -> {
                                respond(
                                    content = genesis.logJsonl,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", "application/jsonl"),
                                )
                            }

                            else -> {
                                respond(
                                    content = "Unexpected URL: ${request.url}",
                                    status = HttpStatusCode.NotFound,
                                )
                            }
                        }
                    },
                )

            // 3. Construct the resolver directly with the mock + the real replayer pulled from the session graph.
            val resolver = WebvhDidResolverImpl(httpClientFactory = fakeFactory, replayer = replayer)

            // 4. Resolve and assert.
            val resolveResult = resolver.resolve(did = genesis.did, options = DidResolutionOptions())
            assertTrue(
                resolveResult.isOk,
                "resolve must succeed; error: ${if (resolveResult.isErr) resolveResult.error.message else "<none>"}",
            )
            val resolution = resolveResult.value
            assertNotNull(resolution.didDocument, "resolution must produce a didDocument")
            assertEquals(genesis.did, resolution.didDocument!!.id, "resolved DID document id must match the requested DID")
            assertEquals(
                genesis.logEntry.versionId,
                resolution.didDocumentMetadata?.versionId,
                "resolution metadata versionId must match the genesis entry",
            )

            // Mock recorded exactly one call to the log URL.
            assertEquals(1, fakeFactory.requestCount, "exactly one HTTP fetch should have happened")
        }

    @Test
    fun resolveSurfacesHttp404AsErr() =
        runTest {
            val fakeFactory =
                MockHttpClientFactory(
                    handler = { _ ->
                        respond(content = "not found", status = HttpStatusCode.NotFound)
                    },
                )
            val resolver = WebvhDidResolverImpl(httpClientFactory = fakeFactory, replayer = replayer)
            val resolveResult = resolver.resolve(did = "did:webvh:QmDoesNotExist:example.com", options = DidResolutionOptions())
            assertTrue(resolveResult.isErr, "404 response must surface as Err; got Ok")
        }

    private companion object {
        const val MULTICODEC_ED25519_BYTE_0: Byte = 0xED.toByte()
        const val MULTICODEC_ED25519_BYTE_1: Byte = 0x01.toByte()
    }
}

/**
 * Minimal [HttpClientFactory] that wraps a Ktor [MockEngine] handler. Used
 * for testing the resolver's HTTP-fetch path without hitting the network.
 */
private class MockHttpClientFactory(
    private val handler: io.ktor.client.engine.mock.MockRequestHandler,
) : HttpClientFactory {
    var requestCount: Int = 0
        private set

    override fun createClient(options: HttpClientOptions): HttpClient =
        HttpClient(
            MockEngine { request ->
                requestCount++
                handler(request)
            },
        )

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(getEngineTypeDefault())

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
}
