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

package com.sphereon.oauth2.client.impl.jar

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateEncryptedJarArgs
import com.sphereon.oauth2.client.command.CreateEncryptedJarCommand
import com.sphereon.oauth2.common.error.Oauth2Error
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of CreateEncryptedJarCommand
 *
 * Creates encrypted JAR (nested JWT) as defined in RFC 9101.
 *
 * This command:
 * 1. Takes a signed JAR (JWS) as input
 * 2. Encrypts it using the authorization server's public key
 * 3. Returns the compact JWE serialization
 *
 * The result is a nested JWT: JWE(JWS(authorization_request))
 */
@Inject
@SingleIn(SessionScope::class)
class CreateEncryptedJarCommandImpl(
    execution: SessionExecution,
    private val jweService: JweService,
) : TypedServiceCommandAdapter<CreateEncryptedJarArgs, StringResult>(
        commandId = CreateEncryptedJarCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateEncryptedJarArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateEncryptedJarCommand {
    override val commandId: String get() = CreateEncryptedJarCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateEncryptedJarArgs

    override suspend fun doExecute(
        args: CreateEncryptedJarArgs,
        applyDuring: (CreateEncryptedJarArgs) -> CreateEncryptedJarArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return createEncryptedJarInternal(applied).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun createEncryptedJarInternal(args: CreateEncryptedJarArgs): IdkResult<String, Oauth2Error> {
        try {
            // The signed JAR is the plaintext we want to encrypt
            val plaintext = args.signedJar.encodeToByteArray()

            // Prepare JWE with the recipient's public key
            val prepareArgs =
                PrepareJweArgs(
                    plaintext = plaintext,
                    recipient = ManagedOptsKeyInfo(identifier = args.recipientPublicKey),
                    keyEncryptionAlg = args.keyEncryptionAlgorithm,
                    contentEncryptionAlg = args.contentEncryptionAlgorithm,
                )

            val prepareResult =
                jweService.prepareJwe(prepareArgs).getOrElse { error ->
                    return Err(
                        Oauth2Error.JarCreationFailed(
                            failureMessage = "Failed to prepare JWE for JAR: ${error.message.defaultMessage}",
                            cause = error.exception,
                        ),
                    )
                }

            // Create compact JWE
            val createArgs =
                CreateJweCompactArgs(
                    preparedJwe = prepareResult,
                )

            val jweResult =
                jweService.createJweCompact(createArgs).getOrElse { error ->
                    return Err(
                        Oauth2Error.JarCreationFailed(
                            failureMessage = "Failed to encrypt JAR: ${error.message.defaultMessage}",
                            cause = error.exception,
                        ),
                    )
                }

            // Return the compact JWE serialization
            return Ok(jweResult.serialize())
        } catch (expected: Exception) {
            return Err(
                Oauth2Error.JarCreationFailed(
                    failureMessage = "Encrypted JAR creation failed: ${expected.message}",
                    cause = expected,
                ),
            )
        }
    }
}
