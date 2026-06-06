package com.sphereon.core.api.service

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * eIDAS Level of Assurance.
 *
 * Maps 1:1 to NIST SP 800-63B [AuthAssuranceLevel]:
 * - Low → AAL1
 * - Substantial → AAL2
 * - High → AAL3
 *
 * This is the canonical definition. `idv-public` keeps a `typealias` to this location to
 * preserve existing import paths; the attribute-flow layer depends on it directly.
 */
@JsExportCompat
@Serializable
enum class EidasAssuranceLevel(
    val serializedValue: String,
) {
    @SerialName("low")
    LOW("low"),

    @SerialName("substantial")
    SUBSTANTIAL("substantial"),

    @SerialName("high")
    HIGH("high"),
}
