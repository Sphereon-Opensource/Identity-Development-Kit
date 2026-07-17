/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.mobile

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProviderImpl
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProviderImplFactory
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
 * Real-KMS-graph tests for [LocalNativeWscdFactory]: verifies it produces a working
 * [LocalNativeWscd] wired to a real [MobileKmsProviderImpl] (mirrors
 * [com.sphereon.wallet.wscd.software.SoftwareWscdFactoryTest] for the [WscdProfile.LocalNative]
 * profile).
 *
 * [MobileKmsProviderImplFactory] is a Metro `@AssistedFactory` fun interface with only assisted
 * (non-injected) parameters, so it is trivially implemented with a lambda here - no Metro
 * dependency graph is needed to exercise [LocalNativeWscdFactory] itself.
 */
class LocalNativeWscdFactoryTest {
    @Test
    fun supportsOnlyWscdConfigLocalNative() =
        runTest {
            val factory = newFactory()

            assertTrue(factory.supports(WscdConfig.LocalNative()))
            assertFalse(factory.supports(WscdConfig.Software()))
            assertFalse(factory.supports(WscdConfig.Remote(endpoint = "https://example.com")))
        }

    @Test
    fun createReturnsAWorkingWscdForTheDefaultPreferredBackingConfig() =
        runTest {
            val factory = newFactory()

            val result = factory.create(WscdConfig.LocalNative(requireStrongBox = false))
            assertTrue(result.isOk, "create failed: ${if (result.isErr) result.error else ""}")
            assertEquals(WscdProfile.LocalNative, result.value.profile)

            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-factory-test",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val generated = result.value.generateKey(spec)
            assertTrue(generated.isOk, "generateKey failed: ${if (generated.isErr) generated.error else ""}")
        }

    @Test
    fun createRejectsAnUnsupportedConfig() =
        runTest {
            val factory = newFactory()

            val result = factory.create(WscdConfig.Software())

            assertTrue(result.isErr, "factory must reject configs it does not support")
        }

    /**
     * End-to-end validation of the `requireStrongBox` wiring THROUGH the factory (not just
     * [LocalNativeWscd] directly, see `LocalNativeWscdTest`): a
     * `WscdConfig.LocalNative(requireStrongBox = true)` must produce a `Wscd` whose `generateKey`
     * fails closed on the JVM's JKS software-fallback keystore, proving the factory actually threads
     * `requireStrongBox` into the provider config it constructs
     * (`MOBILE_KMS_HARDWARE_BACKING_KEY` = `..._REQUIRED`), not just into the
     * `LocalNativeWscd.requireStrongBox` flag used for error classification.
     */
    @Test
    fun createWithRequireStrongBoxProducesAWscdThatFailsClosedOnTheJvmSoftwareFallback() =
        runTest {
            val factory = newFactory()

            val result = factory.create(WscdConfig.LocalNative(requireStrongBox = true))
            assertTrue(result.isOk, "create failed: ${if (result.isErr) result.error else ""}")

            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-factory-strongbox-test",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val generated = result.value.generateKey(spec)

            assertTrue(generated.isErr, "requireStrongBox=true must fail closed on the JVM's JKS software-fallback keystore")
            assertEquals("WALLET_WSCD_HARDWARE_REQUIRED", generated.error.code)
        }

    private suspend fun newFactory(): LocalNativeWscdFactory {
        val sessionId = "wallet-wscd-mobile-factory-test-${Uuid.v4String()}"
        val app =
            staticMinimalTestAppGraph(
                application = "LocalNativeWscdFactoryTest",
                appId = "com.sphereon.wallet.wscd-mobile-factory-test",
                profile = "test",
                version = "0.1.0",
            )
        val user = app.userContextManager.getAnonymous()
        val session = user.sessionContextManager.createOrGetFromId(sessionId)
        return LocalNativeWscdFactory(
            mobileKmsProviderImplFactory = MobileKmsProviderImplFactory { config, execution -> MobileKmsProviderImpl(config, execution) },
            execution = session.asCoreApiServiceGraph().serviceExecution,
            sessionId = sessionId,
        )
    }
}
