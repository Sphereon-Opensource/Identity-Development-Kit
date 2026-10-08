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

package com.sphereon.oauth2.client

import com.sphereon.oauth2.client.validation.validateTokenRequest
import com.sphereon.oauth2.common.model.TokenRequest
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Validation tests for token exchange (RFC 8693) fields in TokenRequest
 */
class TokenExchangeValidationTest {
    companion object {
        private const val TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
        private const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
        private const val JWT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"
    }

    @Test
    fun validTokenExchangeRequestPassesValidation() {
        val request =
            TokenRequest(
                grantType = TOKEN_EXCHANGE_GRANT,
                subjectToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test",
                subjectTokenType = ACCESS_TOKEN_TYPE,
            )

        val result = validateTokenRequest(request)

        assertTrue(result is Valid, "Valid token exchange request should pass validation")
    }

    @Test
    fun tokenExchangeWithoutSubjectTokenFailsValidation() {
        val request =
            TokenRequest(
                grantType = TOKEN_EXCHANGE_GRANT,
                subjectTokenType = ACCESS_TOKEN_TYPE,
            )

        val result = validateTokenRequest(request)

        assertTrue(result is Invalid, "Token exchange without subject_token should fail")
        val errors = (result as Invalid).errors
        assertTrue(
            errors.any { it.message.contains("subject_token") },
            "Error should mention subject_token, but got: $errors",
        )
    }

    @Test
    fun tokenExchangeWithoutSubjectTokenTypeFailsValidation() {
        val request =
            TokenRequest(
                grantType = TOKEN_EXCHANGE_GRANT,
                subjectToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test",
            )

        val result = validateTokenRequest(request)

        assertTrue(result is Invalid, "Token exchange without subject_token_type should fail")
        val errors = (result as Invalid).errors
        assertTrue(
            errors.any { it.message.contains("subject_token_type") },
            "Error should mention subject_token_type, but got: $errors",
        )
    }

    @Test
    fun tokenExchangeWithActorTokenButNoActorTokenTypeFailsValidation() {
        val request =
            TokenRequest(
                grantType = TOKEN_EXCHANGE_GRANT,
                subjectToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test",
                subjectTokenType = ACCESS_TOKEN_TYPE,
                actorToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.actor",
            )

        val result = validateTokenRequest(request)

        assertTrue(result is Invalid, "actor_token without actor_token_type should fail")
        val errors = (result as Invalid).errors
        assertTrue(
            errors.any { it.message.contains("actor_token_type") },
            "Error should mention actor_token_type, but got: $errors",
        )
    }

    @Test
    fun tokenExchangeWithActorTokenAndActorTokenTypePassesValidation() {
        val request =
            TokenRequest(
                grantType = TOKEN_EXCHANGE_GRANT,
                subjectToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test",
                subjectTokenType = ACCESS_TOKEN_TYPE,
                actorToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.actor",
                actorTokenType = JWT_TOKEN_TYPE,
            )

        val result = validateTokenRequest(request)

        assertTrue(result is Valid, "Token exchange with both actor_token and actor_token_type should pass")
    }

    @Test
    fun nonTokenExchangeGrantDoesNotRequireStsFields() {
        val request =
            TokenRequest(
                grantType = "client_credentials",
            )

        val result = validateTokenRequest(request)

        assertTrue(result is Valid, "client_credentials grant should not require STS fields")
    }
}
