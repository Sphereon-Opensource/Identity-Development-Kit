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

package com.sphereon.openid.oid4vp.dcql.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.common.store.ConfigurationStore
import com.sphereon.openid.oid4vp.dcql.store.model.DcqlQueryConfiguration
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Store for DCQL query configurations referenced via `query_id` (Universal OID4VP).
 *
 * Configurations are persistent by default (no expiration).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DcqlQueryConfigurationStore", exact = true)
interface DcqlQueryConfigurationStore : ConfigurationStore<String, DcqlQueryConfiguration> {
    suspend fun getByQueryId(queryId: String): IdkResult<DcqlQueryConfiguration?, IdkError>
}
