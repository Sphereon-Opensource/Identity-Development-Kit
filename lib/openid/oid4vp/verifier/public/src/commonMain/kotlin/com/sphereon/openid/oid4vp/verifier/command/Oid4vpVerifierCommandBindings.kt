/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.verifier.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpVerifierCommandBindings {

    @Provides
    fun createSignedAuthorizationRequestCommand(registry: SessionScopedCommandRegistry): CreateSignedAuthorizationRequestCommand =
        registry.get(CreateSignedAuthorizationRequestCommand.COMMAND_ID) as? CreateSignedAuthorizationRequestCommand
            ?: error("No binding for ${CreateSignedAuthorizationRequestCommand.COMMAND_ID}")

    @Provides
    fun buildAuthorizationRequestUriCommand(registry: SessionScopedCommandRegistry): BuildAuthorizationRequestUriCommand =
        registry.get(BuildAuthorizationRequestUriCommand.COMMAND_ID) as? BuildAuthorizationRequestUriCommand
            ?: error("No binding for ${BuildAuthorizationRequestUriCommand.COMMAND_ID}")

    @Provides
    fun retrieveAuthorizationResponseCommand(registry: SessionScopedCommandRegistry): RetrieveAuthorizationResponseCommand =
        registry.get(RetrieveAuthorizationResponseCommand.COMMAND_ID) as? RetrieveAuthorizationResponseCommand
            ?: error("No binding for ${RetrieveAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun handleDirectPostResponseCommand(registry: SessionScopedCommandRegistry): HandleDirectPostResponseCommand =
        registry.get(HandleDirectPostResponseCommand.COMMAND_ID) as? HandleDirectPostResponseCommand
            ?: error("No binding for ${HandleDirectPostResponseCommand.COMMAND_ID}")

    @Provides
    fun verifyHolderBindingCommand(registry: SessionScopedCommandRegistry): VerifyHolderBindingCommand =
        registry.get(VerifyHolderBindingCommand.COMMAND_ID) as? VerifyHolderBindingCommand
            ?: error("No binding for ${VerifyHolderBindingCommand.COMMAND_ID}")

    @Provides
    fun validateAuthorizationResponseCommand(registry: SessionScopedCommandRegistry): ValidateAuthorizationResponseCommand =
        registry.get(ValidateAuthorizationResponseCommand.COMMAND_ID) as? ValidateAuthorizationResponseCommand
            ?: error("No binding for ${ValidateAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun parseAuthorizationResponseCommand(registry: SessionScopedCommandRegistry): ParseAuthorizationResponseCommand =
        registry.get(ParseAuthorizationResponseCommand.COMMAND_ID) as? ParseAuthorizationResponseCommand
            ?: error("No binding for ${ParseAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationRequestCommand(registry: SessionScopedCommandRegistry): CreateAuthorizationRequestCommand =
        registry.get(CreateAuthorizationRequestCommand.COMMAND_ID) as? CreateAuthorizationRequestCommand
            ?: error("No binding for ${CreateAuthorizationRequestCommand.COMMAND_ID}")
}
