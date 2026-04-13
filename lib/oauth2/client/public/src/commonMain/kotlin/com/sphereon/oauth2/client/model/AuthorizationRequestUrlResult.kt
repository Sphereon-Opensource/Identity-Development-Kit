package com.sphereon.oauth2.client.model

/**
 * Result of creating an authorization request URL
 *
 * @property authorizationRequestUrl The complete authorization URL to redirect to
 * @property pkceData PKCE data if PKCE was used (code_verifier needed for token exchange)
 * @property dpopNonce DPoP nonce if DPoP was used with PAR
 */
data class AuthorizationRequestUrlResult(
    val authorizationRequestUrl: String,
    val pkceData: PkceData? = null,
    val dpopNonce: String? = null
)
