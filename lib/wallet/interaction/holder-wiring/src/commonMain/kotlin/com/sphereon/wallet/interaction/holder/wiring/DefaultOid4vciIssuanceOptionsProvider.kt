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

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciHolderIssuanceOptions
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciIssuanceOptionsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciWalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.protocol.oid4vci.withLaunchAttributes
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

private const val DEFAULT_WALLET_HOLDER_CLIENT_ID: String = "https://wallet.sphereon.com"
private const val HOLDER_SIGNING_ALGORITHM: String = "ES256"

/**
 * Default OID4VCI holder-proof signing options for a Tier 1 wallet unit: provisions a FRESH holder
 * proof key per credential issuance THROUGH THE WALLET UNIT ([Wsca]) and reports the unit's opaque
 * key reference plus the credential configuration id resolved from the offer.
 *
 * UNLINKABILITY: each call to [options] mints a brand-new holder key via [Wsca.createCredentialKey]
 * rather than reusing one key across the wallet instance's whole session (ARF v2.9.0 ISSU_12b).
 *
 * WSCA/WSCD BOUNDARY: this holder code routes key provisioning through the injected [Wsca] and NEVER
 * calls the KMS directly.
 *
 * `wallet-runner` replaces this binding with its own `RunnerOid4vciIssuanceOptionsProvider`, which
 * additionally supplies HAIP token proofs and a bootstrap-overridable client id; this default carries
 * no HAIP support and a fixed client id, matching a plain Tier 1 product deployment.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuanceOptionsProvider>())
class DefaultOid4vciIssuanceOptionsProvider(
    private val walletUnitCryptoSurface: Wsca,
) : Oid4vciIssuanceOptionsProvider {
    override suspend fun options(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
    ): Oid4vciHolderIssuanceOptions {
        val keyRef = mintCredentialKey(state.walletUnitId)
        val operationBinding =
            context.privateSessionStore
                .get(context.sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
                ?.values
                ?.get(Oid4vciWalletInteractionProtocolAdapter.SECURITY_OPERATION_BINDING_PRIVATE_KEY)
                ?.takeIf { it.isNotBlank() }
        return Oid4vciHolderIssuanceOptions(
            signingKeyId = keyRef.keyRef ?: keyRef.keyId,
            operationBinding = operationBinding,
            signingAlgorithm = HOLDER_SIGNING_ALGORITHM,
            clientId = DEFAULT_WALLET_HOLDER_CLIENT_ID,
            credentialConfigurationId =
                state.credentialOffer?.credentialConfigurationIds?.firstOrNull()
                    ?: resolvedOffer.offer.credentialConfigurationIds
                        .firstOrNull(),
            haipTokenProofs = null,
        ).withLaunchAttributes(context.attributes)
    }

    private suspend fun mintCredentialKey(walletUnitId: String): WalletAttestedKeyRef =
        walletUnitCryptoSurface
            .createCredentialKey(
                walletUnitId = walletUnitId,
                usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                algorithm = SignatureAlgorithm.ECDSA_SHA256,
            ).getOrElse { error ->
                throw IllegalStateException("Wallet unit could not provision the holder proof key: ${error.code}")
            }
}
