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

package com.sphereon.identity.matching.protection

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType

/**
 * The resolved at-rest protection decision for a single identifier type within a tenant.
 *
 * Couples the storage [mode] with the [normalization] used to canonicalize the value before
 * blind indexing or encryption. [searchable] indicates whether the resulting representation can
 * be looked up at login time; by default this is derived from the mode but it can be overridden by
 * a policy source. PLAINTEXT (looked up by the cleartext column), SEARCHABLE_ENCRYPTED, and
 * SEARCHABLE_BLIND_INDEX (looked up by the deterministic tenant-wide blind index) are searchable;
 * only SALTED_BLINDED is not, because its per-identity salt makes a global lookup impossible.
 */
@JsExportCompat
data class IdentifierProtectionPolicy(
    val identifierType: IdentifierType,
    val mode: IdentifierProtectionMode,
    val normalization: NormalizationProfile = NormalizationProfile.NONE,
    val searchable: Boolean = (mode != IdentifierProtectionMode.SALTED_BLINDED),
)
