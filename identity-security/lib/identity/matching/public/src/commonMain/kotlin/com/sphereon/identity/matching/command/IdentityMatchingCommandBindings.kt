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

package com.sphereon.identity.matching.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface IdentityMatchingCommandBindings {
    @Provides
    fun lookupIdentityMatch(registry: SessionScopedCommandRegistry): LookupIdentityMatchCommand =
        registry.get(LookupIdentityMatchCommand.COMMAND_ID) as? LookupIdentityMatchCommand
            ?: error("No binding for ${LookupIdentityMatchCommand.COMMAND_ID}")

    @Provides
    fun createIdentityMatch(registry: SessionScopedCommandRegistry): CreateIdentityMatchCommand =
        registry.get(CreateIdentityMatchCommand.COMMAND_ID) as? CreateIdentityMatchCommand
            ?: error("No binding for ${CreateIdentityMatchCommand.COMMAND_ID}")

    @Provides
    fun deleteIdentityMatch(registry: SessionScopedCommandRegistry): DeleteIdentityMatchCommand =
        registry.get(DeleteIdentityMatchCommand.COMMAND_ID) as? DeleteIdentityMatchCommand
            ?: error("No binding for ${DeleteIdentityMatchCommand.COMMAND_ID}")

    @Provides
    fun listIdentityMatches(registry: SessionScopedCommandRegistry): ListIdentityMatchesCommand =
        registry.get(ListIdentityMatchesCommand.COMMAND_ID) as? ListIdentityMatchesCommand
            ?: error("No binding for ${ListIdentityMatchesCommand.COMMAND_ID}")
}
