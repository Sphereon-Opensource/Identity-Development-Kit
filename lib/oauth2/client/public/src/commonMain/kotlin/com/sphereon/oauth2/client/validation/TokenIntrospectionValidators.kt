/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.client.validation

import com.sphereon.oauth2.common.model.JwtConfirmation
import com.sphereon.oauth2.common.model.TokenIntrospectionRequest
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength

/**
 * Validates a JWT confirmation claim.
 * RFC 7800 - Proof-of-Possession Key Semantics for JWTs
 */
val validateJwtConfirmation =
    Validation<JwtConfirmation> {
        // At least one of jwk or jkt must be present
        constrain("At least one of 'jwk' or 'jkt' must be present in confirmation claim") { cnf ->
            cnf.jwk != null || cnf.jkt != null
        }

        JwtConfirmation::jkt ifPresent {
            minLength(1) hint "jkt must not be empty"
        }
    }

/**
 * Validates a token introspection request.
 * RFC 7662 Section 2.1 - Introspection Request
 */
val validateTokenIntrospectionRequest =
    Validation<TokenIntrospectionRequest> {
        TokenIntrospectionRequest::token {
            minLength(1) hint "token is required and must not be empty"
        }

        TokenIntrospectionRequest::tokenTypeHint ifPresent {
            minLength(1) hint "token_type_hint must not be empty"
        }
    }

/**
 * Validates a token introspection response.
 * RFC 7662 Section 2.2 - Introspection Response
 *
 * Note: The 'active' field is the only required field in the response.
 * All other fields are optional and only present if the token is active.
 */
val validateTokenIntrospectionResponse =
    Validation<TokenIntrospectionResponse> {
        // 'active' is always required (boolean, no validation needed)

        // If token is active, validate optional fields
        TokenIntrospectionResponse::scope ifPresent {
            minLength(1) hint "scope must not be empty"
        }

        TokenIntrospectionResponse::clientId ifPresent {
            minLength(1) hint "client_id must not be empty"
        }

        TokenIntrospectionResponse::tokenType ifPresent {
            minLength(1) hint "token_type must not be empty"
        }

        TokenIntrospectionResponse::sub ifPresent {
            minLength(1) hint "sub must not be empty"
        }

        TokenIntrospectionResponse::iss ifPresent {
            minLength(1) hint "iss must not be empty"
        }

        TokenIntrospectionResponse::jti ifPresent {
            minLength(1) hint "jti must not be empty"
        }

        // Validate timestamps (if present, must be positive)
        TokenIntrospectionResponse::exp ifPresent {
            constrain("exp must be a positive timestamp") { it >= 0 }
        }

        TokenIntrospectionResponse::iat ifPresent {
            constrain("iat must be a positive timestamp") { it >= 0 }
        }

        TokenIntrospectionResponse::nbf ifPresent {
            constrain("nbf must be a positive timestamp") { it >= 0 }
        }

        // Validate aud (if present, must not be empty)
        TokenIntrospectionResponse::aud ifPresent {
            constrain("aud must contain at least one audience") { it.isNotEmpty() }
        }

        // Validate confirmation claim
        TokenIntrospectionResponse::cnf ifPresent {
            run(validateJwtConfirmation)
        }
    }
