/*
 * © 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Where a credential claim takes its value from, besides the offer and the issuance pipeline. */
enum class CredentialClaimSourceKind {
    /** One named claim that the upstream IdP released at the federated login. */
    FEDERATION_USERINFO,

    /** The upstream IdP subject of the federated login. */
    FEDERATION_SUBJECT,

    /** The upstream IdP issuer of the federated login. */
    FEDERATION_ISSUER,
}

/**
 * Source reference of one credential claim definition, configured as
 * `credentials.[<id>].claims.[<claim>].source`.
 *
 * Accepted values:
 * - `federation.userinfo.<name>`: the claim `<name>` from the federated login's identity claims
 * - `federation.subject`: the upstream subject
 * - `federation.issuer`: the upstream issuer
 *
 * Federated identity claims only reach a credential through such a reference. A claim without a
 * source, or a federated claim that no definition names, is never copied into the credential.
 */
data class CredentialClaimSource(
    val kind: CredentialClaimSourceKind,
    val claimName: String? = null,
) {
    /** The source value from [token], or null when the token has no federated login or lacks the claim. */
    fun resolve(token: ValidatedTokenContext): JsonElement? {
        val upstreamIssuer = token.upstreamIssuer ?: return null
        return when (kind) {
            CredentialClaimSourceKind.FEDERATION_USERINFO -> token.userinfoClaims?.get(requireNotNull(claimName))
            CredentialClaimSourceKind.FEDERATION_SUBJECT -> token.upstreamSubject?.let(::JsonPrimitive)
            CredentialClaimSourceKind.FEDERATION_ISSUER -> JsonPrimitive(upstreamIssuer)
        }
    }

    companion object {
        const val FEDERATION_USERINFO_PREFIX = "federation.userinfo."
        const val FEDERATION_SUBJECT = "federation.subject"
        const val FEDERATION_ISSUER = "federation.issuer"

        /** Parses a configured source value; null when the value is not a known source reference. */
        fun parse(value: String): CredentialClaimSource? {
            val trimmed = value.trim()
            return when {
                trimmed == FEDERATION_SUBJECT -> CredentialClaimSource(CredentialClaimSourceKind.FEDERATION_SUBJECT)
                trimmed == FEDERATION_ISSUER -> CredentialClaimSource(CredentialClaimSourceKind.FEDERATION_ISSUER)
                trimmed.startsWith(FEDERATION_USERINFO_PREFIX) ->
                    trimmed
                        .removePrefix(FEDERATION_USERINFO_PREFIX)
                        .takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
                        ?.let { CredentialClaimSource(CredentialClaimSourceKind.FEDERATION_USERINFO, it) }
                else -> null
            }
        }
    }
}

/**
 * Values for every claim definition of one credential configuration that names a source. Only the
 * named claims are returned, keyed by the credential claim name; unresolved sources are omitted.
 */
fun resolveCredentialClaimSources(
    sources: Map<String, CredentialClaimSource>,
    token: ValidatedTokenContext,
): Map<String, JsonElement> =
    buildMap {
        sources.forEach { (claimName, source) -> source.resolve(token)?.let { put(claimName, it) } }
    }
