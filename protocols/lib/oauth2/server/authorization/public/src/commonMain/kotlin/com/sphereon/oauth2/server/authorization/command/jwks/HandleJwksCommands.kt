/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.command.jwks

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.command.JwksResult

/**
 * Args for [HandleJwksRequestCommand]. JWKS has no caller-supplied input, so this carries no
 * fields; the executor delegates to the lower-level `getJwks` command.
 */
class HandleJwksRequestArgs

/**
 * Orchestration command for `GET /.well-known/jwks.json`. Wraps the lower-level
 * [com.sphereon.oauth2.server.authorization.command.GetJwksCommand] so the HTTP adapter has a
 * single point of entry per endpoint, mirroring the OAuth2Handlers facade decomposition.
 */
interface HandleJwksRequestCommand : ServiceCommand<HandleJwksRequestArgs, JwksResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jwks.handle-jwks-request"
    }
}
