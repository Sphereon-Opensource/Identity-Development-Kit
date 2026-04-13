package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Declares the regulatory and compliance context of a command.
 *
 * IDK declares this as static metadata. EDK uses it for:
 * - Audit event enrichment: tag events with applicable frameworks
 * - Compliance routing: GDPR-tagged events to GDPR audit pipeline
 * - Art. 30 GDPR: generate record-of-processing from commands with [dataProcessingActivity] = true
 * - Consent enforcement: check required consents before execution
 * - Data retention: apply [retentionDays] to command outputs/side-effects
 * - DPIA tracking: flag commands requiring Data Protection Impact Assessment
 *
 * All values are defaults — EDK config can override per commandId pattern and per tenant.
 */
@Serializable
data class ComplianceProfile(
    /** Regulatory frameworks applicable to this command's operation. */
    val applicableFrameworks: Set<RegulatoryFramework> = emptySet(),
    /** Lawful basis under which this operation processes personal data.
     *  Empty = command does not process personal data. */
    val dataProcessingBasis: Set<DataProcessingBasis> = emptySet(),
    /** Consent types that must be obtained before executing this command.
     *  EDK checks consent state at runtime. Empty = no consent required. */
    val requiredConsents: Set<String> = emptySet(),
    /** Whether this command constitutes a data processing activity
     *  for Art. 30 GDPR record-of-processing generation. */
    val dataProcessingActivity: Boolean = false,
    /** Default data retention period in days for outputs/side-effects.
     *  Null = no retention requirement declared (inherits from config). */
    val retentionDays: Int? = null,
    /** Whether this command requires a Data Protection Impact Assessment
     *  (GDPR Art. 35) due to high-risk processing. */
    val requiresDpia: Boolean = false,
) {
    companion object {
        val NONE = ComplianceProfile()
    }
}

/**
 * Identifies a regulatory framework or standard. Extensible — modules can
 * define domain-specific frameworks by constructing new instances.
 */
@Serializable
@JvmInline
value class RegulatoryFramework(
    val value: String,
) {
    companion object {
        // EU regulations
        val GDPR = RegulatoryFramework("gdpr") // EU 2016/679 — General Data Protection Regulation
        val EIDAS = RegulatoryFramework("eidas") // EU 910/2014 — Electronic Identification and Trust Services
        val EIDAS2 = RegulatoryFramework("eidas2") // EU 2024/1183 — eIDAS 2.0 (EUDIW amendment)
        val NIS2 = RegulatoryFramework("nis2") // EU 2022/2555 — Network and Information Security
        val DORA = RegulatoryFramework("dora") // EU 2022/2554 — Digital Operational Resilience Act
        val EUDIW = RegulatoryFramework("eudiw") // EU Digital Identity Wallet regulation

        // International standards
        val ISO27001 = RegulatoryFramework("iso27001") // Information security management
        val ISO27701 = RegulatoryFramework("iso27701") // Privacy information management
        val SOC2 = RegulatoryFramework("soc2") // Trust service criteria

        // Sector-specific
        val PSD2 = RegulatoryFramework("psd2") // EU 2015/2366 — Payment Services Directive
        val AMLR = RegulatoryFramework("amlr") // Anti-money laundering regulation
    }
}

/**
 * Lawful basis for data processing. Primarily GDPR Art. 6(1) and Art. 9(2),
 * but extensible for other jurisdictions.
 *
 * Aligns with the existing `LegalBasis` value class in idv-public, using
 * structured dotted keys for unambiguous identification.
 */
@Serializable
@JvmInline
value class DataProcessingBasis(
    val value: String,
) {
    companion object {
        // GDPR Art. 6(1) — lawful basis for processing
        val CONSENT = DataProcessingBasis("gdpr.art6.1a.consent")
        val CONTRACT = DataProcessingBasis("gdpr.art6.1b.contract")
        val LEGAL_OBLIGATION = DataProcessingBasis("gdpr.art6.1c.legal_obligation")
        val VITAL_INTERESTS = DataProcessingBasis("gdpr.art6.1d.vital_interests")
        val PUBLIC_INTEREST = DataProcessingBasis("gdpr.art6.1e.public_interest")
        val LEGITIMATE_INTEREST = DataProcessingBasis("gdpr.art6.1f.legitimate_interest")

        // GDPR Art. 9(2) — exceptions for special category data
        val EXPLICIT_CONSENT = DataProcessingBasis("gdpr.art9.2a.explicit_consent")
        val EMPLOYMENT_LAW = DataProcessingBasis("gdpr.art9.2b.employment_law")
        val SUBSTANTIAL_PUBLIC_INTEREST = DataProcessingBasis("gdpr.art9.2g.substantial_public_interest")

        // Non-GDPR
        val AML_RECORD_KEEPING = DataProcessingBasis("amlr.art56.record_keeping")
    }
}

// ========== DSL ==========

fun complianceProfile(block: ComplianceProfileBuilder.() -> Unit): ComplianceProfile = ComplianceProfileBuilder().apply(block).build()

class ComplianceProfileBuilder {
    private val frameworks = mutableSetOf<RegulatoryFramework>()
    private val bases = mutableSetOf<DataProcessingBasis>()
    private val consents = mutableSetOf<String>()
    private var processingActivity = false
    private var retention: Int? = null
    private var dpia = false

    fun framework(f: RegulatoryFramework) {
        frameworks.add(f)
    }

    fun frameworks(f: Collection<RegulatoryFramework>) {
        frameworks.addAll(f)
    }

    fun processingBasis(b: DataProcessingBasis) {
        bases.add(b)
    }

    fun processingBases(b: Collection<DataProcessingBasis>) {
        bases.addAll(b)
    }

    fun requiresConsent(vararg consent: String) {
        consents.addAll(consent)
    }

    fun dataProcessingActivity() {
        processingActivity = true
    }

    fun retentionDays(days: Int) {
        retention = days
    }

    fun requiresDpia() {
        dpia = true
    }

    fun build() = ComplianceProfile(frameworks, bases, consents, processingActivity, retention, dpia)
}
