/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.command.CreateIdentityMatchCommand
import com.sphereon.identity.matching.command.DeleteIdentityMatchCommand
import com.sphereon.identity.matching.command.ListIdentityMatchesCommand
import com.sphereon.identity.matching.command.LookupIdentityMatchCommand
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface IdentityMatchingCommandDescriptors {

    @Provides @IntoSet
    fun lookupIdentityMatch(impl: Lazy<LookupIdentityMatchCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(LookupIdentityMatchCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createIdentityMatch(impl: Lazy<CreateIdentityMatchCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateIdentityMatchCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun deleteIdentityMatch(impl: Lazy<DeleteIdentityMatchCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DeleteIdentityMatchCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun listIdentityMatches(impl: Lazy<ListIdentityMatchesCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ListIdentityMatchesCommand.COMMAND_ID) { impl.value }
}
