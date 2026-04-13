package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Arguments for the UserInfo endpoint
 */
data class GetUserInfoArgs(
    val accessToken: String
)

/**
 * UserInfo endpoint response (OpenID Connect Core Section 5.3)
 */
@Serializable
data class UserInfoResponse(
    val sub: String,
    val claims: Map<String, JsonElement> = emptyMap()
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
