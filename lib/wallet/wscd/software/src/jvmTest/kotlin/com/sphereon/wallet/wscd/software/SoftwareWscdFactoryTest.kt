/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.wallet.wscd.testfixtures.createWalletAppGraph
import com.sphereon.wallet.wscd.SoftwareWscdKeyStoreConfiguration
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wscd.WscdConfig
import com.sphereon.wallet.wscd.WscdKeySpec
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real-KMS-graph tests for [SoftwareWscdFactory]: verifies it produces a working [SoftwareWscd]
 * whose software KMS provider registers lazily. Registration is owned by
 * [SoftwareKmsProviderRegistrar], not by this factory - `create()` never registers anything
 * itself.
 */
class SoftwareWscdFactoryTest {
    @Test
    fun supportsOnlyWscdConfigSoftware() =
        runTest {
            val factory = newFactory()

            assertTrue(factory.supports(WscdConfig.Software()))
            assertFalse(factory.supports(WscdConfig.LocalNative()))
            assertFalse(factory.supports(WscdConfig.Remote(endpoint = "https://example.com")))
        }

    @Test
    fun createReturnsAWorkingWscdThatLazilyRegistersItsSoftwareProvider() =
        runTest {
            val factory = newFactory()

            val result = factory.create(WscdConfig.Software())
            assertTrue(result.isOk, "create failed: ${if (result.isErr) result.error else ""}")
            assertEquals(WscdProfile.Software, result.value.profile)

            // create() itself must not register anything: registration only happens on the first
            // key operation (SoftwareKmsProviderRegistrar.ensureRegistered).
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-factory-test",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val generated = result.value.generateKey(spec)
            assertTrue(generated.isOk, "generateKey failed: ${if (generated.isErr) generated.error else ""}")

            // A second create() call yields a fresh Wscd instance (per this factory's contract);
            // its first key operation must not fail or double-register the session's
            // already-registered provider.
            val second = factory.create(WscdConfig.Software())
            assertTrue(second.isOk, "second create failed: ${if (second.isErr) second.error else ""}")
            val secondGenerated = second.value.generateKey(spec.copy(walletUnitId = "wallet-factory-test-2"))
            assertTrue(secondGenerated.isOk, "second generateKey failed: ${if (secondGenerated.isErr) secondGenerated.error else ""}")
        }

    @Test
    fun createRejectsAnUnsupportedConfig() =
        runTest {
            val factory = newFactory()

            val result = factory.create(WscdConfig.Remote(endpoint = "https://example.com"))

            assertTrue(result.isErr, "factory must reject configs it does not support")
        }

    private suspend fun newFactory(): SoftwareWscdFactory {
        val sessionId = "wallet-wscd-software-factory-test-${Uuid.v4String()}"
        val app =
            createWalletAppGraph(
                application = "SoftwareWscdFactoryTest",
                appId = "com.sphereon.wallet.wscd-software-factory-test",
                profile = "test",
                version = "0.1.0",
            )
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId(sessionId)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        val softwareKmsProviderFactory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val registrar =
            SoftwareKmsProviderRegistrar(
                keyManagerService = kms,
                softwareKmsProviderFactory = softwareKmsProviderFactory,
                execution = session.asCoreApiServiceGraph().serviceExecution,
                app = app,
                keyStoreConfiguration = SoftwareWscdKeyStoreConfiguration.InMemoryForTestingOnly,
                sessionId = sessionId,
            )
        return SoftwareWscdFactory(
            keyManagerService = kms,
            providerRegistrar = registrar,
        )
    }
}
