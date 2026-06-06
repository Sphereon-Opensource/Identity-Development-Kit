package com.sphereon.core.api.compliance

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Data-residency / territory restriction governing where retained personal data may be stored
 * and processed.
 *
 * Extensible value class — well-known restrictions are companion constants; deployments may
 * construct jurisdiction-specific values. Referenced by `AttributeRetentionPolicy.Retained`.
 */
@JsExportCompat
@Serializable
data class TerritoryRestriction(
    val value: String,
) {
    companion object {
        /** No territorial restriction on storage or processing. */
        val NONE = TerritoryRestriction("none")

        /** Data may only reside within the EU / EEA. */
        val EU_EEA = TerritoryRestriction("eu_eea")

        /** Data may only reside within the issuing nation. */
        val NATIONAL_ONLY = TerritoryRestriction("national_only")
    }
}
