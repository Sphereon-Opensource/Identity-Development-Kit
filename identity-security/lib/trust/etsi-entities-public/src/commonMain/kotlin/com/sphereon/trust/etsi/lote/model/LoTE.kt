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
 *
 */

package com.sphereon.trust.etsi.lote.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * Root data model for ETSI TS 119 602 List of Trusted Entities (LoTE).
 *
 * Based on ETSI TS 119 602 (November 2025) specification.
 * Schema repository: https://forge.etsi.org/rep/esi/x19_60201_lists_of_trusted_entities
 */
@JsExportCompat
@Serializable
data class LoTE(
    @SerialName("ListAndSchemeInformation")
    val listAndSchemeInformation: ListAndSchemeInformation,
    @SerialName("TrustedEntitiesList")
    val trustedEntitiesList: List<TrustedEntity> = emptyList(),
)

/**
 * List and scheme information for a LoTE per ETSI TS 119 602.
 */
@JsExportCompat
@Serializable
data class ListAndSchemeInformation(
    @SerialName("LoTEVersionIdentifier")
    val versionIdentifier: Int,
    @SerialName("LoTESequenceNumber")
    val sequenceNumber: Int,
    @SerialName("LoTEType")
    val type: String? = null,
    @SerialName("SchemeOperatorName")
    val schemeOperatorName: List<MultiLangString>,
    @SerialName("SchemeOperatorAddress")
    val schemeOperatorAddress: OperatorAddress? = null,
    @SerialName("SchemeName")
    val schemeName: List<MultiLangString> = emptyList(),
    @SerialName("SchemeInformationURI")
    val schemeInformationURI: List<MultiLangURI> = emptyList(),
    @SerialName("StatusDeterminationApproach")
    val statusDeterminationApproach: String? = null,
    @SerialName("SchemeTypeCommunityRules")
    val schemeTypeCommunityRules: List<MultiLangURI> = emptyList(),
    @SerialName("SchemeTerritory")
    val schemeTerritory: String? = null,
    @SerialName("PolicyOrLegalNotice")
    val policyOrLegalNotice: List<PolicyOrLegalNoticeEntry> = emptyList(),
    @SerialName("HistoricalInformationPeriod")
    val historicalInformationPeriod: Int? = null,
    @SerialName("PointersToOtherLoTE")
    val pointersToOtherLoTE: List<OtherLoTEPointer> = emptyList(),
    @SerialName("ListIssueDateTime")
    val listIssueDateTime: Instant,
    @SerialName("NextUpdate")
    val nextUpdate: Instant,
    @SerialName("DistributionPoints")
    val distributionPoints: List<String> = emptyList(),
    @SerialName("SchemeExtensions")
    val schemeExtensions: List<JsonElement> = emptyList(),
)
