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
 * Resolves the [IdentifierProtectionPolicy] that applies to a given identifier type within a
 * tenant. Implementations may consult tenant configuration; the default supplies sensible
 * built-in choices so callers always receive a usable policy.
 */
@JsExportCompat
interface IdentifierProtectionPolicyService {
    /** Returns the policy governing how identifiers of [type] are protected for [tenantId]. */
    suspend fun policyFor(
        tenantId: String,
        type: IdentifierType,
    ): IdentifierProtectionPolicy
}

/**
 * Built-in [IdentifierProtectionPolicyService] with safe defaults and no tenant overrides.
 *
 * Defaults:
 * - email / phone / did / x509 are searchable blind indexes (email/phone/did each normalize
 *   to their natural canonical form).
 * - issuer / oidc_issuer / oid4vci_issuer are public URLs and stay plaintext (host-normalized).
 * - any unrecognized type falls back to a searchable blind index, which is the privacy-safe
 *   default for an unknown correlation identifier.
 */
class DefaultIdentifierProtectionPolicyService : IdentifierProtectionPolicyService {
    override suspend fun policyFor(
        tenantId: String,
        type: IdentifierType,
    ): IdentifierProtectionPolicy =
        when (type.value.lowercase()) {
            "email" -> {
                IdentifierProtectionPolicy(
                    identifierType = type,
                    mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                    normalization = NormalizationProfile.EMAIL,
                )
            }

            "phone" -> {
                IdentifierProtectionPolicy(
                    identifierType = type,
                    mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                    normalization = NormalizationProfile.PHONE_E164,
                )
            }

            "did" -> {
                IdentifierProtectionPolicy(
                    identifierType = type,
                    mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                    normalization = NormalizationProfile.DID,
                )
            }

            "x509" -> {
                IdentifierProtectionPolicy(
                    identifierType = type,
                    mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                    normalization = NormalizationProfile.NONE,
                )
            }

            "issuer", "oidc_issuer", "oid4vci_issuer" -> {
                IdentifierProtectionPolicy(
                    identifierType = type,
                    mode = IdentifierProtectionMode.PLAINTEXT,
                    normalization = NormalizationProfile.URL_HOST,
                )
            }

            else -> {
                IdentifierProtectionPolicy(
                    identifierType = type,
                    mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                    normalization = NormalizationProfile.NONE,
                )
            }
        }
}
