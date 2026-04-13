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

package com.sphereon.openid.oid4vci.issuer.attribute

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import kotlinx.serialization.json.JsonElement

/**
 * Optional callback invoked before credential issuance to contribute additional attributes.
 *
 * IDK ships a no-op default. EDK replaces it via `@ContributesBinding`.
 */
interface CredentialAttributeContributor {
    suspend fun contribute(
        session: IssuanceSession,
        tokenContext: ValidatedTokenContext,
        credentialConfigurationId: String,
    ): IdkResult<Map<String, JsonElement>, IdkError>
}
