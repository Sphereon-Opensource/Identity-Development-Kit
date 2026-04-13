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

package com.sphereon.oauth2.common.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.command.ValidateIdTokenArgs
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.model.ValidatedIdToken
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for OpenID Connect ID Token operations
 *
 * Provides ID Token validation for:
 * - OpenID Connect authentication flows
 * - OpenID4VP presentation exchange
 * - OpenID4VCI credential issuance
 *
 * Unlike pure OIDC implementations, this service is designed to work across
 * multiple OpenID-based protocols (OID4VP, OID4VCI) that use ID Tokens but
 * may not implement full OIDC Discovery.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IdTokenService", exact = true)
@JsExportCompat
interface IdTokenService {

    suspend fun validateIdToken(args: ValidateIdTokenArgs): IdkResult<ValidatedIdToken, IdkError>

    /**
     * Commands for direct command access
     *
     * Provides access to individual commands for advanced use cases
     * or custom flows that need more control.
     */
    val commands: Commands

    @JsExportIgnoreCompat
    interface Commands {
        val validateIdToken: ValidateIdTokenCommand
    }
}
