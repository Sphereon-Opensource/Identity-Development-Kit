/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isRequired
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.AuthorizationCodeStorage
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of VerifyAuthorizationCodeGrantCommand
 *
 * Verifies authorization_code grant requests according to RFC 6749 Section 4.1.3
 * with PKCE support (RFC 7636).
 *
 * Verification steps:
 * 1. Retrieve and consume authorization code (atomic operation)
 * 2. Verify code is not expired
 * 3. Verify client_id matches
 * 4. Verify redirect_uri matches (if present in original request)
 * 5. Verify PKCE code_verifier (if PKCE was used)
 * 6. Return verified grant data
 *
 * Security considerations:
 * - Authorization codes MUST be single-use (RFC 6749 Section 10.5)
 * - Codes MUST be short-lived (typically 10 minutes max)
 * - PKCE MUST be used for public clients (RFC 8252)
 * - DPoP binding is preserved if present
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyAuthorizationCodeGrantCommandImpl", exact = true)
class VerifyAuthorizationCodeGrantCommandImpl(
    execution: SessionExecution,
    private val authorizationCodeStorage: AuthorizationCodeStorage,
    private val clientRegistry: ClientRegistry,
    private val configProvider: OAuth2ServersConfigProvider
) : TypedServiceCommandAdapter<VerifyAuthorizationCodeGrantArgs, VerifiedAuthorizationCodeGrant>(
    commandId = VerifyAuthorizationCodeGrantCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyAuthorizationCodeGrantArgs>(),
    outputTypeToken = typeToken<VerifiedAuthorizationCodeGrant>(),
), VerifyAuthorizationCodeGrantCommand {

    override val commandId: String get() = VerifyAuthorizationCodeGrantCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyAuthorizationCodeGrantArgs

    override suspend fun doExecute(
        args: VerifyAuthorizationCodeGrantArgs,
        applyDuring: (VerifyAuthorizationCodeGrantArgs) -> VerifyAuthorizationCodeGrantArgs
    ): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.code, applied.redirectUri, applied.clientId, applied.codeVerifier).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        code: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String?
    ): IdkResult<VerifiedAuthorizationCodeGrant, AuthorizationServerError> {

        // Retrieve and consume authorization code (atomic operation)
        // This prevents replay attacks by ensuring the code can only be used once
        val codeData = authorizationCodeStorage.consumeAuthorizationCode(code).getOrElse { error ->
            return when (error) {
                is AuthorizationServerError.StorageError -> {
                    if (error.details.contains("already been used")) {
                        // RFC 6749 Section 10.5: If a code is used more than once,
                        // SHOULD revoke all tokens issued based on that code
                        Err(AuthorizationServerError.InvalidGrant(
                            details = "Authorization code has already been used",
                            exception = null
                        ))
                    } else {
                        Err(AuthorizationServerError.ServerError(
                            details = "Failed to retrieve authorization code: ${error.details}",
                            exception = null
                        ))
                    }
                }
                else -> Err(error)
            }
        }

        // Check if code was found
        if (codeData == null) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Invalid or expired authorization code",
                    exception = null
                )
            )
        }

        // Verify code is not expired
        val now = Clock.System.now()
        if (codeData.expiresAt < now) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Authorization code has expired",
                    exception = null
                )
            )
        }

        // Verify client_id matches
        if (codeData.clientId != clientId) {
            return Err(
                AuthorizationServerError.InvalidGrant(
                    details = "Authorization code was issued to a different client",
                    exception = null
                )
            )
        }

        // Verify redirect_uri matches (if present in original request)
        // RFC 6749 Section 4.1.3: redirect_uri REQUIRED if included in authorization request
        if (codeData.redirectUri.isNotBlank()) {
            if (redirectUri.isBlank()) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Missing required parameter: redirect_uri",
                        exception = null
                    )
                )
            }
            if (codeData.redirectUri != redirectUri) {
                return Err(
                    AuthorizationServerError.InvalidGrant(
                        details = "redirect_uri does not match authorization request",
                        exception = null
                    )
                )
            }
        }

        // Verify PKCE if code_challenge was present (RFC 7636)
        if (codeData.codeChallenge != null) {
            if (codeVerifier.isNullOrBlank()) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Missing required parameter: code_verifier (PKCE required)",
                        exception = null
                    )
                )
            }

            // Verify code_verifier format (RFC 7636 Section 4.1)
            // Must be 43-128 characters, A-Z, a-z, 0-9, -, ., _, ~
            if (codeVerifier.length !in 43..128) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "code_verifier must be 43-128 characters",
                        exception = null
                    )
                )
            }

            // Compute challenge from verifier
            val computedChallenge = when (codeData.codeChallengeMethod ?: PkceMethod.S256) {
                PkceMethod.PLAIN -> codeVerifier
                PkceMethod.S256 -> {
                    // BASE64URL(SHA256(ASCII(code_verifier)))
                    hash(codeVerifier.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
                }
            }

            // Verify challenge matches
            if (computedChallenge != codeData.codeChallenge) {
                return Err(
                    AuthorizationServerError.InvalidGrant(
                        details = "PKCE verification failed: code_verifier does not match code_challenge",
                        exception = null
                    )
                )
            }
        } else {
            // No PKCE was used - check if it's required for this client
            val client = clientRegistry.getClient(clientId).getOrElse { error ->
                return Err(
                    AuthorizationServerError.ServerError(
                        details = "Failed to retrieve client registration: $error",
                        exception = null
                    )
                )
            }

            if (client == null) {
                return Err(
                    AuthorizationServerError.InvalidClient(
                        details = "Client not found",
                        exception = null
                    )
                )
            }

            // Check server-level PKCE requirement from config
            val serverPkceRequired = configProvider.serverConfig.pkce.isRequired

            // RFC 8252: Public clients MUST use PKCE
            if (client.requirePkce || serverPkceRequired) {
                return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "PKCE is required for this client",
                        exception = null
                    )
                )
            }
        }

        // Return verified grant
        return Ok(
            VerifiedAuthorizationCodeGrant(
                codeData = codeData,
                subject = codeData.subject,
                clientId = codeData.clientId,
                scope = codeData.scope,
                dpopJkt = codeData.dpopJkt,
                userClaims = codeData.userClaims,
                additionalData = codeData.additionalData
            )
        )
    }
}
