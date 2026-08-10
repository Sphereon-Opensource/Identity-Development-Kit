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

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.PrivateKeyJwtAssertionAssembly
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.wallet.interaction.protocol.oid4vci.HolderServiceOid4vciRefreshTokenGrantProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciKeyAttestationProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciRefreshTokenGrantProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciTokenEndpointProofsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.SecureComponentOid4vciTokenEndpointProofsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.SecureComponentOid4vciKeyAttestationProvider
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpJarmOptionsProvider
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Trivial, safe-by-default bindings for the OID4VCI/OID4VP holder wiring's optional seams. The
 * OID4VCI seams default to the interface's own `.none`/`.unsupported` companion (the safe-default
 * convention those interfaces establish); the JARM seam defaults to working encrypted responses
 * (see the member KDoc).
 *
 * A consumer that needs real behaviour for one or more of these (client-attestation DPoP proofs,
 * KA-on-demand, JARM) excludes this WHOLE module via
 * `@ContributesTo(SessionScope::class, replaces = [DefaultWalletHolderOptionalSeamsModule::class])`
 * and re-declares every member (Metro has no per-member override, only whole-declaration
 * `replaces`); `wallet-runner`'s `WalletRunnerInteractionModule` does exactly this - it needs custom
 * behaviour for three of the four seams (DPoP-decorated token-endpoint proofs, real KA-on-demand,
 * HAIP-aware JARM options) and simply re-declares the fourth ([Oid4vciRefreshTokenGrantProvider])
 * identically to this module's default.
 */
@ContributesTo(SessionScope::class)
interface DefaultWalletHolderOptionalSeamsModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideOid4vciTokenEndpointProofsProvider(
        wsca: Wsca,
        privateKeyJwtAssertionAssembly: PrivateKeyJwtAssertionAssembly,
    ): Oid4vciTokenEndpointProofsProvider =
        SecureComponentOid4vciTokenEndpointProofsProvider(wsca, privateKeyJwtAssertionAssembly)

    @Provides
    @SingleIn(SessionScope::class)
    fun provideOid4vciRefreshTokenGrantProvider(
        holder: Oid4vciHolderService,
    ): Oid4vciRefreshTokenGrantProvider =
        HolderServiceOid4vciRefreshTokenGrantProvider(holder)

    @Provides
    @SingleIn(SessionScope::class)
    fun provideOid4vciKeyAttestationProvider(wsca: Wsca): Oid4vciKeyAttestationProvider =
        SecureComponentOid4vciKeyAttestationProvider(wsca)

    /**
     * Unlike the other seams this one does NOT default to `.none`: a null-options seam makes every
     * `direct_post.jwt` presentation fail closed with "jarmOptions is required", and encrypted
     * responses per OpenID4VP 1.0 section 8.3 are the norm for EUDI-style verifiers. The default
     * therefore enables unsigned encrypted JARM, deriving the JWE setup from the verifier's
     * client_metadata, with the wallet unit id as payload issuer.
     */
    @Provides
    @SingleIn(SessionScope::class)
    fun provideOid4vpJarmOptionsProvider(): Oid4vpJarmOptionsProvider = Oid4vpJarmOptionsProvider.walletUnitIssuer
}
