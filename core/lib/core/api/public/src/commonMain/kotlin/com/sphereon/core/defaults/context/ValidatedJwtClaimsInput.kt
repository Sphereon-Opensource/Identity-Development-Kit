/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.context

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.context.BasicSecuredDetails
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantInput
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Wraps a [JwtClaimsInput] with the assertion that the underlying bearer token
 * has been validated upstream (signature, `exp`, `iss`, `aud`, etc.).
 *
 * The Ktor `TenantResolutionPlugin` requires its `jwtClaimsInputFactory` to
 * return this type — a plain [JwtClaimsInput] is refused. The marker exists so
 * a deployment cannot accidentally wire a factory that decodes a token without
 * validating it: the type system makes the validation step explicit at the
 * factory's signature.
 *
 * Sanctioned construction:
 *  - Validators (e.g. the IDK `ktor-server-jwt-auth` plugin) parse and verify
 *    the token, then call [JwtClaimsInput.markValidated] on the resulting
 *    claims. The wrapper exposes the same [TenantInput] / [PrincipalInput]
 *    surface the resolver chain consumes, plus the inner [JwtClaimsInput] for
 *    callers that need the raw claims.
 *
 * The wrapper does NOT itself perform any cryptographic check. The contract
 * is documentation + type discipline — calling `markValidated()` on
 * unverified claims is a deployment-side bug equivalent to lying. The point
 * is that such a bug is now visible at the call site rather than buried in a
 * factory's body.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidatedJwtClaimsInput", exact = true)
class ValidatedJwtClaimsInput internal constructor(
    val claimsInput: JwtClaimsInput,
) : TenantInput by claimsInput,
    PrincipalInput by claimsInput {
    override fun toString(): String = "ValidatedJwtClaimsInput(claimKeys=${claimsInput.claims.keys})"
}

/**
 * Mark this [JwtClaimsInput] as the result of a verified token decode.
 *
 * Call ONLY after every authenticity check the deployment requires has
 * passed: signature against the issuer's JWKS, `iss` whitelist, `aud` match,
 * `exp` / `nbf` window, and any optional `at_hash` / `c_hash` checks. The
 * Ktor `TenantResolutionPlugin` rejects unverified `JwtClaimsInput` at its
 * factory boundary; this is the only sanctioned path to satisfy it.
 */
fun JwtClaimsInput.markValidated(): ValidatedJwtClaimsInput = ValidatedJwtClaimsInput(this)

/**
 * Project the validated bearer token into [SecuredTenantContextDetails] for the
 * session context, so commands can read the request's validated JWT via
 * `execution.sessionContext.context.secureDetails?.jwt`.
 *
 * Returns `null` when the input carries no raw token (claims-only inputs have no
 * credential to surface). `iss` / `iat` / `nbf` / `exp` are mirrored from the
 * validated claims; the upstream validator already enforced them, this is a
 * read-only projection (epoch seconds scaled to the milliseconds the
 * [SecuredTenantContextDetails] contract uses).
 */
fun ValidatedJwtClaimsInput.toSecuredDetails(): SecuredTenantContextDetails? {
    val jwt = claimsInput.rawToken ?: return null
    val claims = claimsInput.claims

    fun epochMillis(claim: String): Long? = (claims[claim] as? JsonPrimitive)?.content?.toLongOrNull()?.let { it * MILLIS_PER_SECOND }
    val issuer = (claims["iss"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val validFrom = epochMillis("nbf") ?: epochMillis("iat")
    val validUntil = epochMillis("exp")
    return if (issuer != null && validFrom != null && validUntil != null) {
        BasicSecuredDetails(jwt = jwt, iss = issuer, validFrom = validFrom, validUntil = validUntil)
    } else if (issuer != null) {
        BasicSecuredDetails(jwt = jwt, iss = issuer)
    } else {
        BasicSecuredDetails(jwt = jwt)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
