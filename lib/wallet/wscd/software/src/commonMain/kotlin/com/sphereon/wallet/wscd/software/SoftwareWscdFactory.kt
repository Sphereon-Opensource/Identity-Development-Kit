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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.wscd.Wscd
import com.sphereon.wallet.wscd.WscdConfig
import com.sphereon.wallet.wscd.WscdFactory
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Creates [SoftwareWscd] instances for [WscdConfig.Software].
 *
 * Product/runner bootstraps hold a [WscdConfig], never a KMS reference; they resolve the
 * contributed `Set<WscdFactory>` and let the matching factory (this one, for the software
 * profile) own KMS wiring end to end.
 *
 * KMS provider registration is owned by [SoftwareKmsProviderRegistrar], NOT
 * by this factory: [create] simply forwards the session-scoped registrar into a new
 * [SoftwareWscd] instance (fresh key cache, per this factory's contract - callers must call
 * [create] once per session and hold the result). Registration itself happens lazily on that
 * instance's first `generateKey`/`generateFreshKey` call, exactly the same way the direct
 * SessionScope-injected [SoftwareWscd] singleton registers - so there is no double registration
 * between the two paths, and this factory never needs its own copy of the registration logic.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<WscdFactory>())
class SoftwareWscdFactory(
    private val keyManagerService: KeyManagerService,
    private val providerRegistrar: KmsProviderBootstrap,
) : WscdFactory {
    override fun supports(config: WscdConfig): Boolean = config is WscdConfig.Software

    override suspend fun create(config: WscdConfig): IdkResult<Wscd, IdkError> {
        if (config !is WscdConfig.Software) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "SoftwareWscdFactory only supports WscdConfig.Software, got '$config'",
                ),
            )
        }
        return Ok(SoftwareWscd(keyManagerService, providerRegistrar))
    }
}
