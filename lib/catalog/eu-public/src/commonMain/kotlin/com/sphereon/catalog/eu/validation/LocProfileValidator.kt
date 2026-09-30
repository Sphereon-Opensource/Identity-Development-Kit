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
import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.ListOfCatalogues
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Instant

/**
 * Checks a parsed [ListOfCatalogues] against the LoC profile of ETSI TS 119 602. The generic LoTE parser has
 * already produced the model, so this only enforces what the profile adds on top.
 */
class LocProfileValidator {
    /**
     * @param now when given, a NextUpdate in the past is reported as a finding.
     */
    fun validate(
        loc: ListOfCatalogues,
        now: Instant? = null,
    ): List<CatalogueFinding> {
        val f = FindingCollector()
        val info = loc.lote.listAndSchemeInformation
        val p = "ListAndSchemeInformation"

        if (loc.loteTag != EuCatalogueConstants.LOC_TAG) {
            f.error(CatalogueFindingCodes.LOC_TAG_INVALID, "ListOfTrustedEntities/@LOTETag", "Expected ${EuCatalogueConstants.LOC_TAG}, found ${loc.loteTag}")
        }
        if (info.versionIdentifier != EuCatalogueConstants.SUPPORTED_CATALOGUE_VERSION) {
            f.error(CatalogueFindingCodes.LOC_VERSION_INVALID, "$p/LoTEVersionIdentifier", "Expected ${EuCatalogueConstants.SUPPORTED_CATALOGUE_VERSION}, found ${info.versionIdentifier}")
        }
        if (info.sequenceNumber < 1) {
            f.error(CatalogueFindingCodes.LOC_SEQUENCE_INVALID, "$p/LoTESequenceNumber", "The sequence number must be positive")
        }
        if (info.type?.trim() != EuCatalogueConstants.LOC_TYPE) {
            f.error(CatalogueFindingCodes.LOC_TYPE_INVALID, "$p/LoTEType", "Expected ${EuCatalogueConstants.LOC_TYPE}, found ${info.type}")
        }
        if (info.schemeTerritory?.trim() != EuCatalogueConstants.TERRITORY_EU) {
            f.error(CatalogueFindingCodes.LOC_TERRITORY_INVALID, "$p/SchemeTerritory", "Expected ${EuCatalogueConstants.TERRITORY_EU}, found ${info.schemeTerritory}")
        }
        if (info.statusDeterminationApproach?.trim() != EuCatalogueConstants.LOC_STATUS_DETERMINATION_APPROACH) {
            f.error(CatalogueFindingCodes.LOC_STATUS_APPROACH_INVALID, "$p/StatusDeterminationApproach", "Expected ${EuCatalogueConstants.LOC_STATUS_DETERMINATION_APPROACH}")
        }
        if (info.schemeTypeCommunityRules.none { it.uriValue.trim() == EuCatalogueConstants.LOC_SCHEME_TYPE_COMMUNITY_RULES }) {
            f.error(CatalogueFindingCodes.LOC_COMMUNITY_RULES_INVALID, "$p/SchemeTypeCommunityRules", "Expected ${EuCatalogueConstants.LOC_SCHEME_TYPE_COMMUNITY_RULES}")
        }
        if (info.historicalInformationPeriod != null) {
            f.error(CatalogueFindingCodes.LOC_HISTORICAL_PERIOD_PRESENT, "$p/HistoricalInformationPeriod", "HistoricalInformationPeriod must be absent")
        }
        if (loc.lote.trustedEntitiesList.isNotEmpty()) {
            f.error(CatalogueFindingCodes.LOC_TRUSTED_ENTITIES_PRESENT, "TrustedEntitiesList", "TrustedEntitiesList must not be present")
        }

        loc.criticalSchemeExtensions.forEachIndexed { i, name ->
            f.error(CatalogueFindingCodes.LOC_CRITICAL_EXTENSION_PRESENT, "$p/SchemeExtensions/Extension[$i]", "The LoC profile allows no critical extension, found $name")
        }

        validatePointers(loc, f)

        val latest = info.listIssueDateTime.plus(EuCatalogueConstants.LOC_MAX_UPDATE_INTERVAL_MONTHS, DateTimeUnit.MONTH, TimeZone.UTC)
        if (loc.nextUpdate > latest) {
            f.error(CatalogueFindingCodes.LOC_NEXT_UPDATE_TOO_FAR, "$p/NextUpdate", "NextUpdate must be at most ${EuCatalogueConstants.LOC_MAX_UPDATE_INTERVAL_MONTHS} months after ListIssueDateTime")
        }
        if (now != null && loc.nextUpdate <= now) {
            f.error(CatalogueFindingCodes.LOC_NEXT_UPDATE_PASSED, "$p/NextUpdate", "NextUpdate ${loc.nextUpdate} has passed")
        }
        return f.findings
    }

    private fun validatePointers(
        loc: ListOfCatalogues,
        f: FindingCollector,
    ) {
        val path = "ListAndSchemeInformation/PointersToOtherLoTE"
        val expected = listOf(EuCatalogueConstants.LOC_TYPE, EuCatalogueConstants.COA_LOTE_TYPE, EuCatalogueConstants.COS_LOTE_TYPE)
        if (loc.pointers.size != expected.size) {
            f.error(CatalogueFindingCodes.LOC_POINTERS_INVALID, path, "Expected ${expected.size} pointers, found ${loc.pointers.size}")
        }
        for (type in expected) {
            val count = loc.pointers.count { it.loteType == type }
            if (count != 1) {
                f.error(CatalogueFindingCodes.LOC_POINTERS_INVALID, path, "Expected exactly one pointer of type $type, found $count")
            }
        }
        loc.pointers.forEachIndexed { i, pointer ->
            val pp = "$path/OtherLoTEPointer[$i]"
            if (pointer.signerCertificates.isEmpty()) {
                f.error(CatalogueFindingCodes.LOC_POINTER_SIGNERS_MISSING, "$pp/ServiceDigitalIdentities", "The pointer lists no authorised signer certificates")
            }
            val location = pointer.location.trim().lowercase()
            if (!location.startsWith("https://") && !location.startsWith("http://")) {
                f.error(CatalogueFindingCodes.LOC_POINTER_LOCATION_INVALID, "$pp/LoTELocation", "Expected an http or https location, found ${pointer.location}")
            }
        }
    }
}
