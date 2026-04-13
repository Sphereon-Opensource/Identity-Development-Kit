package com.sphereon.oauth2.client.validation

import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.PkceMethod
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.pattern

/**
 * Konform validator for PKCE data (RFC 7636)
 *
 * Validates:
 * - Code verifier length (43-128 characters)
 * - Code verifier character set (unreserved characters)
 * - Code challenge length (43+ characters for S256, matches verifier for plain)
 * - Code challenge character set
 */
val validatePkceData = Validation<PkceData> {
    PkceData::codeVerifier {
        minLength(43) hint "code_verifier must be at least 43 characters (RFC 7636)"
        maxLength(128) hint "code_verifier must not exceed 128 characters (RFC 7636)"
        pattern("^[A-Za-z0-9._~-]+$") hint "code_verifier must use unreserved characters: [A-Z], [a-z], [0-9], '-', '.', '_', '~'"
    }

    PkceData::codeChallenge {
        minLength(43) hint "code_challenge must be at least 43 characters"
        maxLength(128) hint "code_challenge must not exceed 128 characters"
        pattern("^[A-Za-z0-9._~-]+$") hint "code_challenge must use unreserved characters or base64url encoding"
    }

    // For plain method, challenge should equal verifier
    constrain("For PLAIN method, code_challenge must equal code_verifier") { pkce ->
        pkce.codeChallengeMethod != PkceMethod.PLAIN || pkce.codeChallenge == pkce.codeVerifier
    }

    // For S256 method, challenge should be base64url(sha256(verifier))
    // Length check: SHA256 produces 32 bytes, base64url encodes to 43 characters
    constrain("For S256 method, code_challenge should be 43 characters (base64url of SHA256)") { pkce ->
        pkce.codeChallengeMethod != PkceMethod.S256 || pkce.codeChallenge.length == 43
    }
}
