/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.IdkResult
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
