/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import kotlin.uuid.Uuid

internal data class CredentialRequestCorrelation(
    val protocolSessionId: String,
    val issuanceSession: IssuanceSession?,
)

/** Resolves protocol history identity without configuration-ID or subject inference. */
internal suspend fun resolveCredentialRequestCorrelation(
    requestedIdentifier: String?,
    tokenIdentifiers: List<String>?,
    tokenId: String?,
    sessionStore: CredentialIssuanceSessionStore,
    opaqueIdProvider: () -> String = { Uuid.random().toString() },
): IdkResult<CredentialRequestCorrelation, IdkError> {
    val authorizedIdentifiers = tokenIdentifiers.orEmpty()
    if (authorizedIdentifiers.isNotEmpty()) {
        val exactIdentifier =
            requestedIdentifier
                ?: return Err(
                    IdkError.fromString(
                        code = "invalid_credential_request",
                        message = "credential_identifier is required by token authorization_details",
                    ),
                )
        if (exactIdentifier !in authorizedIdentifiers) {
            return Err(unknownCredentialIdentifier(exactIdentifier))
        }
        val session = sessionStore.get(exactIdentifier).getOrElse { return Err(it) }
            ?: return Err(unknownCredentialIdentifier(exactIdentifier))
        return Ok(CredentialRequestCorrelation(protocolSessionId = session.sessionId, issuanceSession = session))
    }

    if (requestedIdentifier != null) {
        return Err(unknownCredentialIdentifier(requestedIdentifier))
    }
    val protocolSessionId =
        tokenId?.let { "oid4vci:token-jti:$it" }
            ?: "oid4vci:credential-request:${opaqueIdProvider()}"
    return Ok(CredentialRequestCorrelation(protocolSessionId = protocolSessionId, issuanceSession = null))
}

private fun unknownCredentialIdentifier(identifier: String): IdkError =
    IdkError.fromString(
        code = "unknown_credential_identifier",
        message = "Unknown credential_identifier: '$identifier'",
    )
