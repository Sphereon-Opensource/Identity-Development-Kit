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

package com.sphereon.oauth2.server.authorization.command.userinfo

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse

/**
 * Args for [HandleUserInfoRequestCommand]. Carries the bearer access token extracted from the
 * `Authorization` header by the HTTP adapter.
 */
data class HandleUserInfoRequestArgs(
    val accessToken: String,
)

/**
 * Orchestration command for `GET/POST /userinfo` per OpenID Connect Core 1.0 §5.3. Delegates to
 * the lower-level [com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand] which
 * filters claims by granted scopes.
 */
interface HandleUserInfoRequestCommand : ServiceCommand<HandleUserInfoRequestArgs, UserInfoResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.userinfo.handle-userinfo-request"
    }
}
