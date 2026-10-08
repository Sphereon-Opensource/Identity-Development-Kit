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

package com.sphereon.sdjwt.vc

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Service implementation for SD-JWT-VC operations
 *
 * Provides high-level API for:
 * - Verifying SD-JWT-VC credentials
 * - Verifying SD-JWT-VC presentations (with KB-JWT)
 * - Resolving type and issuer metadata
 *
 * Session-scoped for multi-tenancy support.
 * Follows the same pattern as SdJwtServiceImpl.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SdJwtVcService>())
class SdJwtVcServiceImpl(
    private val verifySdJwtVcCommand: VerifySdJwtVcCommand,
    private val verifySdJwtVcPresentationCommand: VerifySdJwtVcPresentationCommand,
    private val resolveTypeMetadataCommand: ResolveTypeMetadataCommand,
    private val resolveIssuerMetadataCommand: ResolveIssuerMetadataCommand,
    private val execution: SessionExecution,
) : SdJwtVcService {
    /**
     * Implementation of Commands that exposes the injected command instances.
     */
    inner class CommandsImpl : SdJwtVcService.Commands {
        override val verifySdJwtVc: VerifySdJwtVcCommand = this@SdJwtVcServiceImpl.verifySdJwtVcCommand
        override val verifySdJwtVcPresentation: VerifySdJwtVcPresentationCommand = this@SdJwtVcServiceImpl.verifySdJwtVcPresentationCommand
        override val resolveTypeMetadata: ResolveTypeMetadataCommand = this@SdJwtVcServiceImpl.resolveTypeMetadataCommand
        override val resolveIssuerMetadata: ResolveIssuerMetadataCommand = this@SdJwtVcServiceImpl.resolveIssuerMetadataCommand
    }

    override val commands: SdJwtVcService.Commands = CommandsImpl()

    // Implement command service interfaces

    override suspend fun verifySdJwtVc(args: VerifySdJwtVcArgs): IdkResult<SdJwtVcVerificationResult, IdkError> = verifySdJwtVcCommand.execute(args)

    override suspend fun verifySdJwtVcPresentation(args: VerifySdJwtVcPresentationArgs): IdkResult<SdJwtVcPresentationVerificationResult, IdkError> = verifySdJwtVcPresentationCommand.execute(args)

    override suspend fun resolveTypeMetadata(args: ResolveTypeMetadataArgs): IdkResult<TypeMetadataResolutionResult, IdkError> = resolveTypeMetadataCommand.execute(args)

    override suspend fun resolveIssuerMetadata(args: ResolveIssuerMetadataArgs): IdkResult<IssuerMetadataResolutionResult, IdkError> = resolveIssuerMetadataCommand.execute(args)

    // Implement traditional service interface methods

    override suspend fun verify(args: VerifySdJwtVcArgs): IdkResult<SdJwtVcVerificationResult, IdkError> = verifySdJwtVcCommand.execute(args)

    override suspend fun verifyPresentation(args: VerifySdJwtVcPresentationArgs): IdkResult<SdJwtVcPresentationVerificationResult, IdkError> = verifySdJwtVcPresentationCommand.execute(args)
}
