/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.wallet.WalletHolderVerificationMethodProvisioner
import com.sphereon.wallet.WalletHolderVerificationMethodRegistrar
import com.sphereon.wallet.credential.WalletHolderIdentifierKind
import com.sphereon.wallet.credential.WalletHolderVerificationMethod
import com.sphereon.wallet.credential.WalletUnitStore
import com.sphereon.wallet.interaction.WalletHolderKeyProvisioningService
import com.sphereon.wallet.interaction.WalletInteractionDiagnostics
import com.sphereon.wallet.interaction.toDiagnosticDetails
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/** Self-contained default policy; deployments can replace it with JWKS, managed, or X.509 policy. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletHolderVerificationMethodProvisioner>())
class DidJwkWalletHolderVerificationMethodProvisioner(
    private val didProviderRegistry: DidProviderRegistry,
) : WalletHolderVerificationMethodProvisioner {
    override suspend fun provision(
        walletUnitId: String,
        keyAlias: String,
        attestedKey: WalletAttestedKeyRef,
        signingAlgorithm: SignatureAlgorithm,
    ): WalletHolderVerificationMethod {
        val publicJwk =
            attestedKey.publicKeyJwk
                ?.let { encoded ->
                    try {
                        Json.decodeFromString<Jwk>(encoded)
                    } catch (cause: Exception) {
                        throw IllegalStateException("WSCA returned malformed public JWK for holder key '$keyAlias'", cause)
                    }
                }
                ?: throw IllegalStateException("WSCA did not return a public JWK for holder key '$keyAlias'")
        val didProvider =
            didProviderRegistry.getProvider("jwk")
                ?: throw IllegalStateException("did:jwk holder identifier provisioning requires a registered JWK DID provider")
        val did =
            didProvider
                .create(DidCreateOptions(method = "jwk", publicKeyJwk = publicJwk))
                .getOrElse { error ->
                    throw IllegalStateException("JWK DID provider could not derive a holder identifier: ${error.code}")
                }.did
        return WalletHolderVerificationMethod(
            value = "$did#0",
            controller = did,
            kind = WalletHolderIdentifierKind.DID_VERIFICATION_METHOD,
            signingAlgorithm = signingAlgorithm,
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletHolderVerificationMethodRegistrar>())
class WalletUnitStoreHolderVerificationMethodRegistrar(
    private val walletUnitStore: WalletUnitStore,
) : WalletHolderVerificationMethodRegistrar {
    override suspend fun register(
        walletUnitId: String,
        keyAlias: String,
        method: WalletHolderVerificationMethod,
    ) = walletUnitStore.registerHolderVerificationMethod(walletUnitId, keyAlias, method).map { Unit }
}

/**
 * The sole holder-key creation path for OID4VCI. Private operations remain inside WSCA; this
 * service durably records only typed public resolution metadata in the wallet-unit profile.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletHolderKeyProvisioningService>())
class WalletHolderKeyProvisioningServiceImpl(
    private val walletUnitCryptoSurfaceProvider: Provider<Wsca>,
    private val walletUnitStore: WalletUnitStore,
    private val verificationMethodProvisioner: WalletHolderVerificationMethodProvisioner,
    private val verificationMethodRegistrar: WalletHolderVerificationMethodRegistrar,
    private val diagnostics: WalletInteractionDiagnostics,
) : WalletHolderKeyProvisioningService {
    constructor(
        walletUnitCryptoSurface: Wsca,
        walletUnitStore: WalletUnitStore,
        verificationMethodProvisioner: WalletHolderVerificationMethodProvisioner,
        verificationMethodRegistrar: WalletHolderVerificationMethodRegistrar,
        diagnostics: WalletInteractionDiagnostics,
    ) : this(Provider { walletUnitCryptoSurface }, walletUnitStore, verificationMethodProvisioner, verificationMethodRegistrar, diagnostics)

    private val walletUnitCryptoSurface: Wsca by lazy { walletUnitCryptoSurfaceProvider() }

    override suspend fun provision(
        walletUnitId: String,
        signingAlgorithm: SignatureAlgorithm,
    ): WalletAttestedKeyRef {
        walletUnitStore
            .resolveStorageProfile(walletUnitId)
            .getOrElse { error ->
                throw failure(STAGE_STORAGE_PROFILE, walletUnitId, "Wallet unit could not resolve its storage profile: ${error.code}", error.toDiagnosticDetails("error"))
            }
        val attestedKey =
            walletUnitCryptoSurface
                .createCredentialKey(
                    walletUnitId = walletUnitId,
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = signingAlgorithm,
                ).getOrElse { error ->
                    throw failure(STAGE_CREATE_KEY, walletUnitId, "Wallet unit could not provision the holder proof key: ${error.code}", error.toDiagnosticDetails("error"))
                }
        try {
            val keyAlias = attestedKey.keyRef ?: attestedKey.keyId
            require(keyAlias.isNotBlank()) { "WSCA returned a blank holder key reference" }
            val method = verificationMethodProvisioner.provision(walletUnitId, keyAlias, attestedKey, signingAlgorithm)
            verificationMethodRegistrar
                .register(walletUnitId, keyAlias, method)
                .getOrElse { error ->
                    throw IllegalStateException("Wallet unit could not register holder verification metadata: ${error.code}", error.exception)
                }
        } catch (cause: Exception) {
            diagnostics.warn(EVENT_FAILED, null, details(STAGE_VERIFICATION_METHOD, walletUnitId) + cause.toDiagnosticDetails("cause"))
            val discarded = walletUnitCryptoSurface.discardCredentialKey(walletUnitId, attestedKey)
            if (discarded.isErr) {
                throw failure(
                    STAGE_ROLLBACK,
                    walletUnitId,
                    "Wallet holder-key provisioning failed and WSCA rollback also failed: ${discarded.error.code}",
                    discarded.error.toDiagnosticDetails("rollback"),
                    cause,
                )
            }
            throw cause
        }
        return attestedKey
    }

    private fun failure(
        stage: String,
        walletUnitId: String,
        message: String,
        errorDetails: Map<String, String>,
        cause: Throwable? = null,
    ): IllegalStateException {
        diagnostics.warn(EVENT_FAILED, null, details(stage, walletUnitId) + errorDetails)
        return IllegalStateException(message, cause)
    }

    private fun details(
        stage: String,
        walletUnitId: String,
    ): Map<String, String> = mapOf("stage" to stage, "walletUnitId" to walletUnitId)

    private companion object {
        const val EVENT_FAILED = "holder_key.provision.failed"
        const val STAGE_STORAGE_PROFILE = "storage_profile"
        const val STAGE_CREATE_KEY = "create_key"
        const val STAGE_VERIFICATION_METHOD = "verification_method"
        const val STAGE_ROLLBACK = "rollback"
    }
}
