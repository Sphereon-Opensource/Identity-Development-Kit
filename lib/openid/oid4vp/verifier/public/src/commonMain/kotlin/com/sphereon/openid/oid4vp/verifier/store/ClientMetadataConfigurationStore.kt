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

package com.sphereon.openid.oid4vp.verifier.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.verifier.model.ClientMetadataConfiguration
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Store for RP client metadata configurations.
 *
 * Client metadata is persistent by default (no expiration).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ClientMetadataConfigurationStore", exact = true)
interface ClientMetadataConfigurationStore : ConfigurationStore<String, ClientMetadataConfiguration> {
    suspend fun getByClientMetadataId(clientMetadataId: String): IdkResult<ClientMetadataConfiguration?, IdkError>

    suspend fun getByClientId(clientId: String): IdkResult<ClientMetadataConfiguration?, IdkError>
}
