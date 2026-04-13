package com.sphereon.oauth2.client.validation

import com.sphereon.oauth2.common.model.AuthorizationRequest
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

/**
 * Konform validator for OAuth 2.0 Authorization Request (RFC 6749)
 */
val validateAuthorizationRequest = Validation<AuthorizationRequest> {
    AuthorizationRequest::clientId {
        minLength(1) hint "client_id is required"
    }

    AuthorizationRequest::redirectUri ifPresent {
        pattern("https?://.*".toRegex()) hint "redirect_uri must be a valid URL"
    }

    AuthorizationRequest::responseType {
        minLength(1) hint "response_type is required"
    }

    AuthorizationRequest::state ifPresent {
        minLength(8) hint "state must be at least 8 characters"
    }

    AuthorizationRequest::codeChallengeMethod ifPresent {
        pattern("plain|S256".toRegex()) hint "code_challenge_method must be 'plain' or 'S256'"
    }

    // If code_challenge is present, code_challenge_method must be present
    run {
        constrain("code_challenge requires code_challenge_method") { req ->
            req.codeChallenge == null || req.codeChallengeMethod != null
        }
    }
}
