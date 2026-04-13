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
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.Serializable

/**
 * Arguments for the JWKS endpoint (empty — returns all public keys)
 */
class GetJwksArgs

/**
 * JWKS endpoint result
 */
@Serializable
data class JwksResult(
    val keys: List<Jwk>,
)

/**
 * Get JWKS command
 *
 * Returns the server's public signing key(s) for ID token and access token verification.
 * Always available (needed for JWT access token verification regardless of OIDC mode).
 */
interface GetJwksCommand : ServiceCommand<GetJwksArgs, JwksResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jwks.get"
    }
}
