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

import com.sphereon.oauth2.common.model.AuthorizationErrorResponse
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.PushedAuthorizationResponse
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.minimum
import io.konform.validation.constraints.notBlank
import io.konform.validation.constraints.pattern

/**
 * URL pattern validator
 */
private val urlPattern = "https?://.*".toRegex()

/**
 * Konform validator for Authorization Response (success)
 *
 * Validates:
 * - code is present and non-empty
 */
val validateAuthorizationResponse =
    Validation<AuthorizationResponse> {
        AuthorizationResponse::code {
            notBlank() hint "code is required"
            minLength(1) hint "code must not be empty"
        }
    }

/**
 * Konform validator for Authorization Error Response
 *
 * Validates:
 * - error is present and non-empty
 * - error_uri is a valid URL if present
 */
val validateAuthorizationErrorResponse =
    Validation<AuthorizationErrorResponse> {
        AuthorizationErrorResponse::error {
            notBlank() hint "error is required"
        }

        AuthorizationErrorResponse::errorUri ifPresent {
            pattern(urlPattern) hint "error_uri must be a valid URL"
        }
    }

/**
 * Konform validator for Pushed Authorization Response
 *
 * Validates:
 * - request_uri is present and non-empty
 * - expires_in is positive
 */
val validatePushedAuthorizationResponse =
    Validation<PushedAuthorizationResponse> {
        PushedAuthorizationResponse::requestUri {
            notBlank() hint "request_uri is required"
        }

        PushedAuthorizationResponse::expiresIn {
            minimum(1) hint "expires_in must be positive"
        }
    }
