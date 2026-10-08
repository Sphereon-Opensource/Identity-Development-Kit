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

internal fun resolveCredentialConfigurationId(
    requestedConfigurationId: String?,
    requestedIdentifier: String?,
    identifierMappings: Map<String, String>,
    tokenConfigurationIds: List<String>,
): String? =
    requestedConfigurationId
        ?: requestedIdentifier?.let(identifierMappings::get)
        ?: tokenConfigurationIds.firstOrNull()

internal fun scopeAuthorizesCredentialConfiguration(
    tokenScope: String?,
    credentialScope: String?,
): Boolean =
    !credentialScope.isNullOrBlank() && credentialScope in tokenScope.orEmpty().split(' ').filter(String::isNotBlank)

internal fun tokenAuthorizesCredentialConfiguration(
    requestedConfigurationId: String,
    tokenConfigurationIds: List<String>,
    tokenScope: String?,
    credentialScope: String?,
): Boolean =
    requestedConfigurationId in tokenConfigurationIds ||
        scopeAuthorizesCredentialConfiguration(tokenScope, credentialScope)

/** Resolves protocol history identity without configuration-ID or subject inference. */
internal suspend fun resolveCredentialRequestCorrelation(
    requestedIdentifier: String?,
    tokenIdentifiers: List<String>?,
    tokenId: String?,
    sessionStore: CredentialIssuanceSessionStore,
    tokenIssuerState: String? = null,
    opaqueIdProvider: () -> String = { Uuid.random().toString() },
): IdkResult<CredentialRequestCorrelation, IdkError> {
    val authorizedIdentifiers = tokenIdentifiers.orEmpty()
    if (authorizedIdentifiers.isNotEmpty()) {
        // OID4VCI 1.0 requires a conforming wallet to use a credential_identifier when
        // authorization_details returned credential_identifiers. The issuer is deliberately
        // open-world at this boundary, however: wallets that send credential_configuration_id
        // are accepted later only when that exact configuration is authorized by the validated
        // token. If a wallet does send an identifier it must still be an exact advertised value.
        if (requestedIdentifier != null && requestedIdentifier !in authorizedIdentifiers) {
            return Err(unknownCredentialIdentifier(requestedIdentifier))
        }
        // credential_identifier is a wallet-visible authorization handle, not the issuance
        // session id. Offer/session correlation is carried separately in an internal token claim.
        if (tokenIssuerState != null) {
            val session = sessionStore.getByIssuerState(tokenIssuerState).getOrElse { return Err(it) }
                ?: return Err(
                    IdkError.fromString(
                        code = "invalid_token",
                        message = "Access token references an unknown issuance session",
                    ),
                )
            return Ok(CredentialRequestCorrelation(protocolSessionId = session.sessionId, issuanceSession = session))
        }
        val protocolSessionId =
            tokenId?.let { "oid4vci:token-jti:$it" }
                ?: "oid4vci:credential-request:${opaqueIdProvider()}"
        return Ok(CredentialRequestCorrelation(protocolSessionId = protocolSessionId, issuanceSession = null))
    }

    if (requestedIdentifier != null) {
        // The wallet only sends credential_identifier when the token response advertised
        // credential_identifiers, so an empty authorized list here means the identifiers were
        // lost between the AS and this issuer — introspection withholds authorization_details
        // from callers that are not registered internal clients of the AS
        // (`oauth2.servers.<asId>.internal-clients.*`).
        return Err(
            IdkError.fromString(
                code = "unknown_credential_identifier",
                message =
                    "credential_identifier '$requestedIdentifier' was sent but the validated token carries no " +
                        "authorization_details; if the token response advertised credential_identifiers, the issuer's " +
                        "introspection caller is not registered as an internal client of the authorization server",
            ),
        )
    }
    if (tokenIssuerState != null) {
        val session = sessionStore.getByIssuerState(tokenIssuerState).getOrElse { return Err(it) }
            ?: return Err(
                IdkError.fromString(
                    code = "invalid_token",
                    message = "Access token references an unknown issuance session",
                ),
            )
        return Ok(CredentialRequestCorrelation(protocolSessionId = session.sessionId, issuanceSession = session))
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
