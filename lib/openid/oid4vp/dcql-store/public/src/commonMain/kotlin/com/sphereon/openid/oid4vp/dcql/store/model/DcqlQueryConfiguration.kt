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

package com.sphereon.openid.oid4vp.dcql.store.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A stored DCQL query configuration referenced by `query_id` (Universal OID4VP).
 *
 * Configuration entries are intended to be persistent by default.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DcqlQueryConfiguration", exact = true)
@JsExportCompat
@Serializable
data class DcqlQueryConfiguration(
    val queryId: String,
    val name: String,
    val description: String? = null,
    val dcqlQuery: DcqlQuery,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * The version number of the currently active DCQL body, when the configuration is backed
     * by a version-history store. Null for stores without versioning (the IDK KV/SQLite
     * backings); set by the EDK versioned store.
     */
    val currentVersion: Int? = null,
)
