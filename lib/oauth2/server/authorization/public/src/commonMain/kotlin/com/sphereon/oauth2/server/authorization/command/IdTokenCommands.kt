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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.StringResult

/**
 * Arguments for creating an ID token
 */
data class CreateIdTokenArgs(
    val subject: String,
    val clientId: String,
    val nonce: String? = null,
    val authTime: Long? = null,
    val acr: String? = null,
    val amr: List<String>? = null,
    val accessToken: String? = null,
    val authorizationCode: String? = null,
    val userClaims: Map<String, Any> = emptyMap(),
    val additionalClaims: Map<String, Any> = emptyMap(),
)

/**
 * Create ID token command
 *
 * OpenID Connect Core 1.0 Section 2: ID Token
 *
 * Generates a signed ID Token JWT containing claims about the authentication
 * of an End-User. Includes at_hash and c_hash when applicable.
 */
interface CreateIdTokenCommand : ServiceCommand<CreateIdTokenArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.idtoken.create"
    }
}
