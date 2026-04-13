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

package com.sphereon.openid.oid4vp.common.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ParseClientIdCommand
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.ResolveScopeCommand
import com.sphereon.openid.oid4vp.common.ValidateClientIdCommand
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataCommand
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationCommand
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpCommonCommandBindings {

    @Provides
    fun parseClientIdCommand(registry: SessionScopedCommandRegistry): ParseClientIdCommand =
        registry.get(ParseClientIdCommand.COMMAND_ID) as? ParseClientIdCommand
            ?: error("No binding for ${ParseClientIdCommand.COMMAND_ID}")

    @Provides
    fun verifyTransactionDataCommand(registry: SessionScopedCommandRegistry): VerifyTransactionDataCommand =
        registry.get(VerifyTransactionDataCommand.COMMAND_ID) as? VerifyTransactionDataCommand
            ?: error("No binding for ${VerifyTransactionDataCommand.COMMAND_ID}")

    @Provides
    fun parseTransactionDataCommand(registry: SessionScopedCommandRegistry): ParseTransactionDataCommand =
        registry.get(ParseTransactionDataCommand.COMMAND_ID) as? ParseTransactionDataCommand
            ?: error("No binding for ${ParseTransactionDataCommand.COMMAND_ID}")

    @Provides
    fun resolveScopeCommand(registry: SessionScopedCommandRegistry): ResolveScopeCommand =
        registry.get(ResolveScopeCommand.COMMAND_ID) as? ResolveScopeCommand
            ?: error("No binding for ${ResolveScopeCommand.COMMAND_ID}")

    @Provides
    fun verifyVerifierAttestationCommand(registry: SessionScopedCommandRegistry): VerifyVerifierAttestationCommand =
        registry.get(VerifyVerifierAttestationCommand.COMMAND_ID) as? VerifyVerifierAttestationCommand
            ?: error("No binding for ${VerifyVerifierAttestationCommand.COMMAND_ID}")

    @Provides
    fun validateClientIdCommand(registry: SessionScopedCommandRegistry): ValidateClientIdCommand =
        registry.get(ValidateClientIdCommand.COMMAND_ID) as? ValidateClientIdCommand
            ?: error("No binding for ${ValidateClientIdCommand.COMMAND_ID}")
}
