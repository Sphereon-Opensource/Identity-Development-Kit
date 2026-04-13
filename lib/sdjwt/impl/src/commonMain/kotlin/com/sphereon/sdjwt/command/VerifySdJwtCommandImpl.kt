/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.sdjwt.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter


import com.sphereon.crypto.jose.jws.VerifyJwsCommand as JwsVerifyCommand
import com.sphereon.crypto.resolution.extern.CnfExternalIdentifierResolutionService
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.SdJwtVerificationResult
import com.sphereon.sdjwt.VerifySdJwtArgs

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName


/**
 * Implementation of VerifySdJwtCommand.
 *
 * This command verifies an SD-JWT according to RFC 9901 §6:
 * 1. Parse and validate SD-JWT format
 * 2. Verify JWT signature using existing JWS infrastructure
 * 3. Verify disclosure digests match values in _sd array
 * 4. If KB-JWT present, verify it and check sd_hash binding
 * 5. Validate timing claims (exp, nbf, iat)
 *
 * ## Holder Binding Resolution
 *
 * When the SD-JWT contains a CNF claim, this command uses the [CnfExternalIdentifierResolutionService]
 * to resolve the holder's public key for KB-JWT signature verification. The service supports:
 * - DID-based kid (e.g., "did:key:z6Mk...#key-1") → resolves via DID resolver
 * - Embedded JWK → uses directly
 * - JKU (JWK Set URL) → fetches from remote URL
 *
 * @property execution Session execution context
 * @property verifyJwsCommand Command for verifying JWS signatures
 * @property cnfResolver Service for resolving CNF claims to holder keys
 * @property plugin Optional plugin for command execution
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifySdJwtCommandImpl", exact = true)
class VerifySdJwtCommandImpl(
    execution: SessionExecution,
    private val verifyJwsCommand: JwsVerifyCommand,
    private val cnfResolver: CnfExternalIdentifierResolutionService,
) : TypedServiceCommandAdapter<VerifySdJwtArgs, SdJwtVerificationResult>(
    commandId = VerifySdJwtCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifySdJwtArgs>(),
    outputTypeToken = typeToken<SdJwtVerificationResult>(),
), VerifySdJwtCommand {

    override val commandId: String get() = VerifySdJwtCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifySdJwtArgs

    override suspend fun doExecute(
        args: VerifySdJwtArgs,
        applyDuring: (VerifySdJwtArgs) -> VerifySdJwtArgs
    ): IdkResult<SdJwtVerificationResult, IdkError> {
        val appliedArgs = applyDuring(args)

        log.debug("Verifying SD-JWT: length=${appliedArgs.sdJwt.length}, validateDisclosures=${appliedArgs.validateDisclosures}")

        try {
            // Create verifier and delegate to it
            val verifier = SdJwtVerifier(
                verifyJwsCommand = verifyJwsCommand,
                cnfResolver = cnfResolver
            )

            val result = verifier.verify(
                sdJwtString = appliedArgs.sdJwt,
                identifier = appliedArgs.identifier,
                expectedAudience = appliedArgs.expectedAudience,
                expectedNonce = appliedArgs.expectedNonce,
                validateDisclosures = appliedArgs.validateDisclosures
            )

            if (result.isOk) {
                val verification = result.value
                log.info("SD-JWT verification: isValid=${verification.isValid}, signatureValid=${verification.signatureValid}, disclosuresValid=${verification.disclosuresValid}")
            } else {
                log.error("SD-JWT verification failed: ${result.error.message}")
            }

            return result
        } catch (e: Exception) {
            log.error("Failed to verify SD-JWT: ${e.message}", e)
            return IdkResult.err(
                IdkError.fromString(
                    message = "Failed to verify SD-JWT: ${e.message}",
                    exception = e
                )
            )
        }
    }

}
