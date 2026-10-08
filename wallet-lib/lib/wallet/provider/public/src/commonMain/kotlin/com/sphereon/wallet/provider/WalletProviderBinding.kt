/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider

import kotlinx.serialization.Serializable

/**
 * Which [WalletProvider] implementation produced a [WalletProviderBinding.descriptor]: `LOCAL` for
 * the OSS self-signing default ([WalletProviderKind.LOCAL], `com.sphereon.wallet.provider.local.LocalWalletProvider`),
 * `MANAGED` for an enterprise-operated wallet-unit backend reached through the EDK MANAGED
 * provisioning saga (`com.sphereon.wallet.unit.provider.RemoteWalletProvider`).
 */
@Serializable
enum class WalletProviderKind { LOCAL, MANAGED }

/**
 * Persists which [WalletProvider] provisioned a wallet profile's unit and what it returned, so a
 * profile descriptor carries this as ONE typed fact rather than the provider identity/descriptor
 * fields being reconstructed ad hoc by every reader.
 *
 * [providerId] is provider-implementation-defined: the OSS `LocalWalletProvider` uses its
 * configured `LocalWalletProviderConfig.providerId`; the EDK MANAGED path uses the backend tenant
 * id of the wallet-unit backend that provisioned the unit (see `WalletUnitProfileProvisioner`).
 */
@Serializable
data class WalletProviderBinding(
    val providerId: String,
    val kind: WalletProviderKind,
    val descriptor: WalletUnitDescriptor,
)
