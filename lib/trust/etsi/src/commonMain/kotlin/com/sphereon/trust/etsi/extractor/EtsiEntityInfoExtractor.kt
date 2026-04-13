/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.extractor

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityAddress
import com.sphereon.trust.core.model.EntityContact
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.EntityRole
import com.sphereon.trust.core.model.EntityRoleMapping
import com.sphereon.trust.core.model.LocalizedString
import com.sphereon.trust.core.model.LocalizedUri
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.TrustChainPosition
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.inferContactType
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSITrustedEntity
import com.sphereon.trust.etsi.model.ETSITrustedEntityInformation
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Extracts entity information from ETSI LoTE trusted entities.
 *
 * Maps [ETSITrustedEntityInformation] to [DiscoveredEntityInfo] with:
 * - Multi-language names and trade names
 * - Postal and electronic addresses
 * - Information URIs
 * - Service type to entity role mapping
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(scope = SessionScope::class, binding = binding<EntityInfoExtractor>())
class EtsiEntityInfoExtractor : EntityInfoExtractor {
    override val supportedContextTypes: Set<String> = setOf(TrustContext.TYPE_ETSI_TSL)

    override suspend fun extractEntityInfo(
        context: TrustContext,
        validationPath: List<String>,
        options: EntityDiscoveryOptions,
    ): List<DiscoveredEntityInfo> {
        // validationPath from ETSI validator is [territory, tspName, serviceName]
        // We return info for the matched entity (leaf) only for now since
        // the ETSI validator does not store the full LoTE data in the path.
        // Full chain traversal would require re-loading the trust list.
        return emptyList()
    }

    /**
     * Maps an ETSI trusted entity to a [DiscoveredEntityInfo].
     * Called directly by [ETSITrustValidator] which has access to the full entity data.
     */
    fun mapEntity(
        entity: ETSITrustedEntity,
        territory: String,
        matchedServiceType: String?,
        depth: Int,
        nodeRole: TrustChainNodeRole,
    ): DiscoveredEntityInfo {
        val info = entity.trustedEntityInformation
        return DiscoveredEntityInfo(
            entityIdentifier = info.identifier ?: info.name.firstOrNull()?.value ?: "unknown",
            sourceType = TrustAnchorType.ETSI_TSL,
            chainPosition = TrustChainPosition(depth = depth, role = nodeRole),
            names = info.name.map { LocalizedString(lang = it.lang, value = it.value) },
            tradeNames = info.tradeName.map { LocalizedString(lang = it.lang, value = it.value) },
            contacts =
                info.address.electronicAddresses.map { addr ->
                    EntityContact(type = inferContactType(addr), value = addr)
                },
            addresses =
                info.address.postalAddresses.map { pa ->
                    EntityAddress(
                        streetAddress = pa.streetAddress,
                        locality = pa.locality,
                        stateOrProvince = pa.stateOrProvince,
                        postalCode = pa.postalCode,
                        countryName = pa.countryName,
                    )
                },
            informationUris = info.informationURI.map { LocalizedUri(lang = it.lang, uri = it.uriValue) },
            organizationName = info.name.firstOrNull()?.value,
            jurisdiction = territory,
            roles =
                buildList {
                    if (matchedServiceType != null) {
                        add(EntityRoleMapping.fromEtsiServiceType(matchedServiceType))
                    }
                },
        )
    }

    /**
     * Maps the scheme operator of a LoTE to a [DiscoveredEntityInfo] (trust anchor level).
     */
    fun mapSchemeOperator(
        lote: ETSILoTE,
        depth: Int,
    ): DiscoveredEntityInfo =
        DiscoveredEntityInfo(
            entityIdentifier = lote.schemeOperatorName.firstOrNull()?.value ?: lote.schemeTerritory,
            sourceType = TrustAnchorType.ETSI_TSL,
            chainPosition = TrustChainPosition(depth = depth, role = TrustChainNodeRole.TRUST_ANCHOR),
            names = lote.schemeOperatorName.map { LocalizedString(lang = it.lang, value = it.value) },
            contacts =
                lote.schemeOperatorAddress?.electronicAddresses?.map { addr ->
                    EntityContact(type = inferContactType(addr), value = addr)
                } ?: emptyList(),
            addresses =
                lote.schemeOperatorAddress?.postalAddresses?.map { pa ->
                    EntityAddress(
                        streetAddress = pa.streetAddress,
                        locality = pa.locality,
                        stateOrProvince = pa.stateOrProvince,
                        postalCode = pa.postalCode,
                        countryName = pa.countryName,
                    )
                } ?: emptyList(),
            informationUris = lote.schemeInformationURI.map { LocalizedUri(lang = it.lang, uri = it.uriValue) },
            organizationName = lote.schemeOperatorName.firstOrNull()?.value,
            jurisdiction = lote.schemeTerritory,
            roles = listOf(EntityRole.GENERAL),
        )
}
