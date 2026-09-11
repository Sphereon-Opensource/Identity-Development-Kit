/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyConfig
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyResolver
import com.sphereon.openid.oid4vci.issuer.impl.lifecycle.OfferLifecycleInitializer
import com.sphereon.openid.oid4vci.issuer.impl.testAuthorizationSnapshot
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuanceLifecycleHook
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuancePhase
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciOfferLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciOfferLifecycleResult
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciPhaseLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciPhaseLifecycleResult
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionCallbackConfig
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CreateCredentialOfferCommandImplLifecycleTest {
    private val instanceId = "00000000-0000-4000-8000-000000000011"
    private val issuerId = "https://issuer.example.com/oid4vci"

    private class RecordingLifecycleHook(
        private val correlationId: String? = "corr-1",
        private val initializationError: IdkError? = null,
    ) : Oid4vciIssuanceLifecycleHook {
        var offerArgs: Oid4vciOfferLifecycleArgs? = null
        val phases = mutableListOf<Oid4vciPhaseLifecycleArgs>()

        override suspend fun initializeOffer(args: Oid4vciOfferLifecycleArgs): IdkResult<Oid4vciOfferLifecycleResult, IdkError> {
            offerArgs = args
            initializationError?.let { return Err(it) }
            return Ok(Oid4vciOfferLifecycleResult(correlationId = correlationId))
        }

        override suspend fun recordPhase(args: Oid4vciPhaseLifecycleArgs): IdkResult<Oid4vciPhaseLifecycleResult, IdkError> {
            phases += args
            return Ok(Oid4vciPhaseLifecycleResult())
        }
    }

    private class RecordingPolicyResolver : CredentialIssuancePolicyResolver {
        var resolveCalls = 0

        override suspend fun resolve(credentialConfigurationId: String): CredentialIssuancePolicyConfig {
            resolveCalls++
            return CredentialIssuancePolicyConfig()
        }
    }

    private fun sampleArgs() =
        CreateCredentialOfferArgs(
            instanceId = instanceId,
            issuerId = issuerId,
            credentialConfigurationIds = listOf("PID"),
            preAuthorizedCodeGrant = true,
            authorizationCodeGrant = false,
            preSeededAttributes = mapOf("given_name" to JsonPrimitive("Ada")),
            initialLifecycleFields = mapOf("employee_id" to JsonPrimitive("E1042")),
            callback = IssuanceSessionCallbackConfig(
                url = "https://operator.example/callback",
                statuses = listOf(IssuanceSessionStatus.DEFERRED, IssuanceSessionStatus.COMPLETED),
                includeIssuanceData = true,
            ),
            state = "opaque-workflow-state",
            authorizationPolicySnapshot = testAuthorizationSnapshot(instanceId),
        )

    @Test
    fun ordinaryOfferInitializationErrorPreventsSessionGrantAndOfferWrites() =
        runTest {
            val expectedError = IdkError.INVALID_STATE(message = "Configured ordinary pipeline could not initialize")
            val hook = RecordingLifecycleHook(initializationError = expectedError)
            val sessionStore = RecordingSessionStore()
            val asBridge = NoOpAsBridge()
            var offerWrites = 0
            val offerStore = object : CredentialOfferStore by NoOpOfferStore() {
                override suspend fun store(
                    offerId: String,
                    offer: CredentialOffer,
                    ttlSeconds: Long,
                    sessionId: String?,
                ): IdkResult<Unit, IdkError> {
                    offerWrites++
                    return Ok(Unit)
                }
            }
            val command = CreateCredentialOfferCommandImpl(
                execution = TestSessionExecution(),
                asBridge = asBridge,
                offerStore = offerStore,
                sessionStore = sessionStore,
                lifecycleInitializer = OfferLifecycleInitializer(lifecycleHook = hook),
            )

            val result = command.execute(sampleArgs())

            assertEquals(issuerId, hook.offerArgs?.issuerId)
            assertTrue(result.isErr, "An initialization Err must not become an offer without a pipeline")
            assertEquals(expectedError, result.error)
            assertTrue(sessionStore.created.isEmpty())
            assertNull(asBridge.lastRegisterPreAuthCodeArgs)
            assertEquals(0, offerWrites)
            assertTrue(hook.phases.isEmpty())
        }

    @Test
    fun offerCreationWithLifecycleHookStoresCorrelationAndSeedsInitialFields() =
        runTest {
            val hook = RecordingLifecycleHook(correlationId = "corr-1")
            val sessionStore = RecordingSessionStore()
            val asBridge = NoOpAsBridge()
            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = asBridge,
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    lifecycleInitializer = OfferLifecycleInitializer(lifecycleHook = hook),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            assertEquals(true, asBridge.lastRegisterPreAuthCodeArgs?.useCredentialIdentifiers)
            val storedSession = sessionStore.created.first()
            assertEquals(instanceId, storedSession.instanceId)
            assertEquals(storedSession.sessionId, storedSession.issuerState)
            val tokenCorrelation =
                resolveCredentialRequestCorrelation(
                    requestedIdentifier = null,
                    tokenIdentifiers = null,
                    tokenId = "pre-authorized-token-jti",
                    sessionStore = sessionStore,
                    tokenIssuerState = storedSession.sessionId,
                ).getOrThrow()
            assertEquals(storedSession.sessionId, tokenCorrelation.protocolSessionId)
            assertEquals(storedSession, tokenCorrelation.issuanceSession)
            assertEquals("corr-1", storedSession.lifecycleCorrelationId)
            assertEquals(JsonPrimitive("Ada"), hook.offerArgs?.initialFields?.get("given_name"))
            assertEquals(JsonPrimitive("E1042"), hook.offerArgs?.initialFields?.get("employee_id"))
            assertEquals(listOf(Oid4vciIssuancePhase.START, Oid4vciIssuancePhase.PRE_AUTHORIZED), hook.phases.map { it.phase })
            assertTrue(hook.phases.all { it.correlationId == "corr-1" })
            assertEquals("https://operator.example/callback", sessionStore.created.first().callback?.url)
            assertEquals("opaque-workflow-state", sessionStore.created.first().state)
        }

    @Test
    fun offerCreationWithoutLifecycleHookStoresNullCorrelation() =
        runTest {
            val sessionStore = RecordingSessionStore()
            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    lifecycleInitializer = OfferLifecycleInitializer(),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            assertNull(sessionStore.created.first().lifecycleCorrelationId)
        }

    @Test
    fun offerTtlIsConvertedOnceToAbsoluteExpiryForPreAuthorizedRegistration() =
        runTest {
            val sessionStore = RecordingSessionStore()
            val asBridge = NoOpAsBridge()
            val fixedNow = Instant.parse("2030-01-01T00:00:00Z")
            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = asBridge,
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    lifecycleInitializer = OfferLifecycleInitializer(),
                    clock = object : Clock {
                        override fun now(): Instant = fixedNow
                    },
                )

            val out = cmd.execute(sampleArgs().copy(offerTtlSeconds = 37))

            assertTrue(out.isOk)
            assertEquals(fixedNow.epochSeconds + 37L, asBridge.lastRegisterPreAuthCodeArgs?.expiresAtEpochSeconds)
            assertEquals(fixedNow.epochSeconds + 37L, sessionStore.created.first().expiresAt)
        }

    @Test
    fun offerTtlOverflowIsRejectedBeforeSessionPersistence() =
        runTest {
            val sessionStore = RecordingSessionStore()
            val policyResolver = RecordingPolicyResolver()
            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    lifecycleInitializer = OfferLifecycleInitializer(policyResolver = policyResolver),
                    clock = object : Clock {
                        override fun now(): Instant = Instant.fromEpochSeconds(1L)
                    },
                )

            val result = cmd.execute(sampleArgs().copy(offerTtlSeconds = Long.MAX_VALUE))

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertEquals(0, policyResolver.resolveCalls)
            assertTrue(sessionStore.created.isEmpty())
        }

    @Test
    fun offerTtlWithUnrepresentableAbsoluteExpiryIsRejectedBeforeSessionPersistence() =
        runTest {
            val sessionStore = RecordingSessionStore()
            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    lifecycleInitializer = OfferLifecycleInitializer(),
                    clock = object : Clock {
                        override fun now(): Instant = Instant.fromEpochSeconds(0L)
                    },
                )

            val result = cmd.execute(sampleArgs().copy(offerTtlSeconds = Long.MAX_VALUE))

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertTrue(sessionStore.created.isEmpty())
        }

    @Test
    fun crossIssuerAuthorizationSnapshotIsRejectedBeforeLifecycleOrPersistenceSideEffects() =
        runTest {
            val hook = RecordingLifecycleHook()
            val sessionStore = RecordingSessionStore()
            val cmd = CreateCredentialOfferCommandImpl(
                execution = TestSessionExecution(),
                asBridge = NoOpAsBridge(),
                offerStore = NoOpOfferStore(),
                sessionStore = sessionStore,
                lifecycleInitializer = OfferLifecycleInitializer(lifecycleHook = hook),
            )

            val result = cmd.execute(
                sampleArgs().copy(
                    authorizationPolicySnapshot = testAuthorizationSnapshot("00000000-0000-4000-8000-000000000099"),
                ),
            )

            assertTrue(result.isErr)
            assertNull(hook.offerArgs)
            assertTrue(sessionStore.created.isEmpty())
        }
}
