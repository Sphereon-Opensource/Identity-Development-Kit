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
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A credential configuration saved moments before its credential is requested may be missing from
 * the snapshot the issuer reads its signing configurations from. The credential request and the
 * deferred pipeline must then reload once instead of refusing to issue; a configuration the backing
 * store does not hold either is still refused.
 */
class SigningConfigReloadingOnMissTest {
    private val badge = CredentialSigningConfig(signingKeyAlias = "issuer-signing", expirationInDays = 365)

    @Test
    fun signingConfigurationWrittenAfterTheSnapshotIsFoundAfterOneReload() =
        runTest {
            val provider = SnapshotSigningProvider(snapshot = emptyMap(), backingStore = mapOf("EmployeeBadge" to badge))

            assertEquals(badge, provider.signingConfigReloadingOnMiss("EmployeeBadge"))
            assertEquals(1, provider.reloads)
        }

    @Test
    fun signingConfigurationUnknownToTheBackingStoreIsStillMissing() =
        runTest {
            val provider = SnapshotSigningProvider(snapshot = emptyMap(), backingStore = emptyMap())

            assertNull(provider.signingConfigReloadingOnMiss("DoesNotExist"))
            assertEquals(1, provider.reloads)
        }

    @Test
    fun signingConfigurationInTheSnapshotDoesNotReload() =
        runTest {
            val provider = SnapshotSigningProvider(snapshot = mapOf("EmployeeBadge" to badge), backingStore = mapOf("EmployeeBadge" to badge))

            assertEquals(badge, provider.signingConfigReloadingOnMiss("EmployeeBadge"))
            assertEquals(0, provider.reloads)
        }

    private class SnapshotSigningProvider(
        snapshot: Map<String, CredentialSigningConfig>,
        private val backingStore: Map<String, CredentialSigningConfig>,
    ) : Oid4vciIssuerConfigProvider {
        private var published: Map<String, CredentialSigningConfig> = snapshot
        var reloads = 0
            private set

        override val issuerIdentifier: String = "https://issuer.example.com"
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()
        override val authorizationServers: List<String>? = null
        override val display: List<DisplayProperties>? = null

        override suspend fun credentialSigningConfigs(): Map<String, CredentialSigningConfig> = published

        override suspend fun reloadConfiguration() {
            reloads++
            published = backingStore
        }
    }
}
