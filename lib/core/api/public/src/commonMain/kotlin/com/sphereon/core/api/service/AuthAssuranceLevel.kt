package com.sphereon.core.api.service

import com.sphereon.core.compat.JsExportCompat
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
@JsExportCompat
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
 * RFC 8176 Authentication Method Reference (amr) values.
 */
object Amr {
    const val MFA = "mfa"
    const val OTP = "otp"
    const val HWK = "hwk"
    const val SWK = "swk"
    const val PWD = "pwd"
    const val PIN = "pin"
    const val FACE = "face"
    const val FPT = "fpt"
    const val SMS = "sms"
    const val KBA = "kba"
}
