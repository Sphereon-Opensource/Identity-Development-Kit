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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import kotlinx.serialization.Serializable

/**
 * Arguments for creating a new DCQL query configuration.
 *
 * The tenant is resolved from the session execution context, never passed as an argument.
 */
@JsExportCompat
@Serializable
data class CreateDcqlQueryArgs(
    val queryId: String,
    val name: String,
    val description: String? = null,
    val dcqlQuery: DcqlQuery,
    val enabled: Boolean = true,
)

/**
 * Arguments for reading a single DCQL query configuration by its `query_id`.
 */
@JsExportCompat
@Serializable
data class GetDcqlQueryArgs(
    val queryId: String,
)

/**
 * Arguments for listing all DCQL query configurations for the current tenant.
 */
@JsExportCompat
@Serializable
class ListDcqlQueriesArgs

/**
 * Arguments for updating an existing DCQL query configuration.
 *
 * A null field leaves the corresponding value unchanged. This supports both full
 * replacement (PUT, all fields supplied) and partial update (PATCH, a subset supplied).
 */
@JsExportCompat
@Serializable
data class UpdateDcqlQueryArgs(
    val queryId: String,
    val name: String? = null,
    val description: String? = null,
    val dcqlQuery: DcqlQuery? = null,
    val enabled: Boolean? = null,
)

/**
 * Arguments for deleting a DCQL query configuration by its `query_id`.
 */
@JsExportCompat
@Serializable
data class DeleteDcqlQueryArgs(
    val queryId: String,
)
