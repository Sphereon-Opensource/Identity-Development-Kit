/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.IdkResult
import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialTrustValidationMode", exact = true)
enum class CredentialTrustValidationMode {
    DEFAULT_ENFORCE,
    AUDIT,
    DISABLED,
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialIssuerRef", exact = true)
data class CredentialIssuerRef(
    val issuer: String? = null,
    val method: String? = null,
    val did: String? = null,
    val oidfedEntityId: String? = null,
    val kid: String? = null,
    val x5c: List<String> = emptyList(),
    /** The authenticated ISO 18013 document type when [method] is `x509` for `mso_mdoc`. */
    val docType: String? = null,
)

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialTrustValidation", exact = true)
data class CredentialTrustValidation(
    val enabled: Boolean,
    val trusted: Boolean? = null,
    val mode: CredentialTrustValidationMode = CredentialTrustValidationMode.DEFAULT_ENFORCE,
    val method: String? = null,
    val trustDomainIds: List<String> = emptyList(),
    val matchedTrustDomainId: String? = null,
    val matchedAnchorId: String? = null,
    val status: String? = null,
    val details: String? = null,
    val diagnostics: List<String> = emptyList(),
    val policyDigest: String? = null,
    val decisionDigest: String? = null,
    val scopedTrustMark: ScopedTrustMarkValidation? = null,
)

/** Facts retained from a trust decision for one credential candidate. */
@JsExportCompat
@Serializable
data class CredentialTrustDecision(
    val credentialQueryId: String,
    val presentationSha256: String,
    val issuer: CredentialIssuerRef? = null,
    val trust: CredentialTrustValidation,
)

/** Outcome of the scoped mark check. */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedTrustMarkOutcome", exact = true)
enum class ScopedTrustMarkOutcome {
    VERIFIED,
    REJECTED,
    UNAVAILABLE;

    companion object {
        fun fromValue(value: String): ScopedTrustMarkOutcome = entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown ScopedTrustMarkOutcome: $value")
    }
}

/** Scoped mark facts supplied by the trust authority, without the raw mark or credential. */
@JsExportCompat
@Serializable
data class ScopedTrustMarkValidation(
    val outcome: ScopedTrustMarkOutcome,
    val policyDigest: String,
    val issuer: String,
    val trustMarkType: String,
    val scopePolicyId: String,
    val scopePolicyDigest: String,
    val authorityDomainId: String,
    val authorityAnchorId: String,
    val checkedAtEpochMillis: Long,
    val reasonCode: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val credentialType: AttestationTypeKey? = null,
    val markIssuer: String? = null,
    val authorityEntityId: String? = null,
    val authorityAnchorVersion: Long? = null,
    val markDigest: String? = null,
    val criteriaReference: String? = null,
    val delegationIssuer: String? = null,
    val delegationDigest: String? = null,
    val markExpiresAtEpochMillis: Long? = null,
    val statusIssuedAtEpochMillis: Long? = null,
    val statusValidUntilEpochMillis: Long? = null,
    val issuerDecisionDigest: String? = null,
)

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpCredentialTrustValidationArgs", exact = true)
data class Oid4vpCredentialTrustValidationArgs(
    val verifierId: String? = null,
    val dcqlQueryId: String? = null,
    /**
     * Optional identifier of the verification template the authorization request was created
     * from. Threaded through so trust-domain resolution can apply TEMPLATE-scoped defaults.
     */
    val templateId: String? = null,
    val credentialQueryId: String,
    val format: String,
    val presentation: String,
    val issuer: CredentialIssuerRef? = null,
)

interface Oid4vpCredentialTrustValidator {
    suspend fun supports(args: Oid4vpCredentialTrustValidationArgs): Boolean = true

    suspend fun validate(args: Oid4vpCredentialTrustValidationArgs): IdkResult<CredentialTrustValidation, IdkError>
}

/**
 * Declares the `Set<Oid4vpCredentialTrustValidator>` multibinding as allow-empty so any SessionScope
 * graph resolves it even when no trust validator is on the classpath. Lives in this public/SPI module
 * so every consumer can inject the set without re-declaring it - mirrors
 * `CredentialStatusVerifierMultibinds` in lib-statuslist-public. A deployment that wants OID4VP
 * credential-trust enforcement contributes an `Oid4vpCredentialTrustValidator` (e.g. the EDK's
 * `Oid4vpTrustDomainCredentialTrustValidator`); otherwise the set stays empty and
 * `ValidateAuthorizationResponseCommandImpl` disables trust checking (fail-open by design only for
 * deployments that deliberately ship with none).
 */
@ContributesTo(SessionScope::class)
interface Oid4vpCredentialTrustValidatorMultibinds {
    @Multibinds(allowEmpty = true)
    fun oid4vpCredentialTrustValidators(): Set<Oid4vpCredentialTrustValidator>
}
