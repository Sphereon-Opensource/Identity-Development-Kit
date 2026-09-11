package com.sphereon.openid.oid4vp.verifier

import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat

/** Server-produced verification facts. Absence on historical sessions is not successful verification. */
@Serializable
@JsExportCompat
data class VerifiedCredentialEvidence(
    /** SHA-256 of the exact presentation bytes, encoded as unpadded base64url. */
    val presentationSha256: String,
    val verifiedAtEpochMillis: Long,
    val issuer: CredentialIssuerRef? = null,
    val trust: CredentialTrustValidation? = null,
    val status: VerifiedCredentialStatus,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val temporalFacts: VerifiedCredentialTemporalFacts? = null,
)

/** Deliberately omits status-list addresses and technical diagnostics. */
@Serializable
@JsExportCompat
data class VerifiedCredentialStatus(
    val evaluation: VerifiedCredentialStatusOutcome,
    val resolvedValues: List<Int> = emptyList(),
    val required: Boolean,
    val rejectOnUnresolvable: Boolean,
)

@Serializable
@JsExportCompat
enum class VerifiedCredentialStatusOutcome { ACCEPT, REJECT, SKIPPED }

/** Extracted only from authenticated credential envelopes after successful verification.
 * Null on the parent means unsupported or uninterpretable temporal facts; null fields here mean absent claims. */
@Serializable
@JsExportCompat
data class VerifiedCredentialTemporalFacts(
    val issuedAtEpochMillis: Long? = null,
    val notBeforeEpochMillis: Long? = null,
    val expiresAtEpochMillis: Long? = null,
)
