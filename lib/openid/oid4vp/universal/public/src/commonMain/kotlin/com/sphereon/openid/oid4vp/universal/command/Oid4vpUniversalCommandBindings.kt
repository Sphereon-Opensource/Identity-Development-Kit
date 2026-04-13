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

package com.sphereon.openid.oid4vp.universal.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusServiceCommand
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpUniversalCommandBindings {

    @Provides
    fun createAuthRequestServiceCommand(registry: SessionScopedCommandRegistry): CreateAuthRequestServiceCommand =
        registry.get(CreateAuthRequestServiceCommand.COMMAND_ID) as? CreateAuthRequestServiceCommand
            ?: error("No binding for ${CreateAuthRequestServiceCommand.COMMAND_ID}")

    @Provides
    fun getAuthRequestStatusServiceCommand(registry: SessionScopedCommandRegistry): GetAuthRequestStatusServiceCommand =
        registry.get(GetAuthRequestStatusServiceCommand.COMMAND_ID) as? GetAuthRequestStatusServiceCommand
            ?: error("No binding for ${GetAuthRequestStatusServiceCommand.COMMAND_ID}")

    @Provides
    fun deleteAuthRequestServiceCommand(registry: SessionScopedCommandRegistry): DeleteAuthRequestServiceCommand =
        registry.get(DeleteAuthRequestServiceCommand.COMMAND_ID) as? DeleteAuthRequestServiceCommand
            ?: error("No binding for ${DeleteAuthRequestServiceCommand.COMMAND_ID}")
}
