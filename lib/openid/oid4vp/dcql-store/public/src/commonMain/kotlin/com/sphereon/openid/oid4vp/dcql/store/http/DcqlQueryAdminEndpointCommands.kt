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

package com.sphereon.openid.oid4vp.dcql.store.http

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/**
 * Tag applied to all DCQL query administration endpoints.
 */
private const val DCQL_ADMIN_TAG = "oid4vp-dcql"

/**
 * List all DCQL query configurations for the current tenant.
 *
 * GET /api/dcql/v1/queries
 */
@JsExportCompat
interface ListDcqlQueriesEndpointCommand : HttpEndpointCommand {
    companion object {
const val COMMAND_ID = "oid4vp.dcql-http.list"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/queries",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listDcqlQueries",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf(DCQL_ADMIN_TAG),
                summary = "List DCQL query configurations",
            )
    }
}

/**
 * Create a new DCQL query configuration.
 *
 * POST /api/dcql/v1/queries
 */
@JsExportCompat
interface CreateDcqlQueryEndpointCommand : HttpEndpointCommand {
    companion object {
const val COMMAND_ID = "oid4vp.dcql-http.create"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/queries",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "createDcqlQuery",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf(DCQL_ADMIN_TAG),
                summary = "Create a DCQL query configuration",
            )
    }
}

/**
 * Read a single DCQL query configuration by its `query_id`.
 *
 * GET /api/dcql/v1/queries/{queryId}
 */
@JsExportCompat
interface GetDcqlQueryEndpointCommand : HttpEndpointCommand {
    companion object {
const val COMMAND_ID = "oid4vp.dcql-http.get"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/queries/{queryId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getDcqlQuery",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf(DCQL_ADMIN_TAG),
                summary = "Get a DCQL query configuration",
            )
    }
}

/**
 * Replace a DCQL query configuration in full.
 *
 * PUT /api/dcql/v1/queries/{queryId}
 */
@JsExportCompat
interface ReplaceDcqlQueryEndpointCommand : HttpEndpointCommand {
    companion object {
const val COMMAND_ID = "oid4vp.dcql-http.replace"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "/queries/{queryId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "replaceDcqlQuery",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf(DCQL_ADMIN_TAG),
                summary = "Replace a DCQL query configuration",
            )
    }
}

/**
 * Partially update a DCQL query configuration.
 *
 * PATCH /api/dcql/v1/queries/{queryId}
 */
@JsExportCompat
interface PatchDcqlQueryEndpointCommand : HttpEndpointCommand {
    companion object {
const val COMMAND_ID = "oid4vp.dcql-http.patch"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PATCH,
                pathPattern = "/queries/{queryId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "patchDcqlQuery",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf(DCQL_ADMIN_TAG),
                summary = "Partially update a DCQL query configuration",
            )
    }
}

/**
 * Delete a DCQL query configuration by its `query_id`.
 *
 * DELETE /api/dcql/v1/queries/{queryId}
 */
@JsExportCompat
interface DeleteDcqlQueryEndpointCommand : HttpEndpointCommand {
    companion object {
const val COMMAND_ID = "oid4vp.dcql-http.delete"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/queries/{queryId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "deleteDcqlQuery",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf(DCQL_ADMIN_TAG),
                summary = "Delete a DCQL query configuration",
            )
    }
}
