package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Arguments for fetching UserInfo from an OIDC Provider
 */
data class FetchUserInfoArgs(
    val accessToken: String,
    val userinfoEndpoint: String
)

/**
 * UserInfo response from the OIDC Provider
 */
@Serializable
data class FetchUserInfoResult(
    val sub: String,
    val claims: Map<String, JsonElement> = emptyMap()
)

/**
 * Fetch UserInfo command
 *
 * OpenID Connect Core 1.0 Section 5.3: UserInfo Endpoint
 *
 * Fetches claims about the authenticated End-User from the
 * UserInfo endpoint using a Bearer access token.
 */
interface FetchUserInfoCommand : ServiceCommand<FetchUserInfoArgs, FetchUserInfoResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.client.userinfo.fetch"
    }
}
