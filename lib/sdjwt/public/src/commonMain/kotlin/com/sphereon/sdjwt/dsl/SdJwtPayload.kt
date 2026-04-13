/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt.dsl

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The result of building an SD-JWT payload.
 *
 * This data class cleanly separates the JWT claims from the selective disclosure metadata,
 * eliminating the need for workarounds like storing metadata as a special claim.
 *
 * When issuing an SD-JWT, the [SdJwtIssuer] uses this information to:
 * 1. Transform claims in [sdClaims] into disclosures
 * 2. Replace those claims with digests in the `_sd` array
 * 3. Add decoy digests based on [minimumDigests]
 *
 * Example:
 * ```kotlin
 * val payload = sdJwtPayload {
 *     iss("https://issuer.example.com")
 *     subSd("user-123")
 *     claimSd("email", "user@example.com")
 *     claim("verified", true)
 *     minimumDigests(5)
 * }
 *
 * println(payload.claims)          // {"iss":"https://issuer.example.com","sub":"user-123","email":"user@example.com","verified":true}
 * println(payload.sdClaims)        // [sub, email]
 * println(payload.minimumDigests)  // 5
 * ```
 *
 * @property claims The JWT claims as a JsonObject (no SD metadata pollution)
 * @property sdClaims Set of claim names that should be selectively disclosable
 * @property minimumDigests Optional minimum number of digests (including decoys) in the `_sd` array
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtPayload", exact = true)
@JsExportCompat
data class SdJwtPayload(
    val claims: JsonObject,
    val sdClaims: Set<String>,
    val minimumDigests: Int? = null,
) {
    /**
     * Checks if a specific claim is marked as selectively disclosable.
     *
     * @param claimName The name of the claim to check
     * @return True if the claim is selectively disclosable
     */
    fun isClaimSelectivelyDisclosable(claimName: String): Boolean = claimName in sdClaims

    /**
     * Returns the set of claim names that are NOT selectively disclosable.
     *
     * These claims will appear directly in the JWT payload without being
     * hidden behind digests.
     */
    val plainClaims: Set<String>
        get() = claims.keys - sdClaims
}
