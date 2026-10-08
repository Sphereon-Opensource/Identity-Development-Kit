/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.TrustContext

/**
 * Extracts entity information from trust validation artifacts.
 *
 * Each trust type (X.509, OIDFED, ETSI, DID) implements this interface
 * to extract contact, organization, and display info from its specific
 * source data (certificate DN fields, entity configurations, LoTE entries, etc.).
 *
 * Implementations are registered via `@ContributesIntoSet` and used by both
 * the trust validation enrichment flow and the standalone [DiscoverEntityInfoCommand].
 */
@JsExportCompat
interface EntityInfoExtractor {
    /** The trust context types this extractor supports */
    @JsExportIgnoreCompat
    val supportedContextTypes: Set<String>

    /** Whether this extractor supports the given context */
    fun supports(context: TrustContext): Boolean = context.type in supportedContextTypes

    /**
     * Extract entity info from trust validation artifacts.
     *
     * @param context The trust context
     * @param validationPath The resolved validation path (entity IDs, cert chain, etc.)
     * @param options Discovery options controlling depth and role filtering
     * @return List of discovered entities, ordered leaf-first
     */
    suspend fun extractEntityInfo(
        context: TrustContext,
        validationPath: List<String>,
        options: EntityDiscoveryOptions,
    ): List<DiscoveredEntityInfo>
}
