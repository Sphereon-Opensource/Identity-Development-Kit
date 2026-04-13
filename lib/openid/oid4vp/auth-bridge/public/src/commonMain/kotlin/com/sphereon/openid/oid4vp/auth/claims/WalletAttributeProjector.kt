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

package com.sphereon.openid.oid4vp.auth.claims

import com.sphereon.openid.oid4vp.universal.VerifiedCredential
import kotlinx.serialization.json.JsonElement

/**
 * Projects raw wallet credential attributes into canonical output attributes.
 *
 * Replaces the query-based [DcqlClaimsMappingAdapter] for wallet-only auth flows.
 * The implementation applies:
 * 1. Source-specific extraction mappings (wallet key -> canonical name)
 * 2. Canonical attribute rules (required, persist, project flags)
 * 3. Projection filtering (only project=true attributes in output)
 */
interface WalletAttributeProjector {
    /**
     * Project raw wallet credentials into canonical output attributes.
     *
     * @param credentials Verified credentials from the OID4VP presentation
     * @return Projected canonical attributes (only project=true attributes)
     */
    suspend fun projectWalletAttributes(credentials: List<VerifiedCredential>): Map<String, JsonElement>
}
