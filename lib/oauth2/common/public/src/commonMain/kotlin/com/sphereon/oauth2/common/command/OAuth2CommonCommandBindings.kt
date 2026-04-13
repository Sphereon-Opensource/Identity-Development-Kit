/*
 * Copyright 2024-2025 Sphereon International B.V.
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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommand
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2CommonCommandBindings {
    @Provides
    fun applyClientAuthentication(registry: SessionScopedCommandRegistry): ApplyClientAuthenticationCommand =
        registry.get(ApplyClientAuthenticationCommand.COMMAND_ID) as? ApplyClientAuthenticationCommand
            ?: error("No binding for ${ApplyClientAuthenticationCommand.COMMAND_ID}")

    @Provides
    fun createDpopProof(registry: SessionScopedCommandRegistry): CreateDpopProofCommand =
        registry.get(CreateDpopProofCommand.COMMAND_ID) as? CreateDpopProofCommand
            ?: error("No binding for ${CreateDpopProofCommand.COMMAND_ID}")

    @Provides
    fun verifyDpopProof(registry: SessionScopedCommandRegistry): VerifyDpopProofCommand =
        registry.get(VerifyDpopProofCommand.COMMAND_ID) as? VerifyDpopProofCommand
            ?: error("No binding for ${VerifyDpopProofCommand.COMMAND_ID}")

    @Provides
    fun introspectToken(registry: SessionScopedCommandRegistry): IntrospectTokenCommand =
        registry.get(IntrospectTokenCommand.COMMAND_ID) as? IntrospectTokenCommand
            ?: error("No binding for ${IntrospectTokenCommand.COMMAND_ID}")

    @Provides
    fun validateIdToken(registry: SessionScopedCommandRegistry): ValidateIdTokenCommand =
        registry.get(ValidateIdTokenCommand.COMMAND_ID) as? ValidateIdTokenCommand
            ?: error("No binding for ${ValidateIdTokenCommand.COMMAND_ID}")

    @Provides
    fun createJarmResponse(registry: SessionScopedCommandRegistry): CreateJarmResponseCommand =
        registry.get(CreateJarmResponseCommand.COMMAND_ID) as? CreateJarmResponseCommand
            ?: error("No binding for ${CreateJarmResponseCommand.COMMAND_ID}")

    @Provides
    fun verifyJarmResponse(registry: SessionScopedCommandRegistry): VerifyJarmResponseCommand =
        registry.get(VerifyJarmResponseCommand.COMMAND_ID) as? VerifyJarmResponseCommand
            ?: error("No binding for ${VerifyJarmResponseCommand.COMMAND_ID}")

    @Provides
    fun clientRevokeToken(registry: SessionScopedCommandRegistry): ClientRevokeTokenCommand =
        registry.get(ClientRevokeTokenCommand.COMMAND_ID) as? ClientRevokeTokenCommand
            ?: error("No binding for ${ClientRevokeTokenCommand.COMMAND_ID}")
}
