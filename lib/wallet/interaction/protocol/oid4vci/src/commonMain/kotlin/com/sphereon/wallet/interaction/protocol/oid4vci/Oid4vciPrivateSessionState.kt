/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Single typed replacement for the OID4VCI holder issuance executor's formerly loose
 * private-session string keys (token_endpoint, authorization_*, iae_*, deferred_*,
 * notification_*, access_token, refresh_token, holder_key_alias(es),
 * credential_configuration_id, entry_point.raw, tx_code, authorization_callback).
 *
 * The whole object is serialized as ONE JSON value under the single "state" key of the
 * existing [WalletInteractionPrivateSessionData.values] map (namespace unchanged:
 * [Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID]). The store contract itself
 * (Map<String, String>) is untouched; only the shape of the data placed inside it changes.
 */
@Serializable
data class Oid4vciPrivateSessionState(
    val entryPointRaw: String? = null,
    val credentialConfigurationId: String? = null,
    val holderKeyAliases: List<String> = emptyList(),
    val txCode: String? = null,
    val authorizationCallback: String? = null,
    // Set only for a wallet-initiated credential-refresh session: the id of
    // the EXISTING credential record being reissued. The receiver reads this to target the exact
    // record by id (supersede semantics) instead of the issuer+credential-configuration heuristic
    // used for normal issuance top-up.
    val refreshTargetCredentialRecordId: String? = null,
    // authorization_* + AS issuer/token endpoint pre-exchange
    val authorization: AuthorizationLeg? = null,
    // iae_* family incl. pending flag
    val iae: IaeLeg? = null,
    // access/refresh token + post-exchange token_endpoint
    val tokens: TokenLeg? = null,
    // deferred endpoint + transaction id
    val deferred: DeferredLeg? = null,
    // notification endpoint + id
    val notification: NotificationLeg? = null,
) {
    @Serializable
    data class AuthorizationLeg(
        val authorizationServerIssuer: String,
        val tokenEndpoint: String,
        val codeVerifier: String? = null,
        val state: String? = null,
        val redirectUri: String? = null,
        val clientId: String? = null,
        val attestationChallenge: String? = null,
    )

    @Serializable
    data class IaeLeg(
        val authorizationServerIssuer: String? = null,
        val tokenEndpoint: String? = null,
        val iaeEndpoint: String? = null,
        val authSession: String? = null,
        val requestUri: String? = null,
        val redirectUri: String? = null,
        val clientId: String? = null,
        val codeVerifier: String? = null,
        val pending: Boolean = false,
    )

    @Serializable
    data class TokenLeg(
        val accessToken: String,
        val refreshToken: String? = null,
        val tokenEndpoint: String? = null,
    )

    @Serializable
    data class DeferredLeg(
        val deferredCredentialEndpoint: String,
        val transactionId: String,
    )

    @Serializable
    data class NotificationLeg(
        val notificationEndpoint: String,
        val notificationId: String,
    )
}

private const val OID4VCI_STATE_KEY: String = "state"

private val oid4vciStateJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

/**
 * Reads and decodes the single typed OID4VCI private session state blob for this context's
 * session, defaulting to an empty [Oid4vciPrivateSessionState] when no state has been stored
 * yet (or the stored value fails to decode).
 */
internal suspend fun WalletInteractionContext.oid4vciState(): Oid4vciPrivateSessionState {
    val raw =
        privateSessionStore
            .get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
            ?.values
            ?.get(OID4VCI_STATE_KEY)
            ?: return Oid4vciPrivateSessionState()
    return runCatching {
        oid4vciStateJson.decodeFromString(Oid4vciPrivateSessionState.serializer(), raw)
    }.getOrDefault(Oid4vciPrivateSessionState())
}

/**
 * Reads the current typed OID4VCI private session state, applies [transform], and persists the
 * result as the single "state" entry in the namespace's values map, preserving any other
 * (non-OID4VCI-state) entries already present under that namespace.
 */
internal suspend fun WalletInteractionContext.updateOid4vciState(
    transform: (Oid4vciPrivateSessionState) -> Oid4vciPrivateSessionState,
) {
    val existing =
        privateSessionStore
            .get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
            ?.values
            .orEmpty()
    val current =
        existing[OID4VCI_STATE_KEY]?.let { raw ->
            runCatching {
                oid4vciStateJson.decodeFromString(Oid4vciPrivateSessionState.serializer(), raw)
            }.getOrNull()
        } ?: Oid4vciPrivateSessionState()
    privateSessionStore.put(
        sessionId,
        WalletInteractionPrivateSessionData(
            namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
            values = existing + mapOf(OID4VCI_STATE_KEY to oid4vciStateJson.encodeToString(Oid4vciPrivateSessionState.serializer(), transform(current))),
        ),
    )
}
