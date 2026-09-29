/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.token.exchange

import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.token.AnchoredActorToken
import com.sphereon.oauth2.server.authorization.command.token.AnchoredSubjectToken
import com.sphereon.oauth2.server.authorization.command.token.AuthorizeTokenExchangeInput
import com.sphereon.oauth2.server.authorization.command.token.BoundedTokenExchange
import com.sphereon.oauth2.server.authorization.command.token.TokenExchangeAuthorization
import com.sphereon.oauth2.server.authorization.command.token.TrustedIssuerRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Accumulated journey states. They are internal to this module, carry no serializer and are
// immutable: every collection is copied on construction and claim objects are deep copies.

/** RFC 8693 request parameters, still untrusted. */
internal class TokenExchangeParameters(
    val subjectToken: String,
    val subjectTokenType: String,
    val actorToken: String?,
    val actorTokenType: String?,
    resources: List<String>,
    audiences: List<String>,
    val scope: String?,
    val requestedTokenType: String?,
) {
    val resources: List<String> = resources.toList()
    val audiences: List<String> = audiences.toList()
}

/** Sender-constraint facts established when the token endpoint authenticated the request. */
internal class TokenExchangeSender(
    val proofJkt: String?,
    val certThumbprintS256: String?,
    val baseUrlOverride: String?,
)

/** Output of client authentication. */
internal class ClientAuthenticatedExchange(
    val requestedClientId: String,
    val clientAuthorization: VerifiedClientAuthorization?,
    val sender: TokenExchangeSender,
    val parameters: TokenExchangeParameters,
)

internal class ParsedExchange(
    val clientId: String,
    val client: VerifiedClientAuthorization,
    val sender: TokenExchangeSender,
    val parameters: TokenExchangeParameters,
    registeredTargets: Set<String>,
    effectiveRequestedTargets: List<String>,
    requestedAudiencesForAuthorization: List<String>,
) {
    val registeredTargets: Set<String> = registeredTargets.toSet()
    val effectiveRequestedTargets: List<String> = effectiveRequestedTargets.toList()
    val requestedAudiencesForAuthorization: List<String> = requestedAudiencesForAuthorization.toList()
}

/**
 * A token whose issuer resolved to anchored verification keys. The token itself is not yet
 * verified: [untrustedClaims] must not be used for any decision before signature verification.
 */
internal class IssuerResolvedToken(
    val role: String,
    val token: String,
    val tokenType: String,
    val issuer: String,
    val trustedIssuer: TrustedIssuerRef,
    val untrustedClaims: JsonObject,
    val trustedJwks: JsonObject,
)

internal class IssuerTrustResolvedExchange(
    val parsed: ParsedExchange,
    val subject: IssuerResolvedToken,
    val actor: IssuerResolvedToken?,
)

internal class SubjectVerifiedExchange(
    val trust: IssuerTrustResolvedExchange,
    val subject: AnchoredSubjectToken,
    val priorActor: ActorClaim?,
    val subjectCnfJkt: String?,
)

internal class PrincipalsVerifiedExchange(
    val parsed: ParsedExchange,
    val subject: AnchoredSubjectToken,
    val actor: AnchoredActorToken?,
    val priorActor: ActorClaim?,
    val subjectCnfJkt: String?,
)

internal class AuthorizedExchange(
    val principals: PrincipalsVerifiedExchange,
    val input: AuthorizeTokenExchangeInput,
    val authorization: TokenExchangeAuthorization,
)

internal class TargetBoundedExchange(
    val authorized: AuthorizedExchange,
    grantedTargets: List<String>,
    val bounded: BoundedTokenExchange,
) {
    val grantedTargets: List<String> = grantedTargets.toList()
}

internal class ClaimsMappedExchange(
    val bounded: TargetBoundedExchange,
    val additionalClaims: JsonObject,
)

/** Everything the mint step needs. */
internal class ExchangeGrant(
    val mapped: ClaimsMappedExchange,
    val subject: String,
    val clientId: String,
    val scope: String?,
    audience: List<String>,
    resources: List<String>,
    val isDelegation: Boolean,
    val actorSubject: String?,
    val actorClaim: ActorClaim?,
    val authTime: Long?,
    val acr: String?,
    amr: List<String>?,
    val subjectCnfJkt: String?,
    val sender: TokenExchangeSender,
) {
    val audience: List<String> = audience.toList()
    val resources: List<String> = resources.toList()
    val amr: List<String>? = amr?.toList()
}

internal class MintedExchange(
    val grant: ExchangeGrant,
    val accessToken: String,
    val boundJkt: String?,
)

internal fun deepCopy(element: JsonObject): JsonObject = JsonObject(element.mapValues { (_, value) -> deepCopy(value) })

internal fun deepCopy(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> deepCopy(element)
        is JsonArray -> JsonArray(element.map(::deepCopy))
        is JsonNull -> JsonNull
        is JsonPrimitive -> element
    }

/** A non-blank JSON string claim, compared without trimming. */
internal fun JsonObject.strictString(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)

/** A JSON number claim truncated to whole seconds; strings are not NumericDate values. */
internal fun JsonObject.numericDate(name: String): Long? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive.isString || primitive is JsonNull) return null
    return primitive.content.toLongOrNull() ?: primitive.content.toDoubleOrNull()?.toLong()
}

/** A string or array-of-strings claim. Null when any entry is not a non-blank string. */
internal fun JsonObject.stringValues(name: String): List<String>? =
    when (val value = this[name]) {
        is JsonPrimitive ->
            value.takeIf { it.isString && it.content.isNotBlank() }?.let { listOf(it.content) }
        is JsonArray ->
            value
                .mapNotNull { entry -> (entry as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content }
                .takeIf { it.size == value.size }
        else -> null
    }
