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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ConsentDecision
import com.sphereon.oauth2.server.authorization.storage.AuthorizationCodeStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val RANDOM_TOKEN_BYTES = 32

/**
 * Implementation of CreateAuthorizationCodeCommand
 *
 * Creates authorization codes according to RFC 6749 Section 4.1.2.
 *
 * Authorization codes are short-lived credentials that represent the resource owner's
 * authorization to access their protected resources.
 *
 * Code structure:
 * - Opaque random string (not JWT)
 * - Cryptographically secure random generation
 * - Sufficient entropy (256 bits recommended)
 * - Short-lived (typically 10 minutes max)
 * - Single-use (MUST be consumed atomically)
 *
 * The code is stored in AuthorizationCodeStorage with associated metadata including:
 * - Client ID binding
 * - Redirect URI binding
 * - PKCE challenge (if present)
 * - DPoP JKT (if present)
 * - Scope
 * - Subject (user ID)
 *
 * Security considerations:
 * - Codes MUST be single-use (RFC 6749 Section 10.5)
 * - Codes MUST be short-lived (typically 10 minutes)
 * - Codes MUST be bound to client_id
 * - Codes MUST be bound to redirect_uri
 * - PKCE binding MUST be preserved
 * - DPoP binding MUST be preserved
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationCodeCommandImpl", exact = true)
class CreateAuthorizationCodeCommandImpl(
    execution: SessionExecution,
    private val authorizationCodeStorage: AuthorizationCodeStorage,
    private val configProvider: OAuth2ServersConfigProvider,
) : TypedServiceCommandAdapter<CreateAuthorizationCodeArgs, StringResult>(
        commandId = CreateAuthorizationCodeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationCodeArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateAuthorizationCodeCommand {
    override val commandId: String get() = CreateAuthorizationCodeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationCodeArgs

    override suspend fun doExecute(
        args: CreateAuthorizationCodeArgs,
        applyDuring: (CreateAuthorizationCodeArgs) -> CreateAuthorizationCodeArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.session, applied.userId, applied.consent, applied.userClaims, applied.acr, applied.amr).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        session: AuthorizationSession,
        userId: String,
        consent: ConsentDecision,
        userClaims: Map<String, Any> = emptyMap(),
        acr: String? = null,
        amr: List<String>? = null,
    ): IdkResult<String, AuthorizationServerError> {
        val now = Clock.System.now()
        val effectiveLifetime = configProvider.serverConfig.authorizationCodeLifetimeSeconds
        val expiresAt = now + effectiveLifetime.seconds

        // Generate cryptographically secure random authorization code
        // 256 bits = 32 bytes, encoded as base64url
        val code = generateSecureCode()

        // Convert code challenge method from String to PkceMethod
        val pkceMethod =
            session.codeChallengeMethod?.let { method ->
                when (method.uppercase()) {
                    "PLAIN" -> PkceMethod.PLAIN
                    "S256" -> PkceMethod.S256
                    else -> null
                }
            }

        // Create authorization code data
        val codeData =
            AuthorizationCodeData(
                code = code,
                clientId = session.clientId,
                subject = userId,
                redirectUri = session.redirectUri,
                scope = consent.grantedScopes?.joinToString(" "),
                codeChallenge = session.codeChallenge,
                codeChallengeMethod = pkceMethod,
                dpopJkt = session.dpopJkt,
                nonce = session.nonce,
                authTime = session.authTime ?: now.epochSeconds,
                issuedAt = now,
                expiresAt = expiresAt,
                used = false,
                acr = acr,
                amr = amr,
                userClaims = userClaims,
                additionalData = session.additionalData,
            )

        // Store authorization code
        return authorizationCodeStorage
            .storeAuthorizationCode(code, codeData)
            .mapError { error ->
                AuthorizationServerError.ServerError(
                    details = "Failed to store authorization code: $error",
                    exception = null,
                )
            }.map { code }
    }

    /**
     * Generate a cryptographically secure random authorization code
     * 32 bytes (256 bits) of entropy, base64url encoded
     */
    private fun generateSecureCode(): String {
        val randomBytes = Random.Default.nextBytes(RANDOM_TOKEN_BYTES)
        return randomBytes.encodeToBase64Url()
    }
}
