/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.credential.CredentialSubjectExtractor
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletTrustPolicy
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.KvWalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.impl.KvWalletInteractionSessionStore
import com.sphereon.wallet.interaction.impl.LocalWalletInteractionClient
import com.sphereon.wallet.interaction.impl.StoreBackedWalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.impl.WalletInteractionSessionStore
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciHolderIssuanceExecutor
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciIssuanceOptionsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciIssuedCredentialAcceptance
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciKeyAttestationProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciRefreshTokenGrantProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciTokenEndpointProofsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.HolderServiceOid4vciCredentialRequestProofProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.WalletStoreOid4vciCredentialResponseReceiver
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciWalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpJarmOptionsProvider
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpNestedPresentationExecutor
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpWalletConfigProvider
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpWalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.protocol.oid4vp.SecureComponentOid4vpSdJwtHolderBindingProvider
import com.sphereon.wallet.interaction.protocol.oid4vp.WalletStoreOid4vpCredentialResolver
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The ONE SessionScope declaration of the mandatory OID4VCI and OID4VP holder protocol wiring: the
 * extensible adapter multibinding, the two executable protocol-adapter contributions, the default
 * security-gate/protocol-executor policies, and the [WalletInteractionClient] this module's
 * consumers drive Tier 2 flows through.
 *
 * Both `wallet-app-impl`'s own product graph and `wallet-runner`'s graph depend on this module and
 * get REAL flow execution from it, instead of each declaring a competing, duplicate wiring - Metro
 * rejects two `@Multibinds` declarations (or two competing `@IntoSet`/`@Provides` contributions) for
 * the same key on one merged graph, so this MUST be the sole declarer wherever both graphs end up on
 * the same classpath (e.g. once `wallet-runner` depends on `wallet-app-impl` for the Tier 1 `WalletApp`
 * surface, see [com.sphereon.wallet.runner.LocalHeadlessWalletRunner]).
 *
 * Deployment-varying seams (which [WalletTrustPolicy], which [ResponseMode] override, which identity
 * the wallet presents as, ...) are intentionally NOT declared here as `@Provides` inside this
 * interface: each has its own small, independently `replaces`-able module/class
 * ([DefaultWalletCounterpartyEncounterRegistryModule], [DefaultWalletTrustPolicyModule], [DefaultOid4vpResponseModeModule],
 * [DefaultOid4vciIssuanceOptionsProvider], [DefaultOid4vpWalletConfigProvider]) so a consumer like
 * `wallet-runner` can override exactly one seam (e.g. conformance trust policy) via
 * `@ContributesTo`/`@ContributesBinding(replaces = [...])` without having to re-declare the adapters
 * or the multibinding themselves.
 */
@ContributesTo(SessionScope::class)
interface WalletHolderProtocolWiringModule {
    /**
     * Declares the extensible adapter set. `allowEmpty = true` keeps the graph resolvable even on a
     * classpath that contributes no adapters. This is the SOLE `@Multibinds` declaration for this key;
     * consumers contribute additional adapters via
     * `@Provides @IntoSet`, never a second `@Multibinds`.
     */
    @Multibinds(allowEmpty = true)
    fun walletInteractionProtocolAdapters(): Set<WalletInteractionProtocolAdapter>

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletSecurityGate(wsca: Wsca): WalletSecurityGate = WscaWalletSecurityGate(wsca)

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletProtocolExecutor(): WalletProtocolExecutor = WalletProtocolExecutor.walletApp

    /**
     * Real OID4VCI credential-receive adapter, backed by the DI-wired [Oid4vciHolderService] (which
     * does all issuer HTTP through the injected `HttpClientFactory`) and writing received credentials
     * into the real [WalletCredentialStore].
     */
    @Provides
    @IntoSet
    fun provideOid4vciAdapter(
        holder: Oid4vciHolderService,
        oid4vpHolder: Oid4vpHolderService,
        credentialStore: WalletCredentialStore,
        issuanceSessionStore: WalletIssuanceSessionStore,
        optionsProvider: Oid4vciIssuanceOptionsProvider,
        walletConfigProvider: Oid4vpWalletConfigProvider,
        tokenEndpointProofsProvider: Oid4vciTokenEndpointProofsProvider,
        refreshTokenGrantProvider: Oid4vciRefreshTokenGrantProvider,
        keyAttestationProvider: Oid4vciKeyAttestationProvider,
        walletIdentityResolver: WalletIdentityResolver,
        credentialSubjectExtractor: CredentialSubjectExtractor,
        verifySdJwtVcCommand: VerifySdJwtVcCommand,
        walletUnitCryptoSurface: Wsca,
    ): WalletInteractionProtocolAdapter =
        Oid4vciWalletInteractionProtocolAdapter(
            holder = holder,
            issuanceExecutor =
                Oid4vciHolderIssuanceExecutor(
                    holder = holder,
                    optionsProvider = optionsProvider,
                    credentialReceiver =
                        WalletStoreOid4vciCredentialResponseReceiver(
                            credentialStore,
                            issuanceSessionStore,
                            Oid4vciIssuedCredentialAcceptance(
                                verifySdJwtVcCommand = verifySdJwtVcCommand,
                                subjectExtractor = credentialSubjectExtractor,
                                identityResolver = walletIdentityResolver,
                            ),
                        ),
                    nestedPresentationExecutor =
                        Oid4vpNestedPresentationExecutor(
                            holder = oid4vpHolder,
                            selectedCredentialResolver = WalletStoreOid4vpCredentialResolver(credentialStore),
                            sdJwtHolderBindingProvider = SecureComponentOid4vpSdJwtHolderBindingProvider(walletUnitCryptoSurface),
                            walletConfigProvider = walletConfigProvider,
                        ),
                    tokenEndpointProofsProvider = tokenEndpointProofsProvider,
                    credentialStore = credentialStore,
                    issuanceSessionStore = issuanceSessionStore,
                    refreshTokenGrantProvider = refreshTokenGrantProvider,
                    keyAttestationProvider = keyAttestationProvider,
                    // Holder proof keys are managed through Wsca.createCredentialKey (see
                    // DefaultOid4vciIssuanceOptionsProvider). Proof signing MUST use the same WSCA
                    // surface: WSCA selects the WSCD, and that WSCD uses its configured KMS.
                    credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                ),
        )

    /**
     * Real OID4VP presentation adapter, backed by the DI-wired [Oid4vpHolderService] and reading
     * held credentials from the real [WalletCredentialStore] (candidate resolution, selected-credential
     * resolution and presentation submission all run against the store).
     */
    @Provides
    @IntoSet
    fun provideOid4vpAdapter(
        holder: Oid4vpHolderService,
        credentialStore: WalletCredentialStore,
        walletConfigProvider: Oid4vpWalletConfigProvider,
        jarmOptionsProvider: Oid4vpJarmOptionsProvider,
        responseModeOverride: ResponseMode?,
        walletUnitCryptoSurface: Wsca,
    ): WalletInteractionProtocolAdapter =
        Oid4vpWalletInteractionProtocolAdapter.walletStoreBacked(
            holder = holder,
            credentialStore = credentialStore,
            walletConfigProvider = walletConfigProvider,
            jarmOptionsProvider = jarmOptionsProvider,
            responseMode = responseModeOverride,
            // Holder keys for OID4VCI issuance are managed through Wsca.createCredentialKey. The
            // SD-JWT Key Binding JWT must use the same WSCA surface: WSCA selects the WSCD, and that
            // WSCD resolves the key and signs with its configured KMS.
            sdJwtHolderBindingProvider = SecureComponentOid4vpSdJwtHolderBindingProvider(walletUnitCryptoSurface),
        )

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletInteractionPrivateSessionStore(kvStoreService: KvStoreService): WalletInteractionPrivateSessionStore =
        KvWalletInteractionPrivateSessionStore(kvStoreService.getStore(WALLET_INTERACTION_KV_STORE_ID))

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletInteractionSessionStore(kvStoreService: KvStoreService): WalletInteractionSessionStore =
        KvWalletInteractionSessionStore(kvStoreService.getStore(WALLET_INTERACTION_KV_STORE_ID))

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletInteractionSensitiveInputAuthority(
        privateSessionStore: WalletInteractionPrivateSessionStore,
    ): WalletInteractionSensitiveInputAuthority =
        StoreBackedWalletInteractionSensitiveInputAuthority(privateSessionStore)

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletInteractionEngine(
        adapters: Set<WalletInteractionProtocolAdapter>,
        securityGate: WalletSecurityGate,
        protocolExecutor: WalletProtocolExecutor,
        counterpartyEncounterRegistry: WalletCounterpartyEncounterRegistry,
        trustPolicy: WalletTrustPolicy,
        sensitiveInputAuthority: WalletInteractionSensitiveInputAuthority,
        privateSessionStore: WalletInteractionPrivateSessionStore,
        sessionStore: WalletInteractionSessionStore,
    ): DefaultWalletInteractionEngine =
        DefaultWalletInteractionEngine(
            adapters = adapters.toList(),
            protocolExecutor = protocolExecutor,
            counterpartyEncounterRegistry = counterpartyEncounterRegistry,
            securityGate = securityGate,
            trustPolicy = trustPolicy,
            sensitiveInputAuthority = sensitiveInputAuthority,
            privateSessionStore = privateSessionStore,
            sessionStore = sessionStore,
        )

    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletInteractionClient(engine: DefaultWalletInteractionEngine): WalletInteractionClient =
        LocalWalletInteractionClient(engine)

}

// WalletApp platform graphs expose their durable, tenant-scoped KV substrate under this ID.
// Interaction records use dedicated namespaces inside that store and never share blob payloads.
private const val WALLET_INTERACTION_KV_STORE_ID = "blob.metadata"

/**
 * Session-graph accessor for [WalletInteractionClient], consumed by composition roots
 * (`DefaultWalletProfileHandle` in `wallet-app-impl`) that reach a session-scoped binding by
 * casting `SessionInstance.graph` to this interface - mirroring
 * `com.sphereon.wallet.credential.store.WalletUnitStoresGraph` /
 * `com.sphereon.wallet.wsca.impl.WscaGraph`.
 */
@ContributesTo(SessionScope::class)
interface WalletInteractionClientGraph {
    val walletInteractionClient: WalletInteractionClient
    val walletInteractionSensitiveInputAuthority: WalletInteractionSensitiveInputAuthority
}

/**
 * Neutral encounter registry for non-product protocol runners. Local and managed wallet products
 * replace this binding with their Party-backed implementation without changing protocol wiring.
 */
@ContributesTo(SessionScope::class)
interface DefaultWalletCounterpartyEncounterRegistryModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletCounterpartyEncounterRegistry(): WalletCounterpartyEncounterRegistry =
        WalletCounterpartyEncounterRegistry.none
}

/**
 * Default [WalletTrustPolicy]: warns rather than silently trusting an unrecognised counterparty.
 * Its own small `@ContributesTo` interface (rather than a `@Provides` member of
 * [WalletHolderProtocolWiringModule]) so a consumer that needs different behaviour (e.g.
 * `wallet-runner`'s conformance trust profile) can exclude exactly this default via
 * `@ContributesTo(SessionScope::class, replaces = [DefaultWalletTrustPolicyModule::class])` without
 * touching the adapter wiring.
 */
@ContributesTo(SessionScope::class)
interface DefaultWalletTrustPolicyModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideWalletTrustPolicy(): WalletTrustPolicy = WalletTrustPolicy.warn
}

/**
 * Default OID4VP response-mode override: `null` (no override; the adapter's own default applies).
 * `wallet-runner`'s HAIP protocol profile replaces this with one that selects
 * `ResponseMode.DIRECT_POST_JWT` when the HAIP conformance flag is enabled.
 */
@ContributesTo(SessionScope::class)
interface DefaultOid4vpResponseModeModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideOid4vpResponseModeOverride(): ResponseMode? = null
}
