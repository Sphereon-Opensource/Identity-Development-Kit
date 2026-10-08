package com.sphereon.core.api.compliance

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Lawful basis for processing / retaining personal data (GDPR Article 6(1), AML record-keeping,
 * ...).
 *
 * Extensible value class — well-known bases are companion constants. This is the canonical
 * definition; `idv-public` keeps a `typealias` to it so existing import paths keep working, and
 * the attribute-flow layer depends on it directly.
 *
 * Note: a separate `com.sphereon.data.semantic.attributes.LegalBasis` enum also exists in the
 * semantic-attributes module. The two are not yet reconciled — that consolidation is out of
 * scope here.
 */
@JsExportCompat
@Serializable
data class LegalBasis(
    val value: String,
) {
    companion object {
        val GDPR_ART6_1A_CONSENT = LegalBasis("gdpr_art6_1a_consent")
        val GDPR_ART6_1B_CONTRACT = LegalBasis("gdpr_art6_1b_contract")
        val GDPR_ART6_1C_LEGAL_OBLIGATION = LegalBasis("gdpr_art6_1c_legal_obligation")
        val GDPR_ART6_1F_LEGITIMATE_INTEREST = LegalBasis("gdpr_art6_1f_legitimate_interest")
        val AMLR_ART56_RECORD_KEEPING = LegalBasis("amlr_art56_record_keeping")
    }
}
