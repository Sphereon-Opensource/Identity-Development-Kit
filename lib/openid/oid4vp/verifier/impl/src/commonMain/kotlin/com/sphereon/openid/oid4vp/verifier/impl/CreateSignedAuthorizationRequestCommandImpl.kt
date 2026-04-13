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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestCommandService
import com.sphereon.openid.oid4vp.verifier.SignedAuthorizationRequestResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreateSignedAuthorizationRequestCommand for OpenID4VP RP (Verifier).
 *
 * Creates signed authorization requests (JAR - RFC 9101) for OpenID4VP:
 * 1. Creates the authorization request with DCQL query
 * 2. Signs it as a JWT using the verifier's private key
 * 3. Returns both the request and the signed JAR token
 *
 * The signed JAR can then be:
 * - Included directly in the URI via the `request` parameter
 * - Pushed to a PAR endpoint and referenced via `request_uri`
 *
 * Reference: RFC 9101 - The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR)
 */
@Inject
@SingleIn(SessionScope::class)
class CreateSignedAuthorizationRequestCommandImpl(
    execution: SessionExecution,
    private val createAuthorizationRequestCommand: CreateAuthorizationRequestCommand,
    private val createSignedJarCommand: CreateSignedJarCommand,
) : TypedServiceCommandAdapter<CreateSignedAuthorizationRequestArgs, SignedAuthorizationRequestResult>(
        commandId = CreateSignedAuthorizationRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateSignedAuthorizationRequestArgs>(),
        outputTypeToken = typeToken<SignedAuthorizationRequestResult>(),
    ),
    CreateSignedAuthorizationRequestCommand,
    CreateSignedAuthorizationRequestCommandService {
    override val commandId: String get() = CreateSignedAuthorizationRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateSignedAuthorizationRequestArgs

    override suspend fun createSignedAuthorizationRequest(args: CreateSignedAuthorizationRequestArgs): IdkResult<SignedAuthorizationRequestResult, IdkError> = execute(args)

    override suspend fun doExecute(
        args: CreateSignedAuthorizationRequestArgs,
        applyDuring: (CreateSignedAuthorizationRequestArgs) -> CreateSignedAuthorizationRequestArgs,
    ): IdkResult<SignedAuthorizationRequestResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Creating signed authorization request (JAR)")

        // Step 1: Create the base authorization request using existing command
        val createdRequest =
            createAuthorizationRequestCommand
                .execute(processedArgs.requestArgs)
                .getOrElse { error ->
                    log.error("Failed to create authorization request: ${error.message.defaultMessage}")
                    return Err(error)
                }

        log.debug("Created base authorization request with session: ${createdRequest.sessionId}")

        // Step 2: Sign the request as a JAR using oauth2/client command
        val jarArgs =
            CreateSignedJarArgs(
                authorizationRequest = createdRequest.request,
                signingKey = processedArgs.signingKey,
                issuer = processedArgs.requestArgs.clientId, // client_id is the JWT issuer
                audience = processedArgs.audience,
                expirationSeconds = processedArgs.expirationSeconds,
            )

        val signedJar =
            createSignedJarCommand.execute(jarArgs).getOrElse { error ->
                log.error("Failed to sign authorization request as JAR: ${error.message.defaultMessage}")
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to create signed JAR: ${error.message.defaultMessage}",
                        exception = error.exception,
                    ),
                )
            }

        log.info("Created signed authorization request (JAR) for client_id: ${processedArgs.requestArgs.clientId}")

        return Ok(
            SignedAuthorizationRequestResult(
                request = createdRequest.request,
                signedJar = signedJar.value,
                sessionId = createdRequest.sessionId,
            ),
        )
    }
}
