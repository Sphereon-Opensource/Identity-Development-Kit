/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.extractor

import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSITrustedEntity

/**
 * ETSI-specific entity mapping used by [com.sphereon.trust.etsi.validator.ETSITrustValidator].
 *
 * Separate from [com.sphereon.trust.core.EntityInfoExtractor] (multibinding set) so the
 * validator can depend on a single bound type under contribution providers.
 */
interface EtsiEntityMapper {
    fun mapEntity(
        entity: ETSITrustedEntity,
        territory: String,
        matchedServiceType: String?,
        depth: Int,
        nodeRole: TrustChainNodeRole,
    ): DiscoveredEntityInfo

    fun mapSchemeOperator(
        lote: ETSILoTE,
        depth: Int,
    ): DiscoveredEntityInfo
}
