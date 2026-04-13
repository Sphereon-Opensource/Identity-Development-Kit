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

import com.sphereon.oauth2.common.model.AuthorizationRequest
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

/**
 * Konform validator for OAuth 2.0 Authorization Request (RFC 6749)
 */
val validateAuthorizationRequest =
    Validation<AuthorizationRequest> {
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
