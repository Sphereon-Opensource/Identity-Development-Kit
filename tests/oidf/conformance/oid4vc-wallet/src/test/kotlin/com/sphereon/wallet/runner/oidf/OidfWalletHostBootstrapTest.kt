/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.wallet.runner.oidf

import com.sphereon.wallet.runner.HeadlessWalletRunnerBootstrap
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class OidfWalletHostBootstrapTest {
    @Test
    fun isolatedConformanceHostResolvesRunnerWithRealPinEnrollment() =
        runTest {
            val host = createOidfWalletHostRequirements()
            val bootstrap = HeadlessWalletRunnerBootstrap.create(hostRequirements = host)
            try {
                // runner() resolves the real LOCAL profile and executes mandatory WSCA PIN enrollment.
                assertNotNull(bootstrap.runner())
            } finally {
                bootstrap.destroy()
            }
        }
}
