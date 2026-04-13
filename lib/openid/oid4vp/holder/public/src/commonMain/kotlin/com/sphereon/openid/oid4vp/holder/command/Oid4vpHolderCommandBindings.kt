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

package com.sphereon.openid.oid4vp.holder.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseCommand
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpHolderCommandBindings {

    @Provides
    fun resolveClientMetadataCommand(registry: SessionScopedCommandRegistry): ResolveClientMetadataCommand =
        registry.get(ResolveClientMetadataCommand.COMMAND_ID) as? ResolveClientMetadataCommand
            ?: error("No binding for ${ResolveClientMetadataCommand.COMMAND_ID}")

    @Provides
    fun resolveAuthorizationRequestCommand(registry: SessionScopedCommandRegistry): ResolveAuthorizationRequestCommand =
        registry.get(ResolveAuthorizationRequestCommand.COMMAND_ID) as? ResolveAuthorizationRequestCommand
            ?: error("No binding for ${ResolveAuthorizationRequestCommand.COMMAND_ID}")

    @Provides
    fun submitAuthorizationResponseCommand(registry: SessionScopedCommandRegistry): SubmitAuthorizationResponseCommand =
        registry.get(SubmitAuthorizationResponseCommand.COMMAND_ID) as? SubmitAuthorizationResponseCommand
            ?: error("No binding for ${SubmitAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationResponseCommand(registry: SessionScopedCommandRegistry): CreateAuthorizationResponseCommand =
        registry.get(CreateAuthorizationResponseCommand.COMMAND_ID) as? CreateAuthorizationResponseCommand
            ?: error("No binding for ${CreateAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun parseAuthorizationRequestCommand(registry: SessionScopedCommandRegistry): ParseAuthorizationRequestCommand =
        registry.get(ParseAuthorizationRequestCommand.COMMAND_ID) as? ParseAuthorizationRequestCommand
            ?: error("No binding for ${ParseAuthorizationRequestCommand.COMMAND_ID}")
}
