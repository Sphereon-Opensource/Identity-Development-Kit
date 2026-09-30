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

package com.sphereon.catalog.eu.validation

import com.sphereon.catalog.eu.EuCatalogueConstants

/**
 * Which origin-specific rules the conformance validator applies. The models are origin neutral; the fixed EU
 * identifiers, the EU territory and the Commission-issued registration identifiers only apply to the EU profiles.
 *
 * @property expectedIdentifier the exact catalogue identifier, or null when any non-empty identifier is acceptable.
 * @property forbiddenIdentifiers identifiers that this catalogue must not use.
 * @property expectedTerritory the exact territory, or null when any non-empty territory is acceptable.
 * @property requireHistoryForever whether HistoricalInformationPeriod must be the "keep forever" value.
 * @property requireSignature whether the enveloped signature must be present. Authored catalogues are checked
 * unsigned before publishing.
 * @property requireRegistrationIdentifier whether entries must carry a registration identifier.
 */
data class CatalogueProfile(
    val expectedIdentifier: String? = null,
    val forbiddenIdentifiers: Set<String> = emptySet(),
    val expectedTerritory: String? = null,
    val requireHistoryForever: Boolean = false,
    val requireSignature: Boolean = false,
    val requireRegistrationIdentifier: Boolean = false,
) {
    companion object {
        val EU_COA =
            CatalogueProfile(
                expectedIdentifier = EuCatalogueConstants.COA_IDENTIFIER,
                expectedTerritory = EuCatalogueConstants.TERRITORY_EU,
                requireHistoryForever = true,
                requireSignature = true,
                requireRegistrationIdentifier = true,
            )

        val EU_COS = EU_COA.copy(expectedIdentifier = EuCatalogueConstants.COS_IDENTIFIER)

        val RESERVED_IDENTIFIERS: Set<String> = setOf(EuCatalogueConstants.COA_IDENTIFIER, EuCatalogueConstants.COS_IDENTIFIER)

        /**
         * Profile for tenant-authored catalogues and for catalogues synced from other operators. Reserved EU
         * identifiers are refused so a custom catalogue can never impersonate the EU ones.
         */
        fun custom(requireSignature: Boolean = false): CatalogueProfile =
            CatalogueProfile(forbiddenIdentifiers = RESERVED_IDENTIFIERS, requireSignature = requireSignature)
    }
}
