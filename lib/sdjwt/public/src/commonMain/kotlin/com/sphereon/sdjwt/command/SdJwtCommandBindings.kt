/*
 * Copyright (c) 2026 Sphereon B.V.
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

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface SdJwtCommandBindings {
    // SD-JWT commands
    @Provides
    fun issueSdJwt(registry: SessionScopedCommandRegistry): IssueSdJwtCommand =
        registry.get(IssueSdJwtCommand.COMMAND_ID) as? IssueSdJwtCommand
            ?: error("No binding for ${IssueSdJwtCommand.COMMAND_ID}")

    @Provides
    fun presentSdJwt(registry: SessionScopedCommandRegistry): PresentSdJwtCommand =
        registry.get(PresentSdJwtCommand.COMMAND_ID) as? PresentSdJwtCommand
            ?: error("No binding for ${PresentSdJwtCommand.COMMAND_ID}")

    @Provides
    fun verifySdJwt(registry: SessionScopedCommandRegistry): VerifySdJwtCommand =
        registry.get(VerifySdJwtCommand.COMMAND_ID) as? VerifySdJwtCommand
            ?: error("No binding for ${VerifySdJwtCommand.COMMAND_ID}")

    // SD-JWT-VC commands
    @Provides
    fun verifySdJwtVc(registry: SessionScopedCommandRegistry): VerifySdJwtVcCommand =
        registry.get(VerifySdJwtVcCommand.COMMAND_ID) as? VerifySdJwtVcCommand
            ?: error("No binding for ${VerifySdJwtVcCommand.COMMAND_ID}")

    @Provides
    fun verifySdJwtVcPresentation(registry: SessionScopedCommandRegistry): VerifySdJwtVcPresentationCommand =
        registry.get(VerifySdJwtVcPresentationCommand.COMMAND_ID) as? VerifySdJwtVcPresentationCommand
            ?: error("No binding for ${VerifySdJwtVcPresentationCommand.COMMAND_ID}")

    @Provides
    fun resolveTypeMetadata(registry: SessionScopedCommandRegistry): ResolveTypeMetadataCommand =
        registry.get(ResolveTypeMetadataCommand.COMMAND_ID) as? ResolveTypeMetadataCommand
            ?: error("No binding for ${ResolveTypeMetadataCommand.COMMAND_ID}")

    @Provides
    fun resolveIssuerMetadata(registry: SessionScopedCommandRegistry): ResolveIssuerMetadataCommand =
        registry.get(ResolveIssuerMetadataCommand.COMMAND_ID) as? ResolveIssuerMetadataCommand
            ?: error("No binding for ${ResolveIssuerMetadataCommand.COMMAND_ID}")
}
