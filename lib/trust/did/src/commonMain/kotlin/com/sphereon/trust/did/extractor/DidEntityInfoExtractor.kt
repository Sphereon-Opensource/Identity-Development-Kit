/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.did.extractor

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.EntityRole
import com.sphereon.trust.core.model.LocalizedUri
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.TrustChainPosition
import com.sphereon.trust.core.model.TrustContext
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Extracts entity information from DID documents.
 *
 * DID documents provide minimal entity info: the DID itself and any
 * service endpoints. Controller information can indicate a trust chain.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(scope = SessionScope::class, binding = binding<EntityInfoExtractor>())
class DidEntityInfoExtractor : EntityInfoExtractor {
    override val supportedContextTypes: Set<String> = setOf(TrustContext.TYPE_DID)

    override suspend fun extractEntityInfo(
        context: TrustContext,
        validationPath: List<String>,
        options: EntityDiscoveryOptions,
    ): List<DiscoveredEntityInfo> {
        val did = context.parameters["did"] ?: validationPath.firstOrNull() ?: return emptyList()

        return listOf(
            DiscoveredEntityInfo(
                entityIdentifier = did,
                sourceType = TrustAnchorType.DID,
                chainPosition = TrustChainPosition(depth = 0, role = TrustChainNodeRole.LEAF),
                roles = listOf(EntityRole.GENERAL),
                trustAnchor = true,
            ),
        )
    }
}
