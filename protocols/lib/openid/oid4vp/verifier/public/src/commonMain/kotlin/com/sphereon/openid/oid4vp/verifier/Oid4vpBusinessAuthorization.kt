package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.*
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/** Local RP business authorization. A valid proof is evidence, never an action permit. */
fun interface Oid4vpBusinessAuthorization {
    suspend fun authorize(session: AuthorizationSession, proof: ValidationResult): IdkResult<Unit, IdkError>
}

@ContributesTo(SessionScope::class)
interface Oid4vpBusinessAuthorizationBindings {
    @Multibinds(allowEmpty = true)
    fun businessAuthorizations(): Set<Oid4vpBusinessAuthorization>
}

suspend fun authorizeVerifierBusinessAction(
    required: Boolean,
    session: AuthorizationSession?,
    proof: ValidationResult,
    authorities: Set<Oid4vpBusinessAuthorization>,
): IdkResult<Unit, IdkError> {
    if (!proof.valid || !required) return Ok(Unit)
    val authority = authorities.singleOrNull()
        ?: return Err(IdkError.FORBIDDEN_ERROR(message = "verifier_business_authorization_missing_or_ambiguous"))
    if (session == null) return Err(IdkError.FORBIDDEN_ERROR(message = "verifier_business_session_missing"))
    return authority.authorize(session, proof)
}
