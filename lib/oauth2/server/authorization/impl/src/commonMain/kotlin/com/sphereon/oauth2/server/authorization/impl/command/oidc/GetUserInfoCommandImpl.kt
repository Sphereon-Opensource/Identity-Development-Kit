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
import com.sphereon.oauth2.server.authorization.model.SESSION_KEY_OIDC_CLAIMS_USERINFO
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Reserved OIDC / JWT claim names that MUST NOT be set from provider-sourced attributes.
 *
 * These claims have protocol-level meaning (issuer binding, audience, token lifetime,
 * authentication context) and allowing providers to override them via their attribute map
 * would let a compromised or misconfigured IdP inject claims into the UserInfo response
 * that downstream relying parties may trust. Drop silently at the server boundary.
 */
private val RESERVED_OIDC_CLAIMS =
    setOf(
        "sub",
        "iss",
        "aud",
        "exp",
        "iat",
        "auth_time",
        "nonce",
        "acr",
        "amr",
        "azp",
        "jti",
    )

/**
 * Implementation of [GetUserInfoCommand].
 *
 * Validates the access token, requires `openid` scope, looks up user claims, and filters
 * them by granted scopes. Emits a flat JSON object per OIDC Core 1.0 §5.3.2 (see
 * [UserInfoResponse]).
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetUserInfoCommandImpl", exact = true)
class GetUserInfoCommandImpl(
    execution: SessionExecution,
    private val tokenStorage: TokenStorage,
    private val userAuthenticationProvider: UserAuthenticationProvider,
    private val scopeClaimsMapper: OidcScopeClaimsMapper,
) : TypedServiceCommandAdapter<GetUserInfoArgs, UserInfoResponse, IdkError>(
        commandId = GetUserInfoCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetUserInfoArgs>(),
        outputTypeToken = typeToken<UserInfoResponse>(),
    ),
    GetUserInfoCommand {
    override val commandId: String get() = GetUserInfoCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetUserInfoArgs

    override suspend fun doExecute(
        args: GetUserInfoArgs,
        applyDuring: (GetUserInfoArgs) -> GetUserInfoArgs,
    ): IdkResult<UserInfoResponse, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: GetUserInfoArgs): IdkResult<UserInfoResponse, AuthorizationServerError> {
        val tokenData =
            tokenStorage
                .getAccessToken(args.accessToken)
                .mapError { AuthorizationServerError.ServerError(details = "Token lookup failed: $it", exception = null) }
                .getOrElse { return Err(it) }
                ?: return Err(AuthorizationServerError.InvalidRequest(details = "Invalid or unknown access token"))

        if (tokenData.expiresAt < Clock.System.now()) {
            return Err(AuthorizationServerError.InvalidGrant(details = "Access token expired"))
        }
        if (tokenData.revoked) {
            return Err(AuthorizationServerError.InvalidGrant(details = "Access token revoked"))
        }

        val scopes =
            tokenData.scope
                ?.split(" ")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
        if ("openid" !in scopes) {
            return Err(AuthorizationServerError.InvalidScope(scope = scopes.joinToString(" ")))
        }

        val userInfo =
            userAuthenticationProvider
                .getUserInfo(tokenData.subject)
                .mapError { AuthorizationServerError.ServerError(details = "User info lookup failed: ${it.message}", exception = null) }
                .getOrElse { return Err(it) }

        // Strip reserved OIDC claims from provider-sourced attributes BEFORE assembling the
        // canonical claim set, so a provider cannot overwrite server-owned values (e.g. `sub`).
        val safeAttributes = userInfo.attributes.filterKeys { it !in RESERVED_OIDC_CLAIMS }
        val droppedReserved = userInfo.attributes.keys - safeAttributes.keys
        if (droppedReserved.isNotEmpty()) {
            execution.log.debug(
                "Dropped reserved OIDC claims from provider attributes for subject=${tokenData.subject}: $droppedReserved",
            )
        }

        // Build the canonical claim set, with server-owned claims taking precedence over
        // provider attributes. Map<String, Any> is used only transiently so the existing
        // scope mapper API keeps working; the result is converted to JSON below.
        val allClaims =
            buildMap<String, Any> {
                putAll(safeAttributes)
                put("sub", userInfo.userId)
                userInfo.username?.let { put("preferred_username", it) }
                userInfo.displayName?.let { put("name", it) }
                userInfo.email?.let { put("email", it) }
                userInfo.emailVerified?.let { put("email_verified", it) }
                userInfo.phoneNumber?.let { put("phone_number", it) }
                userInfo.phoneNumberVerified?.let { put("phone_number_verified", it) }
            }

        // OIDC Core §5.5 — `claims.userinfo.<name>` request entries union with the scope-
        // derived allowed set. Names came from `claims` parsed at /authorize, threaded through
        // session → code → access token's additionalData under SESSION_KEY_OIDC_CLAIMS_USERINFO.
        val explicitUserinfoClaims =
            (tokenData.additionalData[SESSION_KEY_OIDC_CLAIMS_USERINFO] as? List<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
                .orEmpty()
        val scopeAllowed = scopeClaimsMapper.claimsForScopes(scopes)
        val effectiveAllowed = scopeAllowed + explicitUserinfoClaims
        val filteredClaims =
            if (effectiveAllowed.isEmpty()) {
                emptyMap()
            } else {
                allClaims.filterKeys { it in effectiveAllowed }
            }

        // Emit `sub` first for readability of the wire form; remaining standard claims follow.
        val jsonClaims =
            buildMap<String, JsonElement> {
                filteredClaims["sub"]?.let { put("sub", toJson(it)) }
                filteredClaims.forEach { (key, value) -> if (key != "sub") put(key, toJson(value)) }
            }

        return Ok(UserInfoResponse(JsonObject(jsonClaims)))
    }

    /**
     * Render a claim value to JSON. Strings get a try-parse-as-JSON pass first so values that
     * round-tripped through a string-typed config layer (`getPropertyAsString` etc.) restore
     * their original wire type — `"1714521600"` becomes the JSON number `1714521600`,
     * `"true"` becomes the JSON boolean, `"[\"a\",\"b\"]"` becomes a JSON array, and so on.
     * Anything that isn't valid JSON (the common case for names, URLs, locales) stays as a
     * JSON string. This keeps OIDC Core §5.1's typed claims (`updated_at`, `email_verified`,
     * `phone_number_verified`, `address`) right without enumerating them, and avoids
     * silently mistyping new operator-added custom claims.
     */
    private fun toJson(value: Any): JsonElement =
        when (value) {
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> tryParseJsonLiteral(value) ?: JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }

    /**
     * Returns the parsed [JsonElement] only when the string forms a structurally valid JSON
     * literal that's ALSO a number, boolean, array, or object. We deliberately reject the
     * `JsonNull` and `JsonPrimitive(isString=true)` cases so an attribute whose literal value
     * is `"null"` or a JSON-quoted string doesn't get re-typed in surprising ways — we only
     * promote when the operator clearly wrote a non-string JSON value into config.
     */
    private fun tryParseJsonLiteral(raw: String): JsonElement? {
        if (raw.isEmpty()) return null
        val first = raw.first()
        // Cheap pre-filter: only attempt parse when the first non-whitespace char looks like
        // a JSON literal start. Skips the parse cost for common name / URL / locale strings.
        val looksLikeJson = first.isDigit() || first == '-' || first == '{' || first == '[' || first == 't' || first == 'f'
        if (!looksLikeJson) return null
        return runCatching { Json.parseToJsonElement(raw) }.getOrNull()?.let { parsed ->
            when (parsed) {
                is JsonPrimitive -> if (parsed.isString) null else parsed
                is JsonObject, is JsonArray -> parsed
                else -> null
            }
        }
    }
}
