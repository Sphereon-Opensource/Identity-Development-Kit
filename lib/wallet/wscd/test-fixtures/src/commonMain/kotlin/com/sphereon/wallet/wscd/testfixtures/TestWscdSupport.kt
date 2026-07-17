/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.testfixtures

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.session.SessionInstance

/**
 * Test-only software-KMS wiring for a SoftwareWscd-backed session.
 *
 * This is the ONE sanctioned place tests reach for raw KMS setup: it lives under
 * `lib/wallet/wscd/`, so both `verifyWalletKmsBoundary`'s directory-exempt rule and
 * `verifyWalletKmsDependencyRuleTask`'s `lib-wallet-wscd-*` name-prefix exemption cover it.
 * Product/runner bootstraps never perform KMS wiring directly against a session graph; that
 * responsibility lives in `SoftwareWscd`'s own lazy provider registration (see
 * `SoftwareKmsProviderRegistrar` in lib-wallet-wscd-software).
 *
 * Most callers never need this: a session's Wscd/Wsca bindings (`SoftwareWscd`, `LocalWsca`)
 * already register their own software KMS provider lazily on first key operation. This helper
 * exists for tests that ALSO need a raw [KeyManagerService] handle to set up TEST-STACK key
 * material that is not wallet holder code (e.g. an in-process issuer/verifier/OAuth2-AS signing
 * key), so those tests can obtain a ready [KeyManagerService] without importing KMS provider
 * types themselves.
 */
object TestWscdSupport {
    /**
     * Ensures a software KMS provider identified by [providerId] is registered on [session]
     * (idempotent: a no-op if a provider with that id is already registered), then returns the
     * session's [KeyManagerService].
     *
     * [app] must implement `SoftwareKmsProviderFactoryImpl.Graph` (every wallet product/runner/
     * test app graph does); it is accepted as [Any] so callers do not need to import KMS provider
     * types themselves just to perform the cast.
     */
    fun ensureSoftwareKmsProvider(
        app: Any,
        session: SessionInstance,
        providerId: String,
        makeDefaultKms: Boolean = true,
    ): KeyManagerService {
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        if (!kms.getProviderIds().contains(providerId)) {
            val factory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
            val provider = factory.create(SoftwareKmsProviderConfig(id = providerId), session.asCoreApiServiceGraph().serviceExecution)
            kms.registerProvider(provider, makeDefaultKms = makeDefaultKms)
        }
        return kms
    }
}
