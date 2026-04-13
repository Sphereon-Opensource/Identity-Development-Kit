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

package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.client.model.PkceData
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for PKCE (Proof Key for Code Exchange) operations (RFC 7636)
 *
 * Provides functionality for creating and verifying PKCE challenge/verifier pairs
 * used in the OAuth 2.0 authorization code flow to prevent authorization code
 * interception attacks.
 *
 * This service follows the Command/Service pattern, delegating to command implementations
 * for testability and consistency with other IDK services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PkceService", exact = true)
@JsExportCompat
interface PkceService {
    suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError>

    suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError>

    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all PKCE commands
     */
    @JsExportIgnoreCompat
    interface Commands {
        val createPkce: CreatePkceCommand
        val verifyPkce: VerifyPkceCommand
    }
}
