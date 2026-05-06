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

package com.sphereon.oauth2.server.authorization.impl.command.logout

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenArgs
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * IDK [CreateLogoutTokenCommand] implementation. Builds the JWT payload per OIDC
 * Back-Channel Logout 1.0 §2.4 and signs it through the same [JwtService] /
 * `oauth2.serverIdentifier` pair the AS uses for id_tokens, so RPs can verify
 * `logout_token` against the JWKS they already cache.
 *
 * Required claims: `iss`, `aud`, `iat`, `jti`, `events`, and at least one of `sub`
 * and `sid`. Forbidden claims: `nonce` (BC §2.4). Lifetime is short — a logout
 * token isn't meant to live across replay windows. The IDK omits `exp` because
 * BC §2.4 marks it as OPTIONAL and the spec relies on `jti` dedup at the RP.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateLogoutTokenCommand>())
class CreateLogoutTokenCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    @Named("oauth2.serverIdentifier") private val serverIdentifier: ManagedIdentifierOptsOrResult?,
    private val secureRandom: SecureRandom,
) : TypedServiceCommandAdapter<CreateLogoutTokenArgs, StringResult, IdkError>(
        commandId = CreateLogoutTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateLogoutTokenArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateLogoutTokenCommand {
    override val commandId: String get() = CreateLogoutTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateLogoutTokenArgs

    override suspend fun doExecute(
        args: CreateLogoutTokenArgs,
        applyDuring: (CreateLogoutTokenArgs) -> CreateLogoutTokenArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        if (serverIdentifier == null) {
            return Err(
                IdkError.fromDTO(
                    AuthorizationServerError.ServerError(
                        details = "Cannot mint logout_token: AS signing key not configured",
                    ),
                ),
            )
        }

        val now = Clock.System.now().epochSeconds
        val payload =
            buildJsonObject {
                put("iss", applied.issuer)
                put("aud", applied.clientId)
                put("iat", now)
                put("jti", secureRandom.newToken())
                put("sub", applied.sub)
                applied.sid?.let { put("sid", it) }
                // OIDC BC §2.4: events is a JSON object whose single key identifies the event.
                put(
                    "events",
                    buildJsonObject {
                        put(BACKCHANNEL_LOGOUT_EVENT, JsonObject(emptyMap()))
                    },
                )
            }

        val header =
            buildJsonObject {
                put("typ", LOGOUT_TOKEN_TYP)
            }

        val jwsArgs =
            CreateJwsArgs(
                issuer = serverIdentifier,
                payload = payload.toString(),
                opts =
                    CreateJwsOpts(
                        protectedHeader = header,
                        noIssPayloadUpdate = true,
                    ),
            )

        return jwtService
            .createJwsCompact(jwsArgs)
            .map { StringResult(it.jwt) }
            .mapError { error ->
                IdkError.fromDTO(
                    AuthorizationServerError.ServerError(
                        details = "Failed to sign logout_token: ${error.message.defaultMessage}",
                        exception = error.exception,
                    ),
                )
            }
    }

    private companion object {
        /**
         * OIDC Back-Channel Logout 1.0 §2.4 mandated `typ` value for the logout token JOSE header,
         * so RP middleware (e.g. `oidc-token-introspection-rp`) can dispatch on this header and
         * reject tokens that aren't shaped as logout tokens.
         */
        const val LOGOUT_TOKEN_TYP: String = "logout+jwt"

        /**
         * The logout-token `events` key per OIDC Back-Channel Logout 1.0 §2.4.
         */
        const val BACKCHANNEL_LOGOUT_EVENT: String = "http://schemas.openid.net/event/backchannel-logout"
    }
}
