package com.sphereon.oauth2.client.validation

import com.sphereon.oauth2.common.model.TokenErrorResponse
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import io.konform.validation.Validation
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

/**
 * Konform validator for OAuth 2.0 Token Response (RFC 6749 Section 5.1)
 *
 * Validates:
 * - access_token is present and non-empty
 * - token_type is present (typically "Bearer" or "DPoP")
 * - expires_in is positive if present
 */
val validateTokenResponse = Validation<TokenResponse> {
    TokenResponse::accessToken {
        minLength(1) hint "access_token must not be empty"
    }

    TokenResponse::tokenType {
        minLength(1) hint "token_type must not be empty"
    }

    TokenResponse::expiresIn ifPresent {
        constrain("expires_in must be non-negative") { it >= 0 }
    }

    TokenResponse::cNonceExpiresIn ifPresent {
        constrain("c_nonce_expires_in must be non-negative") { it >= 0 }
    }
}

/**
 * Konform validator for OAuth 2.0 Token Error Response (RFC 6749 Section 5.2)
 *
 * Validates:
 * - error is present and non-empty
 * - error follows RFC 6749 error code format
 */
val validateTokenErrorResponse = Validation<TokenErrorResponse> {
    TokenErrorResponse::error {
        minLength(1) hint "error must not be empty"
        pattern("^[a-z_]+$") hint "error must be a valid OAuth 2.0 error code (lowercase with underscores)"
    }
}

/**
 * Konform validator for OAuth 2.0 Token Request (RFC 6749)
 *
 * Validates grant-specific requirements:
 * - authorization_code: requires code, redirect_uri
 * - refresh_token: requires refresh_token
 * - client_credentials: requires client authentication
 * - pre-authorized_code: requires pre-authorized_code
 */
val validateTokenRequest = Validation<TokenRequest> {
    TokenRequest::grantType {
        minLength(1) hint "grant_type must not be empty"
    }

    // authorization_code grant validation
    constrain("For authorization_code grant, 'code' is required") { request ->
        request.grantType != "authorization_code" || !request.code.isNullOrBlank()
    }

    constrain("For authorization_code grant, 'redirect_uri' is required") { request ->
        request.grantType != "authorization_code" || !request.redirectUri.isNullOrBlank()
    }

    // refresh_token grant validation
    constrain("For refresh_token grant, 'refresh_token' is required") { request ->
        request.grantType != "refresh_token" || !request.refreshToken.isNullOrBlank()
    }

    // pre-authorized_code grant validation (OpenID4VCI)
    constrain("For pre-authorized_code grant, 'pre-authorized_code' is required") { request ->
        request.grantType != "urn:ietf:params:oauth:grant-type:pre-authorized_code" ||
            !request.preAuthorizedCode.isNullOrBlank()
    }

    // Token exchange validation (RFC 8693)
    constrain("For token-exchange grant, 'subject_token' is required") { request ->
        request.grantType != "urn:ietf:params:oauth:grant-type:token-exchange" ||
            !request.subjectToken.isNullOrBlank()
    }

    constrain("For token-exchange grant, 'subject_token_type' is required") { request ->
        request.grantType != "urn:ietf:params:oauth:grant-type:token-exchange" ||
            !request.subjectTokenType.isNullOrBlank()
    }

    constrain("When 'actor_token' is present, 'actor_token_type' is required") { request ->
        request.actorToken == null || !request.actorTokenType.isNullOrBlank()
    }

    // PKCE code_verifier validation (RFC 7636)
    TokenRequest::codeVerifier ifPresent {
        minLength(43) hint "code_verifier must be at least 43 characters (RFC 7636)"
        maxLength(128) hint "code_verifier must not exceed 128 characters (RFC 7636)"
        pattern("^[A-Za-z0-9._~-]+$") hint "code_verifier must use unreserved characters"
    }

    // Client assertion type validation (RFC 7521, RFC 7523)
    constrain("If client_assertion is provided, client_assertion_type is required") { request ->
        request.clientAssertion == null || !request.clientAssertionType.isNullOrBlank()
    }

    constrain("client_assertion_type must be a valid URN") { request ->
        val assertionType = request.clientAssertionType
        assertionType == null || assertionType.startsWith("urn:")
    }
}
