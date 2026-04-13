package com.sphereon.oauth2.server.authorization.impl.command.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapper
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of GetUserInfoCommand
 *
 * Validates the access token, checks it includes the openid scope,
 * looks up user claims, and filters them by granted scopes.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetUserInfoCommandImpl", exact = true)
class GetUserInfoCommandImpl(
    execution: SessionExecution,
    private val tokenStorage: TokenStorage,
    private val userAuthenticationProvider: UserAuthenticationProvider,
    private val scopeClaimsMapper: OidcScopeClaimsMapper
) : TypedServiceCommandAdapter<GetUserInfoArgs, UserInfoResponse>(
    commandId = GetUserInfoCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<GetUserInfoArgs>(),
    outputTypeToken = typeToken<UserInfoResponse>(),
), GetUserInfoCommand {

    override val commandId: String get() = GetUserInfoCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetUserInfoArgs

    override suspend fun doExecute(
        args: GetUserInfoArgs,
        applyDuring: (GetUserInfoArgs) -> GetUserInfoArgs
    ): IdkResult<UserInfoResponse, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        args: GetUserInfoArgs
    ): IdkResult<UserInfoResponse, AuthorizationServerError> {
        // Look up and validate access token
        val tokenData = tokenStorage.getAccessToken(args.accessToken)
            .mapError { AuthorizationServerError.ServerError(details = "Token lookup failed: $it", exception = null) }
            .getOrElse { return Err(it) }
            ?: return Err(AuthorizationServerError.InvalidRequest(details = "Invalid or unknown access token"))

        // Check token not expired
        if (tokenData.expiresAt < Clock.System.now()) {
            return Err(AuthorizationServerError.InvalidGrant(details = "Access token expired"))
        }

        // Check token not revoked
        if (tokenData.revoked) {
            return Err(AuthorizationServerError.InvalidGrant(details = "Access token revoked"))
        }

        // Check scopes include openid
        val scopes = tokenData.scope?.split(" ")?.toSet() ?: emptySet()
        if ("openid" !in scopes) {
            return Err(AuthorizationServerError.InvalidScope(scope = scopes.joinToString(" ")))
        }

        // Get user info from provider
        val userInfo = userAuthenticationProvider.getUserInfo(tokenData.subject)
            .mapError { AuthorizationServerError.ServerError(details = "User info lookup failed: ${it.message}", exception = null) }
            .getOrElse { return Err(it) }

        // Build claims map from UserInfo
        val allClaims = buildMap<String, Any> {
            put("sub", userInfo.userId)
            userInfo.username?.let { put("preferred_username", it) }
            userInfo.displayName?.let { put("name", it) }
            userInfo.email?.let { put("email", it) }
            userInfo.emailVerified?.let { put("email_verified", it) }
            userInfo.phoneNumber?.let { put("phone_number", it) }
            userInfo.phoneNumberVerified?.let { put("phone_number_verified", it) }
            putAll(userInfo.attributes)
        }

        // Filter claims by granted scopes
        val filteredClaims = scopeClaimsMapper.filterClaims(allClaims, scopes)

        // Convert to JsonElement map
        val jsonClaims = filteredClaims.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }

        return Ok(
            UserInfoResponse(
                sub = userInfo.userId,
                claims = jsonClaims
            )
        )
    }
}
