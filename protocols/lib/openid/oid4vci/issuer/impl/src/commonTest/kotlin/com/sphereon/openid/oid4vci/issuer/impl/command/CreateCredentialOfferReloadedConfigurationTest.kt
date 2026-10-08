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

import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.impl.lifecycle.OfferLifecycleInitializer
import com.sphereon.openid.oid4vci.issuer.impl.testAuthorizationSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The issuer reads its credential configurations from a snapshot that can lag a configuration
 * written a moment ago. An offer for such a configuration must succeed; an id the backing store
 * does not hold either must still be refused.
 */
class CreateCredentialOfferReloadedConfigurationTest {
    private val instanceId = "00000000-0000-4000-8000-000000000022"

    @Test
    fun offerForConfigurationWrittenAfterTheSnapshotIsCreated() =
        runTest {
            val provider = SnapshotIssuerConfigProvider(snapshot = setOf("EuPid"), backingStore = setOf("EuPid", "StatusBadge"))

            val result = command(provider).execute(args(listOf("StatusBadge")))

            assertTrue(result.isOk, "error=${if (result.isErr) result.error else null}")
            assertEquals(1, provider.reloads)
        }

    @Test
    fun offerForConfigurationUnknownToTheBackingStoreIsStillRejected() =
        runTest {
            val provider = SnapshotIssuerConfigProvider(snapshot = setOf("EuPid"), backingStore = setOf("EuPid"))

            val result = command(provider).execute(args(listOf("EuPid", "DoesNotExist")))

            assertTrue(result.isErr, "An id unknown after the reload must be refused")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("DoesNotExist"), result.error.message.defaultMessage)
            assertEquals(1, provider.reloads)
        }

    @Test
    fun offerForConfigurationInTheSnapshotDoesNotReload() =
        runTest {
            val provider = SnapshotIssuerConfigProvider(snapshot = setOf("EuPid"), backingStore = setOf("EuPid"))

            val result = command(provider).execute(args(listOf("EuPid")))

            assertTrue(result.isOk, "error=${if (result.isErr) result.error else null}")
            assertEquals(0, provider.reloads)
        }

    private fun command(provider: Oid4vciIssuerConfigProvider) =
        CreateCredentialOfferCommandImpl(
            execution = TestSessionExecution(),
            asBridge = NoOpAsBridge(),
            offerStore = NoOpOfferStore(),
            sessionStore = RecordingSessionStore(),
            issuerConfigProvider = provider,
            lifecycleInitializer = OfferLifecycleInitializer(),
        )

    private fun args(configurationIds: List<String>) =
        CreateCredentialOfferArgs(
            instanceId = instanceId,
            issuerId = "https://issuer.example.com/oid4vci",
            credentialConfigurationIds = configurationIds,
            preAuthorizedCodeGrant = true,
            authorizationPolicySnapshot = testAuthorizationSnapshot(instanceId),
        )

    private class SnapshotIssuerConfigProvider(
        snapshot: Set<String>,
        private val backingStore: Set<String>,
    ) : Oid4vciIssuerConfigProvider {
        private var published: Set<String> = snapshot
        var reloads = 0
            private set

        override val issuerIdentifier: String = "https://issuer.example.com"
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported>
            get() = published.associateWith { CredentialConfigurationSupported(format = "dc+sd-jwt") }
        override val authorizationServers: List<String>? = null
        override val display: List<DisplayProperties>? = null

        override suspend fun reloadConfiguration() {
            reloads++
            published = backingStore
        }
    }
}
