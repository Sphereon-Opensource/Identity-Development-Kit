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

package com.sphereon.sdjwt.vc.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.sdjwt.SdJwtVerificationResult
import com.sphereon.sdjwt.VerifySdJwtArgs
import com.sphereon.sdjwt.command.VerifySdJwtCommand
import com.sphereon.sdjwt.vc.SdJwtVcPresentationVerificationResult
import com.sphereon.sdjwt.vc.SdJwtVcVerificationError
import com.sphereon.sdjwt.vc.SdJwtVcVerificationResult
import com.sphereon.sdjwt.vc.SdJwtVcVerifierImpl
import com.sphereon.sdjwt.vc.VerifySdJwtVcArgs
import com.sphereon.sdjwt.vc.VerifySdJwtVcPresentationArgs
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifySdJwtVcCommandImpl", exact = true)
class VerifySdJwtVcCommandImpl(
    execution: SessionExecution,
    private val verifySdJwtCommand: VerifySdJwtCommand,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<VerifySdJwtVcArgs, SdJwtVcVerificationResult, IdkError>(
        commandId = VerifySdJwtVcCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifySdJwtVcArgs>(),
        outputTypeToken = typeToken<SdJwtVcVerificationResult>(),
    ),
    VerifySdJwtVcCommand {
    override val commandId: String get() = VerifySdJwtVcCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifySdJwtVcArgs

    override suspend fun doExecute(
        args: VerifySdJwtVcArgs,
        applyDuring: (VerifySdJwtVcArgs) -> VerifySdJwtVcArgs,
    ): IdkResult<SdJwtVcVerificationResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val baseVerifier: suspend (VerifySdJwtArgs) -> IdkResult<SdJwtVerificationResult, IdkError> = { baseArgs ->
            verifySdJwtCommand.execute(baseArgs)
        }
        val verifier = SdJwtVcVerifierImpl(baseVerifier, httpClientFactory.createClient(HttpClientOptions.createDefault()))
        return verifier.verify(appliedArgs.sdJwt, appliedArgs.opts).mapError { error ->
            IdkError.fromString(
                message =
                    "SD_JWT_VC_VERIFICATION_FAILED: " +
                        when (error) {
                            is SdJwtVcVerificationError.InvalidVct -> error.message
                            is SdJwtVcVerificationError.InvalidTypeHeader -> error.message
                            is SdJwtVcVerificationError.TypeMetadataResolutionFailed -> "Type metadata resolution failed: ${error.error}"
                            is SdJwtVcVerificationError.TypeMetadataValidationFailed -> "Type metadata validation failed: ${error.errors.size} errors"
                            is SdJwtVcVerificationError.IssuerMetadataResolutionFailed -> "Issuer metadata resolution failed: ${error.error}"
                            is SdJwtVcVerificationError.StatusCheckFailed -> "Status check failed: ${error.message}"
                            is SdJwtVcVerificationError.SdJwtVerificationFailed -> "SD-JWT verification failed: ${error.message}"
                        },
            )
        }
    }
}

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifySdJwtVcPresentationCommandImpl", exact = true)
class VerifySdJwtVcPresentationCommandImpl(
    execution: SessionExecution,
    private val verifySdJwtCommand: VerifySdJwtCommand,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<VerifySdJwtVcPresentationArgs, SdJwtVcPresentationVerificationResult, IdkError>(
        commandId = VerifySdJwtVcPresentationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifySdJwtVcPresentationArgs>(),
        outputTypeToken = typeToken<SdJwtVcPresentationVerificationResult>(),
    ),
    VerifySdJwtVcPresentationCommand {
    override val commandId: String get() = VerifySdJwtVcPresentationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifySdJwtVcPresentationArgs

    override suspend fun doExecute(
        args: VerifySdJwtVcPresentationArgs,
        applyDuring: (VerifySdJwtVcPresentationArgs) -> VerifySdJwtVcPresentationArgs,
    ): IdkResult<SdJwtVcPresentationVerificationResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val baseVerifier: suspend (VerifySdJwtArgs) -> IdkResult<SdJwtVerificationResult, IdkError> = { baseArgs ->
            verifySdJwtCommand.execute(baseArgs)
        }
        val verifier = SdJwtVcVerifierImpl(baseVerifier, httpClientFactory.createClient(HttpClientOptions.createDefault()))
        return verifier.verifyPresentation(appliedArgs.sdJwt, appliedArgs.expectedNonce, appliedArgs.opts).mapError { error ->
            IdkError.fromString(
                message =
                    "SD_JWT_VC_PRESENTATION_VERIFICATION_FAILED: " +
                        when (error) {
                            is SdJwtVcVerificationError.InvalidVct -> error.message
                            is SdJwtVcVerificationError.InvalidTypeHeader -> error.message
                            is SdJwtVcVerificationError.TypeMetadataResolutionFailed -> "Type metadata resolution failed: ${error.error}"
                            is SdJwtVcVerificationError.TypeMetadataValidationFailed -> "Type metadata validation failed: ${error.errors.size} errors"
                            is SdJwtVcVerificationError.IssuerMetadataResolutionFailed -> "Issuer metadata resolution failed: ${error.error}"
                            is SdJwtVcVerificationError.StatusCheckFailed -> "Status check failed: ${error.message}"
                            is SdJwtVcVerificationError.SdJwtVerificationFailed -> "SD-JWT verification failed: ${error.message}"
                        },
            )
        }
    }
}
