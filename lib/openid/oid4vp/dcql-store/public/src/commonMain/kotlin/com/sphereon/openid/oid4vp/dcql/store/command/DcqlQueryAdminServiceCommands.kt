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

package com.sphereon.openid.oid4vp.dcql.store.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vp.dcql.store.model.DcqlQueryConfiguration

/**
 * Create a new DCQL query configuration for the current tenant.
 *
 * Fails if a configuration with the same `query_id` already exists.
 */
@JsExportCompat
interface CreateDcqlQueryServiceCommand : ServiceCommand<CreateDcqlQueryArgs, DcqlQueryConfiguration, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.dcql.create"
    }
}

/**
 * Read a single DCQL query configuration by its `query_id`.
 */
@JsExportCompat
interface GetDcqlQueryServiceCommand : ServiceCommand<GetDcqlQueryArgs, DcqlQueryConfiguration, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.dcql.get"
    }
}

/**
 * List all DCQL query configurations for the current tenant.
 */
@JsExportCompat
interface ListDcqlQueriesServiceCommand : ServiceCommand<ListDcqlQueriesArgs, List<DcqlQueryConfiguration>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.dcql.list"
    }
}

/**
 * Update an existing DCQL query configuration.
 *
 * Fails if no configuration with the given `query_id` exists.
 */
@JsExportCompat
interface UpdateDcqlQueryServiceCommand : ServiceCommand<UpdateDcqlQueryArgs, DcqlQueryConfiguration, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.dcql.update"
    }
}

/**
 * Delete a DCQL query configuration by its `query_id`.
 *
 * Returns true when an entry was removed, false when no entry existed.
 */
@JsExportCompat
interface DeleteDcqlQueryServiceCommand : ServiceCommand<DeleteDcqlQueryArgs, Boolean, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.dcql.delete"
    }
}
