package com.sphereon.core.api.service.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authentication assurance levels per NIST SP 800-63B.
 *
 * Maps 1:1 to eIDAS LoA:
 * - AAL1 → eIDAS Low
 * - AAL2 → eIDAS Substantial
 * - AAL3 → eIDAS High
 *
 * This is the canonical definition. Copies in idv-public and authzen-api
 * should be replaced with imports from this location.
 */
@Serializable
enum class AuthAssuranceLevel(
    val acr: String,
) {
    /** Single-factor authentication. eIDAS Low. */
    @SerialName("aal1")
    AAL1("urn:nist:sp:800-63:aal1"),

    /** Multi-factor authentication. eIDAS Substantial. */
    @SerialName("aal2")
    AAL2("urn:nist:sp:800-63:aal2"),

    /** Hardware-bound + phishing-resistant MFA. eIDAS High. */
    @SerialName("aal3")
    AAL3("urn:nist:sp:800-63:aal3"),
    ;

    /** Short serialized form (aal1, aal2, aal3) — used by IDV models */
    val serializedValue: String get() = name.lowercase()
}

/**
 * Declares the authentication assurance level required to execute a command.
 *
 * These are defaults — EDK config can override per commandId pattern and per tenant
 * (via CommandAssuranceConfig). Config takes precedence over code-declared values.
 *
 * Used by:
 * - Step-up authentication challenges (RFC 9470)
 * - Policy engine context enrichment (action.required_aal, action.max_auth_age)
 * - Risk classification for token introspection gating
 */
@Serializable
data class AssuranceRequirements(
    /** Minimum authentication assurance level. Null = unspecified. */
    val minimumAal: AuthAssuranceLevel? = null,
    /** Maximum seconds since last authentication (RFC 9470). Null = no freshness requirement. */
    val maxAuthAgeSecs: Long? = null,
    /** Required authentication method references (RFC 8176 amr values, e.g., "mfa", "hwk"). */
    val requiredAmr: Set<String> = emptySet(),
    /** NIST SP 800-57: requires multi-person authorization (ceremony-grade operations). */
    val requiresDualControl: Boolean = false,
) {
    companion object {
        val UNSPECIFIED = AssuranceRequirements()
    }
}

// ========== DSL ==========

fun assurance(block: AssuranceBuilder.() -> Unit): AssuranceRequirements = AssuranceBuilder().apply(block).build()

class AssuranceBuilder {
    private var aal: AuthAssuranceLevel? = null
    private var maxAuthAge: Long? = null
    private val amr = mutableSetOf<String>()
    private var dualControl = false

    fun aal1() {
        aal = AuthAssuranceLevel.AAL1
    }

    fun aal2() {
        aal = AuthAssuranceLevel.AAL2
    }

    fun aal3() {
        aal = AuthAssuranceLevel.AAL3
    }

    fun maxAuthAge(seconds: Long) {
        maxAuthAge = seconds
    }

    fun requireAmr(vararg methods: String) {
        amr.addAll(methods)
    }

    fun requireDualControl() {
        dualControl = true
    }

    fun build() = AssuranceRequirements(aal, maxAuthAge, amr, dualControl)
}
