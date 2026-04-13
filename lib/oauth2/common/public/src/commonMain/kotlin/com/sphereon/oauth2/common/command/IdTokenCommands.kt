/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.ValidatedIdToken

/**
 * Arguments for validating an OpenID Connect ID Token
 *
 * @property idToken The ID Token JWT string
 * @property options Validation options (expected issuer, audience, nonce, etc.)
 */
data class ValidateIdTokenArgs(
    val idToken: String,
    val options: IdTokenValidationOptions
)

/**
 * Command to validate an OpenID Connect ID Token
 *
 * This command integrates with the JWT service for signature verification
 * and performs all OpenID Connect ID Token validation requirements.
 */
interface ValidateIdTokenCommand : ServiceCommand<ValidateIdTokenArgs, ValidatedIdToken> {
    override val commandId: String get() = COMMAND_ID
    companion object {
        const val COMMAND_ID = "oauth2.idtoken.validate"
    }
}
