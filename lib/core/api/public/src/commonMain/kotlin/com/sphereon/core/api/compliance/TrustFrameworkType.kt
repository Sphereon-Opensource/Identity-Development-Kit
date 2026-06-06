package com.sphereon.core.api.compliance

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Regulatory framework governing an identity-proofing or data-retention decision
 * (eIDAS, NIST SP 800-63A, UK DIATF, German AML, ...).
 *
 * Extensible value class — well-known frameworks are companion constants; deployments may
 * construct their own. This is the canonical definition; `idv-public` keeps a `typealias`
 * to it so existing import paths keep working, and the attribute-flow layer depends on it
 * directly (it would otherwise create a dependency cycle through `idv-public`).
 */
@JsExportCompat
@Serializable
data class TrustFrameworkType(
    val value: String,
) {
    companion object {
        val EIDAS = TrustFrameworkType("eidas")
        val NIST_800_63A = TrustFrameworkType("nist_800_63A")
        val UK_DIATF = TrustFrameworkType("uk_diatf")
        val DE_AML = TrustFrameworkType("de_aml")
    }
}
