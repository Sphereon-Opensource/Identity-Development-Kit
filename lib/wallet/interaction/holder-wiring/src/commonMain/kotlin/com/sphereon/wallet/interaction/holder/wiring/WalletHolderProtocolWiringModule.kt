/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.interaction.WalletAttendedAuthorizationRegistry
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationPolicyProvider
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolver
import com.sphereon.wallet.interaction.protocol.iso18013.Iso18013DisclosureExecutor
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/** API-only holder wiring contract; concrete selection belongs to final application graphs. */
@ContributesTo(SessionScope::class)
interface WalletHolderProtocolWiringModule {
    @Multibinds(allowEmpty = true)
    fun walletInteractionProtocolAdapters(): Set<WalletInteractionProtocolAdapter>

    @Multibinds(allowEmpty = true)
    fun iso18013DisclosureExecutors(): Set<Iso18013DisclosureExecutor>

    @Multibinds(allowEmpty = true)
    fun walletIdentityResolvers(): Set<WalletIdentityResolver>

    @Multibinds(allowEmpty = true)
    fun walletIssuerAuthenticationResolvers(): Set<WalletIssuerAuthenticationResolver>

    @Multibinds(allowEmpty = true)
    fun walletIssuerAuthenticationPolicyProviders(): Set<WalletIssuerAuthenticationPolicyProvider>

    @Multibinds(allowEmpty = true)
    fun walletIssuerAuthenticationExternalIdentifierServices(): Set<ExternalIdentifierService>
}

/** Session graph accessor exposed by final wallet application graphs. */
@ContributesTo(SessionScope::class)
interface WalletInteractionClientGraph {
    val walletInteractionClient: WalletInteractionClient
    val walletInteractionSensitiveInputAuthority: WalletInteractionSensitiveInputAuthority
}

@ContributesTo(SessionScope::class)
interface WalletInteractionProtocolAdaptersGraph {
    val walletInteractionProtocolAdapters: Set<WalletInteractionProtocolAdapter>
}

@ContributesTo(SessionScope::class)
interface WalletCounterpartyEncounterRegistryGraph {
    val walletCounterpartyEncounterRegistry: com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
}

/** Public accessor for the app-owned attended registry binding. */
@ContributesTo(SessionScope::class)
interface WalletAttendedAuthorizationRegistryGraph {
    val walletAttendedAuthorizationRegistry: WalletAttendedAuthorizationRegistry
}
