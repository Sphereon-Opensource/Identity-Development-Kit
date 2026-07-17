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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.impl.lifecycle.OfferLifecycleInitializer
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

class CreateCredentialOfferCommandImplLifecycleTest {
    private val instanceId = "issuer-instance-offer-lifecycle"
    private val issuerId = "https://issuer.example.com/oid4vci"

    private class RecordingLifecycleHook(
        private val correlationId: String? = "corr-1",
    ) : Oid4vciIssuanceLifecycleHook {
        var offerArgs: Oid4vciOfferLifecycleArgs? = null
        val phases = mutableListOf<Oid4vciPhaseLifecycleArgs>()

        override suspend fun initializeOffer(args: Oid4vciOfferLifecycleArgs): IdkResult<Oid4vciOfferLifecycleResult, IdkError> {
            offerArgs = args
            return Ok(Oid4vciOfferLifecycleResult(correlationId = correlationId))
        }

        override suspend fun recordPhase(args: Oid4vciPhaseLifecycleArgs): IdkResult<Oid4vciPhaseLifecycleResult, IdkError> {
            phases += args
            return Ok(Oid4vciPhaseLifecycleResult())
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
        )

    @Test
    fun offerCreationWithLifecycleHookStoresCorrelationAndSeedsInitialFields() =
        runTest {
            val hook = RecordingLifecycleHook(correlationId = "corr-1")
            val sessionStore = RecordingSessionStore()
            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    lifecycleInitializer = OfferLifecycleInitializer(lifecycleHook = hook),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            assertEquals(instanceId, sessionStore.created.first().instanceId)
            assertEquals("corr-1", sessionStore.created.first().lifecycleCorrelationId)
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
}
