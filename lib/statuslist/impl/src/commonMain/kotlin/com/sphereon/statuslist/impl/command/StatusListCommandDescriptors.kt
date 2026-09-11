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

package com.sphereon.statuslist.impl.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.command.CheckCredentialStatusCommand
import com.sphereon.statuslist.command.CreateStatusListCommand
import com.sphereon.statuslist.command.DeleteStatusListCommand
import com.sphereon.statuslist.command.GetStatusListCommand
import com.sphereon.statuslist.command.GetStatusListEntryCommand
import com.sphereon.statuslist.command.GetStatusListTokenCommand
import com.sphereon.statuslist.command.ListStatusListsCommand
import com.sphereon.statuslist.command.RevokeCredentialStatusCommand
import com.sphereon.statuslist.command.UpdateCredentialStatusCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

/** Registers the status-list service commands into the command registry, keyed by command id. */
@ContributesTo(SessionScope::class)
interface StatusListCommandDescriptors {
    @Provides
    @IntoMap
    @StringKey(CreateStatusListCommand.COMMAND_ID)
    fun createStatusList(impl: CreateStatusListCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(GetStatusListCommand.COMMAND_ID)
    fun getStatusList(impl: GetStatusListCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(DeleteStatusListCommand.COMMAND_ID)
    fun deleteStatusList(impl: DeleteStatusListCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(ListStatusListsCommand.COMMAND_ID)
    fun listStatusLists(impl: ListStatusListsCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(UpdateCredentialStatusCommand.COMMAND_ID)
    fun updateCredentialStatus(impl: UpdateCredentialStatusCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(RevokeCredentialStatusCommand.COMMAND_ID)
    fun revokeCredentialStatus(impl: RevokeCredentialStatusCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(GetStatusListEntryCommand.COMMAND_ID)
    fun getStatusListEntry(impl: GetStatusListEntryCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(GetStatusListTokenCommand.COMMAND_ID)
    fun getStatusListToken(impl: GetStatusListTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides
    @IntoMap
    @StringKey(CheckCredentialStatusCommand.COMMAND_ID)
    fun checkCredentialStatus(impl: CheckCredentialStatusCommandImpl): ServiceCommand<*, *, *> = impl

    // Interface bindings so callers (e.g. the hosting / management REST endpoint commands) can inject
    // a specific command by its interface type and delegate to `execute(...)`, in addition to the
    // id-keyed registry map above. Each returns the same SessionScope singleton impl.
    @Provides
    fun bindCreateStatusList(impl: CreateStatusListCommandImpl): CreateStatusListCommand = impl

    @Provides
    fun bindGetStatusList(impl: GetStatusListCommandImpl): GetStatusListCommand = impl

    @Provides
    fun bindDeleteStatusList(impl: DeleteStatusListCommandImpl): DeleteStatusListCommand = impl

    @Provides
    fun bindListStatusLists(impl: ListStatusListsCommandImpl): ListStatusListsCommand = impl

    @Provides
    fun bindUpdateCredentialStatus(impl: UpdateCredentialStatusCommandImpl): UpdateCredentialStatusCommand = impl

    @Provides
    fun bindRevokeCredentialStatus(impl: RevokeCredentialStatusCommandImpl): RevokeCredentialStatusCommand = impl

    @Provides
    fun bindGetStatusListEntry(impl: GetStatusListEntryCommandImpl): GetStatusListEntryCommand = impl

    @Provides
    fun bindGetStatusListToken(impl: GetStatusListTokenCommandImpl): GetStatusListTokenCommand = impl

    @Provides
    fun bindCheckCredentialStatus(impl: CheckCredentialStatusCommandImpl): CheckCredentialStatusCommand = impl
}
