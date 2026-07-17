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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.kms.provider.mobile.MOBILE_KMS_HARDWARE_BACKING_KEY
import com.sphereon.crypto.kms.provider.mobile.MOBILE_KMS_HARDWARE_BACKING_REQUIRED
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProviderConfig
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProviderImplFactory
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.wscd.Wscd
import com.sphereon.wallet.wscd.WscdConfig
import com.sphereon.wallet.wscd.WscdFactory
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Creates [LocalNativeWscd] instances for [WscdConfig.LocalNative].
 *
 * Product/runner bootstraps hold a [WscdConfig], never a KMS reference; they resolve the
 * contributed `Set<WscdFactory>` and let the matching factory (this one, for the local-native
 * profile) own KMS wiring end to end - mirroring
 * [com.sphereon.wallet.wscd.software.SoftwareWscdFactory].
 *
 * Unlike the software factory, there is no separately-injected "provider bootstrap": a fresh
 * [com.sphereon.crypto.kms.provider.mobile.MobileKmsProvider] is constructed directly by [create]
 * (via [mobileKmsProviderImplFactory]) for every call, scoped to the calling session
 * (`"$sessionId-mobile-kms"`, mirroring `SoftwareKmsProviderRegistrar`'s
 * `"$sessionId-software-kms"` id scheme). There is no session-shared KMS provider REGISTRY to
 * lazily register into here (contrast the software profile's [com.sphereon.crypto.core.kms.KeyManagerService]):
 * the platform keystore is reached directly through the constructed provider instance, which
 * [LocalNativeWscd] then owns exclusively.
 *
 * `WscdConfig.LocalNative.requireStrongBox` is threaded into the provider's
 * `defaultConfigValues[MOBILE_KMS_HARDWARE_BACKING_KEY]` so `MobileKmsProviderImpl` requests
 * signum-supreme's `REQUIRED` hardware-backing feature preference instead of its default
 * `PREFERRED` (see [LocalNativeWscd]'s class KDoc "requireStrongBox" section for the fail-closed
 * semantics this produces).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<WscdFactory>())
class LocalNativeWscdFactory(
    private val mobileKmsProviderImplFactory: MobileKmsProviderImplFactory,
    private val execution: SessionExecution,
    @Named("sessionId") private val sessionId: String,
) : WscdFactory {
    override fun supports(config: WscdConfig): Boolean = config is WscdConfig.LocalNative

    override suspend fun create(config: WscdConfig): IdkResult<Wscd, IdkError> {
        if (config !is WscdConfig.LocalNative) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "LocalNativeWscdFactory only supports WscdConfig.LocalNative, got '$config'",
                ),
            )
        }
        val providerConfig =
            MobileKmsProviderConfig(
                id = "$sessionId-mobile-kms",
                defaultConfigValues =
                    if (config.requireStrongBox) {
                        mapOf(MOBILE_KMS_HARDWARE_BACKING_KEY to MOBILE_KMS_HARDWARE_BACKING_REQUIRED)
                    } else {
                        emptyMap()
                    },
            )
        val provider = mobileKmsProviderImplFactory.create(providerConfig, execution)
        return Ok(LocalNativeWscd(provider = provider, requireStrongBox = config.requireStrongBox))
    }
}
