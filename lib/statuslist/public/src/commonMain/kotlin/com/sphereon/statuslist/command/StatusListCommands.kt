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

package com.sphereon.statuslist.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.pagination.Page
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.ListStatusListsArgs
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.RevokeCredentialStatusArgs
import com.sphereon.statuslist.StatusListEntry
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListResult
import com.sphereon.statuslist.StatusListSummary
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.UpdateEntryStatusArgs

/** Create a status list (issuer/management). */
@JsExportCompat
interface CreateStatusListCommand : ServiceCommand<CreateStatusListArgs, StatusListResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.list.create"
    }
}

/** Fetch a status list by id or correlationId (issuer/management). */
@JsExportCompat
interface GetStatusListCommand : ServiceCommand<StatusListRef, StatusListResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.list.get"
    }
}

/** List status lists, filtered + paginated (issuer/management). */
@JsExportCompat
interface ListStatusListsCommand : ServiceCommand<ListStatusListsArgs, Page<StatusListSummary>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.list.list"
    }
}

/** Update an entry's status by raw value — revoke/suspend/reactivate by index, entryCorrelationId, or credentialId. */
@JsExportCompat
interface UpdateCredentialStatusCommand : ServiceCommand<UpdateEntryStatusArgs, StatusListEntry, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.entry.update"
    }
}

/**
 * Ergonomic revoke/suspend/reactivate of a single credential — the simplest entry point for
 * integrations (e.g. an IDK example issuer) that just want to revoke by credentialId without
 * dealing with raw status values or REST. Pure IDK; backed by the in-memory or any bound driver.
 */
@JsExportCompat
interface RevokeCredentialStatusCommand : ServiceCommand<RevokeCredentialStatusArgs, StatusListEntry, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.entry.revoke"
    }
}

/** Look up a single status-list entry (issuer/management). */
@JsExportCompat
interface GetStatusListEntryCommand : ServiceCommand<EntryRef, StatusListEntry, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.entry.get"
    }
}

/** Fetch the signed, hostable status-list token (public hosting endpoint backs onto this). */
@JsExportCompat
interface GetStatusListTokenCommand : ServiceCommand<StatusListRef, StatusListToken, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.token.get"
    }
}

/** Resolve + verify the status of a credential by status-list reference (holder/verifier). */
@JsExportCompat
interface CheckCredentialStatusCommand : ServiceCommand<ResolveStatusArgs, ResolvedStatus, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "statuslist.status.check"
    }
}
