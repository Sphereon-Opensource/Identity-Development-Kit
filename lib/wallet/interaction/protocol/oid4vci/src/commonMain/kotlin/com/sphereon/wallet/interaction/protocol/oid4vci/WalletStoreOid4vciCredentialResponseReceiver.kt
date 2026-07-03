/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.compat.Uuid
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialClaimDisplay
import com.sphereon.wallet.credential.CredentialClaimMetadata
import com.sphereon.wallet.credential.CredentialDisplayMetadata
import com.sphereon.wallet.credential.CredentialDisplayProperties
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialImageProperties
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialLogoProperties
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityWindow
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.IssuanceProvenance
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import com.sphereon.openid.oid4vc.common.DisplayProperties as Oid4vcDisplayProperties
import com.sphereon.openid.oid4vc.common.ImageProperties as Oid4vcImageProperties
import com.sphereon.openid.oid4vc.common.LogoProperties as Oid4vcLogoProperties
import com.sphereon.openid.oid4vci.common.model.ClaimDisplay as Oid4vciClaimDisplay
import com.sphereon.openid.oid4vci.common.model.CredentialClaim as Oid4vciCredentialClaim

class WalletStoreOid4vciCredentialResponseReceiver(
    private val credentialStore: WalletCredentialStore,
) : Oid4vciCredentialResponseReceiver {
    override suspend fun receiveCredentialResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        credentialResponse: CredentialResponse,
    ): List<WalletCredentialPreview> {
        val credentialConfigurationId =
            context.privateValue("credential_configuration_id")
                ?: state.credentialOffer?.credentialConfigurationIds?.firstOrNull()
                ?: resolvedOffer.offer.credentialConfigurationIds.firstOrNull()
                ?: error("OID4VCI credential response cannot be stored without a credential configuration id")
        val credentialConfiguration =
            resolvedOffer.issuerMetadata.credentialConfigurationsSupported[credentialConfigurationId]
                ?: error("OID4VCI issuer metadata does not contain credential configuration '$credentialConfigurationId'")
        val credentialFormat =
            CredentialFormat.fromValueLenient(credentialConfiguration.format)
                ?: error("Unsupported OID4VCI credential format '${credentialConfiguration.format}'")
        val holderKeyAlias =
            context.privateValue("holder_key_alias")
                ?: error("OID4VCI credential response cannot be stored without a holder key alias")
        val responseItems =
            credentialResponse.credentials
                ?.takeIf { it.isNotEmpty() }
                ?: error("OID4VCI credential response contained no credentials")
        val issuerRef = resolvedOffer.offer.credentialIssuer.toIssuerRef()
        val typeRefs = credentialConfiguration.typeRefs(credentialFormat, credentialConfigurationId)
        require(typeRefs.isNotEmpty()) {
            "OID4VCI credential configuration '$credentialConfigurationId' produced no wallet credential type references"
        }

        val existingRecord = existingRecord(context.walletInstanceId, issuerRef, credentialConfigurationId)
        val now = Clock.System.now()
        val credentialRecordId = existingRecord?.id ?: Uuid.v4String()
        val newInstances =
            responseItems.map { item ->
                val raw =
                    (item.credential as? JsonPrimitive)?.content
                        ?: error("OID4VCI credential response item is not a primitive credential string")
                val credentialInstanceId = Uuid.v4String()
                CredentialInstance(
                    id = credentialInstanceId,
                    walletInstanceId = context.walletInstanceId,
                    credentialRecordId = credentialRecordId,
                    format = credentialFormat,
                    raw = raw,
                    bodyStorageRef =
                        BodyStorageRef(
                            kind = BodyStorageKind.WALLET_STORE,
                            path = credentialBodyPath(context.walletInstanceId, credentialRecordId, credentialInstanceId),
                        ),
                    holderKeyRef = KeyRef(alias = holderKeyAlias),
                    lifecycleState = CredentialLifecycleState.ACTIVE,
                    validity = CredentialValidityWindow(),
                    issuedAt = now,
                    storedAt = now,
                    updatedAt = now,
                )
            }

        val updatedRecord =
            if (existingRecord != null) {
                newInstances.fold(existingRecord.copy(credentialTypeRefs = existingRecord.credentialTypeRefs + typeRefs)) { record, instance ->
                    record.withAddedInstance(instance)
                }
            } else {
                CredentialRecord(
                    id = credentialRecordId,
                    walletInstanceId = context.walletInstanceId,
                    issuerRef = issuerRef,
                    format = credentialFormat,
                    credentialTypeRefs = typeRefs,
                    display =
                        CredentialDisplayMetadata(
                            issuerDisplay = resolvedOffer.issuerMetadata.display.toWalletDisplayProperties(),
                            credentialDisplay = credentialConfiguration.display.toWalletDisplayProperties(),
                            claims = credentialConfiguration.claims.toWalletClaimMetadata(),
                        ),
                    instances = newInstances,
                    issuanceProvenance =
                        IssuanceProvenance(
                            issuanceSessionId = context.sessionId.value,
                            credentialIssuerUrl = resolvedOffer.offer.credentialIssuer,
                            credentialConfigurationId = credentialConfigurationId,
                            expectedCredentialTypeRefs = typeRefs,
                            issuedAt = now,
                            notificationId = credentialResponse.notificationId,
                        ),
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val stored = credentialStore.putCredential(context.walletInstanceId, updatedRecord)
        if (stored.isErr) {
            error("Failed to store OID4VCI credential response: ${stored.error.message.defaultMessage}")
        }

        return listOf(
            WalletCredentialPreview(
                id = stored.value.id,
                name = stored.value.displayName() ?: credentialConfigurationId,
                format = credentialFormat.value,
                issuer =
                    WalletCounterpartySummary(
                        role = WalletCounterpartyRole.ISSUER,
                        identifier = resolvedOffer.offer.credentialIssuer,
                        displayName =
                            resolvedOffer.issuerMetadata.display
                                ?.firstOrNull()
                                ?.name ?: resolvedOffer.offer.credentialIssuer,
                    ),
            ),
        )
    }

    private suspend fun WalletInteractionContext.privateValue(name: String): String? =
        privateSessionStore
            .get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
            ?.values
            ?.get(name)
            ?.takeIf { it.isNotBlank() }

    private suspend fun existingRecord(
        walletInstanceId: String,
        issuerRef: IdentifierRef,
        credentialConfigurationId: String,
    ): CredentialRecord? {
        val matchingMetadata =
            credentialStore.listMetadata(
                walletInstanceId = walletInstanceId,
                filter =
                    CredentialMetadataFilter(
                        issuerRef = issuerRef,
                        credentialConfigurationId = credentialConfigurationId,
                    ),
            )
        if (matchingMetadata.isErr) {
            error("Failed to query existing OID4VCI wallet credentials: ${matchingMetadata.error.message.defaultMessage}")
        }
        val existingId = matchingMetadata.value.firstOrNull()?.credentialRecordId ?: return null
        val existing = credentialStore.getCredential(walletInstanceId, existingId)
        if (existing.isErr) {
            error("Failed to load existing OID4VCI wallet credential '$existingId': ${existing.error.message.defaultMessage}")
        }
        return existing.value
    }

    private fun String.toIssuerRef(): IdentifierRef =
        IdentifierRef(
            type = if (startsWith("did:")) IdentifierType.DID else IdentifierType("https"),
            value = this,
        )

    private fun CredentialConfigurationSupported.typeRefs(
        format: CredentialFormat,
        credentialConfigurationId: String,
    ): Set<CredentialTypeRef> =
        buildSet {
            when {
                format.isSdJwt -> {
                    vct?.takeIf { it.isNotBlank() }?.let {
                        add(
                            CredentialTypeRef(
                                format = format,
                                kind = CredentialTypeRefKind.SD_JWT_VCT,
                                value = it,
                                source = CredentialTypeRefSource.ISSUER_METADATA,
                                primary = true,
                            ),
                        )
                    }
                }

                format.isMdoc -> {
                    doctype?.takeIf { it.isNotBlank() }?.let {
                        add(
                            CredentialTypeRef(
                                format = format,
                                kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                                value = it,
                                source = CredentialTypeRefSource.ISSUER_METADATA,
                                primary = true,
                            ),
                        )
                    }
                }

                else -> {
                    addAll(w3cTypeRefs(format, credentialDefinition?.type.orEmpty()))
                }
            }
            if (isEmpty()) {
                add(
                    CredentialTypeRef(
                        format = format,
                        kind = CredentialTypeRefKind.W3C_VC_TYPE,
                        value = credentialConfigurationId,
                        source = CredentialTypeRefSource.ISSUER_METADATA,
                        primary = true,
                    ),
                )
            }
        }

    private fun w3cTypeRefs(
        format: CredentialFormat,
        types: List<String>,
    ): Set<CredentialTypeRef> {
        val cleanTypes = types.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val primaryType = cleanTypes.firstOrNull { it != "VerifiableCredential" } ?: cleanTypes.firstOrNull()
        return cleanTypes
            .map {
                CredentialTypeRef(
                    format = format,
                    kind = CredentialTypeRefKind.W3C_VC_TYPE,
                    value = it,
                    source = CredentialTypeRefSource.ISSUER_METADATA,
                    primary = it == primaryType,
                )
            }.toSet()
    }

    private fun List<Oid4vcDisplayProperties>?.toWalletDisplayProperties(): List<CredentialDisplayProperties> =
        orEmpty().map {
            CredentialDisplayProperties(
                name = it.name,
                locale = it.locale,
                logo = it.logo.toWalletLogoProperties(),
                description = it.description,
                backgroundColor = it.backgroundColor,
                backgroundImage = it.backgroundImage.toWalletImageProperties(),
                textColor = it.textColor,
            )
        }

    private fun Oid4vcLogoProperties?.toWalletLogoProperties(): CredentialLogoProperties? =
        this?.let {
            CredentialLogoProperties(
                uri = it.uri,
                altText = it.altText,
            )
        }

    private fun Oid4vcImageProperties?.toWalletImageProperties(): CredentialImageProperties? =
        this?.let {
            CredentialImageProperties(uri = it.uri)
        }

    private fun List<Oid4vciCredentialClaim>?.toWalletClaimMetadata(): List<CredentialClaimMetadata> =
        orEmpty().map {
            CredentialClaimMetadata(
                path = it.path,
                mandatory = it.mandatory,
                valueType = it.valueType,
                display = it.display.toWalletClaimDisplay(),
            )
        }

    private fun List<Oid4vciClaimDisplay>?.toWalletClaimDisplay(): List<CredentialClaimDisplay> =
        orEmpty().map {
            CredentialClaimDisplay(
                name = it.name,
                locale = it.locale,
            )
        }

    private fun credentialBodyPath(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
    ): String = "wallet-instances/$walletInstanceId/credentials/$credentialRecordId/instances/$credentialInstanceId/body"
}
