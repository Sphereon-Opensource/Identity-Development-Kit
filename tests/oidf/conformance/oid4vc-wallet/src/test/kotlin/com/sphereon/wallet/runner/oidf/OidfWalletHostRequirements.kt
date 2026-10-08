/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.wallet.runner.oidf

import com.sphereon.core.api.Ok
import com.sphereon.wallet.app.host.createJvmLocalWalletHostRequirements
import com.sphereon.wallet.app.spi.WalletLocalPinCapture
import com.sphereon.wallet.runner.di.WalletRunnerHostRequirements
import com.sphereon.wallet.wsca.WscaPinEntryStage
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import java.nio.file.Path

/** Promptless PIN capture belongs only to this isolated conformance test composition. */
internal fun createOidfWalletHostRequirements(): WalletRunnerHostRequirements {
    val roots = Path.of("build", "oidf-wallet-hosts")
    Files.createDirectories(roots)
    val root = Files.createTempDirectory(roots, "host-")
    val host =
        createJvmLocalWalletHostRequirements(
            root,
            object : WalletLocalPinCapture {
                private var pinLength: Int? = null
                override val stage = MutableStateFlow(WscaPinEntryStage.IDLE)

                override suspend fun captureAndConfirmPin(requiredLength: Int) =
                    Ok(
                        CharArray(
                            requiredLength.also {
                                require(it > 0)
                                pinLength = it
                            },
                        ) { '1' },
                    )

                override suspend fun capturePin() = Ok(CharArray(checkNotNull(pinLength)) { '1' })

                override fun cancelActiveCapture() = Unit
            },
        )
    return WalletRunnerHostRequirements(
        host.runtimeBoundary,
        host.profileDescriptorStore,
        host.userAuthenticationComponent,
        host.softwareWscdKeyStoreConfiguration,
        host.didRepository,
    )
}
