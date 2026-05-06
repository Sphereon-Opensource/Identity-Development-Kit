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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.storage.FederationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reserved standard-claim names projected to dedicated [UserInfo] fields. Anything else flows
 * through `attributes` so downstream consumers can opt into per-deployment OIDC claims without a
 * code change.
 */
private val STANDARD_USERINFO_CLAIM_KEYS =
    setOf(
        "sub",
        "preferred_username",
        "name",
        "email",
        "email_verified",
        "phone_number",
        "phone_number_verified",
    )

/**
 * Projects the cached upstream OIDC claim bag for a federated user into a typed [UserInfo].
 * Standard claim keys land on dedicated fields (with `toBooleanStrictOrNull` coercion for the
 * `*_verified` flags); every other claim flows through `attributes`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetUserInfoCommand>())
class GetUserInfoCommandImpl(
    execution: SessionExecution,
    private val sessionStore: FederationSessionStore,
) : TypedServiceCommandAdapter<GetUserInfoArgs, UserInfo, AuthenticationError>(
        commandId = GetUserInfoCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetUserInfoArgs>(),
        outputTypeToken = typeToken<UserInfo>(),
    ),
    GetUserInfoCommand {
    override val commandId: String get() = GetUserInfoCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetUserInfoArgs

    override suspend fun doExecute(
        args: GetUserInfoArgs,
        applyDuring: (GetUserInfoArgs) -> GetUserInfoArgs,
    ): IdkResult<UserInfo, AuthenticationError> {
        val applied = applyDuring(args)
        val lookup = sessionStore.retrieveCachedUserClaims(applied.userId)
        val cached =
            (if (lookup.isOk) lookup.value else null)
                ?: return Err(AuthenticationError.UserNotFound(description = "No cached claims for user: ${applied.userId}"))

        val claims = cached.claims
        return Ok(
            UserInfo(
                userId = cached.userId,
                username = (claims["preferred_username"] as? JsonPrimitive)?.contentOrNull,
                displayName = (claims["name"] as? JsonPrimitive)?.contentOrNull,
                email = (claims["email"] as? JsonPrimitive)?.contentOrNull,
                emailVerified = (claims["email_verified"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull(),
                phoneNumber = (claims["phone_number"] as? JsonPrimitive)?.contentOrNull,
                phoneNumberVerified = (claims["phone_number_verified"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull(),
                attributes = claims.filterKeys { it !in STANDARD_USERINFO_CLAIM_KEYS },
            ),
        )
    }
}
