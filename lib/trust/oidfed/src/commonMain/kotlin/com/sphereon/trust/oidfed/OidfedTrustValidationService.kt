package com.sphereon.trust.oidfed

import com.sphereon.crypto.jose.jws.StrictCompactJws
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOIDFEntityIdOpts
import com.sphereon.trust.core.model.TrustChain
import com.sphereon.trust.core.model.TrustChainLinks
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.validation.AbstractTrustValidationService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

enum class OidfedTrustAnchorStatus { ACTIVE, DISABLED, REVOKED }

data class OidfedTrustAnchor(
    val entityIdentifier: String,
    val jwks: JsonObject,
    val validFromEpochSeconds: Long? = null,
    val validUntilEpochSeconds: Long? = null,
    val status: OidfedTrustAnchorStatus = OidfedTrustAnchorStatus.ACTIVE,
)

/** A fetched Entity Configuration; claims are absent until authenticated. */
data class OidfedEntityConfiguration(val compactJws: String)

/** A fetched Subordinate Statement, identified by superior and subject lookup. */
data class OidfedSubordinateStatement(val compactJws: String)

data class OidfedJoseHeader(val typ: String?, val alg: String, val kid: String?)

/** Claims returned only by an authenticated JOSE verifier. */
data class OidfedAuthenticatedClaims(
    val issuer: String,
    val subject: String,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val jwks: JsonObject,
    val authorityHints: List<String> = emptyList(),
    val audience: Set<String> = emptySet(),
    val trustMarks: Set<String> = emptySet(),
)

data class OidfedAuthenticatedStatement(val header: OidfedJoseHeader, val claims: OidfedAuthenticatedClaims)

private sealed interface OidfedAuthenticatedCheck {
    data class Valid(val statement: OidfedAuthenticatedStatement) : OidfedAuthenticatedCheck
    data class Invalid(val reason: String) : OidfedAuthenticatedCheck
}

interface OidfedEntityStatementSource {
    suspend fun entityConfiguration(entityIdentifier: String): OidfedEntityConfiguration?
    suspend fun subordinateStatement(superior: String, subject: String): OidfedSubordinateStatement?
}

fun interface OidfedTrustAnchorSource { suspend fun anchors(): List<OidfedTrustAnchor> }

/** Must parse and authenticate the compact JWS, returning claims bound to that exact JWS. */
fun interface OidfedJwsVerifier {
    suspend fun verify(compactJws: String, trustedJwks: JsonObject?): OidfedAuthenticatedStatement?
}

fun interface OidfedValidationClock { fun nowEpochSeconds(): Long }

data class OidfedValidationPolicy(
    val maxChainDepth: Int = 5,
    val maxAgeSeconds: Long = 1800,
    val clockSkewSeconds: Long = 0,
    val allowedAlgorithms: Set<String> = setOf("EdDSA", "ES256", "ES384", "ES512", "ES256K", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512"),
    val entityStatementType: String = "entity-statement+jwt",
)

/** Pure chain validation; HTTP, persistence, and Metro composition remain outside this phase. */
class OidfedTrustValidationService(
    private val source: OidfedEntityStatementSource,
    private val anchors: OidfedTrustAnchorSource,
    private val verifier: OidfedJwsVerifier,
    private val clock: OidfedValidationClock,
    private val policy: OidfedValidationPolicy = OidfedValidationPolicy(),
) : AbstractTrustValidationService("oidfed-chain", setOf(TrustContext.TYPE_OPENID_FEDERATION)) {
    override suspend fun doGetTrustAnchors() = emptyList<com.sphereon.trust.core.model.TrustAnchor>()

    override suspend fun doValidate(request: TrustValidationRequest): TrustValidationResult =
        try { validateInternal(request) } catch (_: Exception) { failure("resolution_or_verification_exception", clock.nowEpochSeconds()) }

    private suspend fun validateInternal(request: TrustValidationRequest): TrustValidationResult {
        val now = clock.nowEpochSeconds()
        val entity = request.context.parameters["entityIdentifier"] ?: return failure("entity_identifier_missing", now)
        if (!validIdentifier(entity)) return failure("entity_identifier_invalid", now)
        val identifier = request.identifier as? ExternalIdentifierOIDFEntityIdOpts
            ?: return failure("identifier_type_mismatch", now)
        if (identifier.identifier != entity) return failure("identifier_context_mismatch", now)
        if (policy.maxChainDepth <= 0 || policy.maxAgeSeconds < 0 || policy.clockSkewSeconds < 0 || policy.maxAgeSeconds > Long.MAX_VALUE - policy.clockSkewSeconds) return failure("validation_policy_invalid", now)
        val requestedDepth = request.context.parameters["maxChainDepth"]?.toIntOrNull() ?: policy.maxChainDepth
        if (requestedDepth <= 0 || requestedDepth > policy.maxChainDepth) return failure("max_chain_depth_exceeds_policy", now)
        val requiredMarks = request.context.parameters["requiredTrustMarks"]?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty()
        val configuredAnchors = anchors.anchors()
        val structurallyValidAnchors = configuredAnchors.filter { validIdentifier(it.entityIdentifier) }
        if (structurallyValidAnchors.isEmpty()) return failure("anchor_missing_or_invalid", now)
        val activeAnchors = structurallyValidAnchors.filter { it.isActive(now) }
        if (activeAnchors.isEmpty()) {
            if (structurallyValidAnchors.all { it.validFromEpochSeconds != null && now < it.validFromEpochSeconds }) return failure("anchor_not_yet_valid", now)
            if (structurallyValidAnchors.all { it.validUntilEpochSeconds != null && now >= it.validUntilEpochSeconds }) return failure("anchor_expired", now)
            return failure("anchor_missing_or_inactive", now)
        }
        val leafConfiguration = source.entityConfiguration(entity) ?: return failure("entity_configuration_missing", now)
        val leaf = when (val checked = authenticatedConfiguration(leafConfiguration, entity, now, null)) {
            is OidfedAuthenticatedCheck.Valid -> checked.statement
            is OidfedAuthenticatedCheck.Invalid -> return failure(checked.reason, now)
        }
        if (requiredMarks.isNotEmpty()) return failure("required_trust_mark_validation_unsupported", now)
        val selfAnchor = activeAnchors.firstOrNull { it.entityIdentifier == entity }
        if (selfAnchor != null) {
            if (leaf.claims.jwks != selfAnchor.jwks || leaf.claims.authorityHints.isNotEmpty()) return failure("root_configuration_invalid", now)
            return trusted(now, listOf(entity), selfAnchor)
        }

        val path = mutableListOf(entity)
        val visited = linkedSetOf(entity)
        var current = entity
        var currentConfiguration = leaf
        repeat(requestedDepth) {
            var issuer: String? = null
            for (candidate in currentConfiguration.claims.authorityHints) {
                if (validIdentifier(candidate) && candidate !in visited && source.subordinateStatement(candidate, current) != null) {
                    issuer = candidate
                    break
                }
            }
            val selectedIssuer = issuer ?: return failure("authority_hint_missing_or_unresolvable", now, path)
            if (!visited.add(selectedIssuer)) return failure("topology_loop", now, path)
            path += selectedIssuer
            val subordinate = source.subordinateStatement(selectedIssuer, current) ?: return failure("subordinate_statement_missing", now, path)
            val issuerConfiguration = source.entityConfiguration(selectedIssuer) ?: return failure("issuer_configuration_missing", now, path)
            val issuerAnchor = activeAnchors.firstOrNull { it.entityIdentifier == selectedIssuer }
            val issuerAuthenticated = when (val checked = authenticatedConfiguration(issuerConfiguration, selectedIssuer, now, issuerAnchor?.jwks)) {
                is OidfedAuthenticatedCheck.Valid -> checked.statement
                is OidfedAuthenticatedCheck.Invalid -> return failure(checked.reason, now, path)
            }
            val subordinateAuthenticated = when (val checked = authenticatedSubordinate(subordinate, selectedIssuer, current, issuerAuthenticated.claims.jwks, now)) {
                is OidfedAuthenticatedCheck.Valid -> checked.statement
                is OidfedAuthenticatedCheck.Invalid -> return failure(checked.reason, now, path)
            }
            if (subordinateAuthenticated.claims.issuer != selectedIssuer || subordinateAuthenticated.claims.subject != current) return failure("subordinate_link_mismatch", now, path)
            val anchor = activeAnchors.firstOrNull { it.entityIdentifier == selectedIssuer }
            if (anchor != null) {
                if (issuerAuthenticated.claims.jwks != anchor.jwks) return failure("wrong_anchor", now, path)
                if (issuerAuthenticated.claims.authorityHints.isNotEmpty()) return failure("root_authority_hints_invalid", now, path)
                return trusted(now, path, anchor)
            }
            current = selectedIssuer
            currentConfiguration = issuerAuthenticated
        }
        return failure("chain_depth_exceeded", now, path)
    }

    private suspend fun authenticatedConfiguration(configuration: OidfedEntityConfiguration, expectedEntity: String, now: Long, trustedJwks: JsonObject?): OidfedAuthenticatedCheck {
        val parsed = parseAndVerify(configuration.compactJws, trustedJwks) ?: return OidfedAuthenticatedCheck.Invalid("configuration_signature_invalid")
        val claims = parsed.claims
        if (!validateHeader(parsed.header, trustedJwks ?: claims.jwks)) return OidfedAuthenticatedCheck.Invalid("configuration_header_invalid")
        if (claims.issuer != expectedEntity || claims.subject != expectedEntity) return OidfedAuthenticatedCheck.Invalid("configuration_subject_mismatch")
        if (claims.audience.isNotEmpty()) return OidfedAuthenticatedCheck.Invalid("configuration_audience_rejected")
        if (!validIdentifier(claims.issuer) || !validIdentifier(claims.subject)) return OidfedAuthenticatedCheck.Invalid("configuration_identifier_invalid")
        validTimesReason(claims, now)?.let { return OidfedAuthenticatedCheck.Invalid(it) }
        if (!validJwks(claims.jwks)) return OidfedAuthenticatedCheck.Invalid("configuration_jwks_invalid")
        if (!validAuthorityHints(claims.authorityHints)) return OidfedAuthenticatedCheck.Invalid("configuration_authority_hints_invalid")
        if (trustedJwks != null && claims.jwks != trustedJwks) return OidfedAuthenticatedCheck.Invalid("configuration_anchor_key_mismatch")
        return OidfedAuthenticatedCheck.Valid(parsed)
    }

    private suspend fun authenticatedSubordinate(subordinate: OidfedSubordinateStatement, expectedIssuer: String, expectedSubject: String, issuerJwks: JsonObject, now: Long): OidfedAuthenticatedCheck {
        val parsed = parseAndVerify(subordinate.compactJws, issuerJwks) ?: return OidfedAuthenticatedCheck.Invalid("subordinate_signature_invalid")
        val claims = parsed.claims
        if (!validateHeader(parsed.header, issuerJwks)) return OidfedAuthenticatedCheck.Invalid("subordinate_header_invalid")
        if (claims.issuer != expectedIssuer || claims.subject != expectedSubject) return OidfedAuthenticatedCheck.Invalid("subordinate_link_mismatch")
        if (claims.authorityHints.isNotEmpty()) return OidfedAuthenticatedCheck.Invalid("subordinate_authority_hints_rejected")
        if (claims.audience.isNotEmpty()) return OidfedAuthenticatedCheck.Invalid("subordinate_audience_rejected")
        if (!validIdentifier(claims.issuer) || !validIdentifier(claims.subject)) return OidfedAuthenticatedCheck.Invalid("subordinate_identifier_invalid")
        validTimesReason(claims, now)?.let { return OidfedAuthenticatedCheck.Invalid(it) }
        if (!validJwks(claims.jwks)) return OidfedAuthenticatedCheck.Invalid("subordinate_jwks_invalid")
        return OidfedAuthenticatedCheck.Valid(parsed)
    }

    private suspend fun parseAndVerify(compactJws: String, trustedJwks: JsonObject?): OidfedAuthenticatedStatement? {
        if (StrictCompactJws.parse(compactJws).isErr) return null
        return verifier.verify(compactJws, trustedJwks)
    }

    private fun validateHeader(header: OidfedJoseHeader, verificationJwks: JsonObject): Boolean {
        if (header.typ != policy.entityStatementType || header.alg == "none" || header.alg !in policy.allowedAlgorithms || header.kid.isNullOrBlank()) return false
        val selectedKey = (verificationJwks["keys"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.firstOrNull { it.string("kid") == header.kid }
        return selectedKey?.string("alg")?.let { it == header.alg } ?: true
    }

    private fun validTimesReason(claims: OidfedAuthenticatedClaims, now: Long): String? {
        if (claims.issuedAtEpochSeconds > claims.expiresAtEpochSeconds) return "statement_time_order_invalid"
        if (claims.issuedAtEpochSeconds > now && differenceExceeds(claims.issuedAtEpochSeconds, now, policy.clockSkewSeconds)) return "statement_not_yet_valid"
        if (claims.expiresAtEpochSeconds <= now && (claims.expiresAtEpochSeconds == now && policy.clockSkewSeconds == 0L || differenceExceeds(now, claims.expiresAtEpochSeconds, policy.clockSkewSeconds))) return "statement_expired"
        if (claims.issuedAtEpochSeconds < now && differenceExceeds(now, claims.issuedAtEpochSeconds, policy.maxAgeSeconds + policy.clockSkewSeconds)) return "statement_stale"
        return null
    }

    private fun differenceExceeds(later: Long, earlier: Long, limit: Long): Boolean {
        if (later < earlier) return false
        if (later >= 0 && earlier < 0 && later > Long.MAX_VALUE + earlier) return true
        return later - earlier > limit
    }

    private fun validJwks(jwks: JsonObject): Boolean {
        val keys = jwks["keys"] as? JsonArray ?: return false
        val objects = keys.filterIsInstance<JsonObject>()
        val kids = objects.mapNotNull { it.string("kid") }
        return keys.isNotEmpty() && objects.size == keys.size && kids.size == kids.toSet().size && objects.all { key ->
            val shape = when (key.string("kty")) {
                "OKP" -> key.string("crv") != null && key.string("x")?.isNotBlank() == true
                "EC" -> key.string("crv") != null && key.string("x")?.isNotBlank() == true && key.string("y")?.isNotBlank() == true
                "RSA" -> key.string("n")?.isNotBlank() == true && key.string("e")?.isNotBlank() == true
                else -> false
            }
            shape && !key.string("kid").isNullOrBlank() && PRIVATE_FIELDS.none { it in key }
        }
    }

    private fun validAuthorityHints(hints: List<String>): Boolean = hints.size == hints.toSet().size && hints.all(::validIdentifier)

    private fun validIdentifier(value: String): Boolean =
        value == value.trim() && HTTPS_IDENTIFIER.matches(value)

    private fun trusted(now: Long, path: List<String>, anchor: OidfedTrustAnchor) = TrustValidationResult(true, TrustStatus.TRUSTED, validationPath = path, trustChain = TrustChain.fromOrderedIdentifiers(path, TrustChainLinks.VERIFIED), details = "oidfed_chain_verified:${anchor.entityIdentifier}", validatedAt = Instant.fromEpochSeconds(now))

    private fun failure(code: String, now: Long, path: List<String> = emptyList()) = TrustValidationResult(false, when (code) { "statement_expired", "statement_stale", "anchor_expired" -> TrustStatus.EXPIRED; "statement_not_yet_valid", "anchor_not_yet_valid" -> TrustStatus.NOT_YET_VALID; else -> TrustStatus.VALIDATION_ERROR }, validationPath = path, trustChain = if (path.isEmpty()) null else TrustChain.fromOrderedIdentifiers(path, TrustChainLinks.BROKEN), details = "oidfed_$code", validatedAt = Instant.fromEpochSeconds(now))

    private fun OidfedTrustAnchor.isActive(now: Long): Boolean = status == OidfedTrustAnchorStatus.ACTIVE && (validFromEpochSeconds == null || now >= validFromEpochSeconds) && (validUntilEpochSeconds == null || now < validUntilEpochSeconds)
    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private companion object {
        val HTTPS_IDENTIFIER = Regex("^https://[^/?#\\s:]+(?::[0-9]{1,5})?(?:/[^\\s]*)?$")
        val PRIVATE_FIELDS = setOf("d", "p", "q", "dp", "dq", "qi", "oth")
    }
}
