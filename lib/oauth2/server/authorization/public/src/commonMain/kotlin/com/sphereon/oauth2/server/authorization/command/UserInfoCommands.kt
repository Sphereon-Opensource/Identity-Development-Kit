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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Arguments for the UserInfo endpoint
 */
data class GetUserInfoArgs(
    val accessToken: String,
)

/**
 * UserInfo endpoint response (OpenID Connect Core Section 5.3)
 */
@Serializable
data class UserInfoResponse(
    val sub: String,
    val claims: Map<String, JsonElement> = emptyMap(),
)

/**
 * Get UserInfo command
 *
 * OpenID Connect Core 1.0 Section 5.3: UserInfo Endpoint
 *
 * Returns claims about the authenticated End-User filtered by
 * the scopes granted in the access token.
 */
interface GetUserInfoCommand : ServiceCommand<GetUserInfoArgs, UserInfoResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.userinfo.get"
    }
}
