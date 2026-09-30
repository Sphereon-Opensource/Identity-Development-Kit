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

package com.sphereon.catalog.eu.model

import com.sphereon.trust.etsi.lote.model.LoTE
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The EU List of Catalogues: a TS 119 602 LoTE carrying the LoC tag, no trusted entities and three pointers.
 */
@Serializable
data class ListOfCatalogues(
    val lote: LoTE,
    val loteTag: String? = null,
    val pointers: List<CataloguePointer> = emptyList(),
    val nextUpdate: Instant,
    val hasSignature: Boolean = false,
    /** Qualified names of the `SchemeExtensions/Extension` entries marked critical. The LoC profile allows none. */
    val criticalSchemeExtensions: List<String> = emptyList(),
)

/**
 * One `OtherLoTEPointer` of the LoC. [signerCertificates] holds the base64 DER certificates of the authorised signers.
 */
@Serializable
data class CataloguePointer(
    val loteType: String? = null,
    val location: String,
    val signerCertificates: List<String> = emptyList(),
    val schemeTypeCommunityRules: List<String> = emptyList(),
    val schemeTerritory: String? = null,
    val mimeType: String? = null,
)

@Serializable
enum class FindingSeverity {
    ERROR,
    WARNING,
}

@Serializable
data class CatalogueFinding(
    val code: String,
    val severity: FindingSeverity,
    val path: String,
    val message: String,
)

@Serializable
data class SignerEvidence(
    val catalogueKind: CatalogueKind,
    val catalogueIdentifier: String,
    val sequenceNumber: Long,
    val certificateBase64: String,
    val signingTime: Instant? = null,
)

@Serializable
data class VerifiedCatalogueSet(
    val loc: ListOfCatalogues,
    val coa: CatalogueOfAttributes? = null,
    val cos: CatalogueOfSchemes? = null,
    val attributeEntries: List<AttributeEntry> = emptyList(),
    val schemeEntries: List<EaaSchemeEntry> = emptyList(),
    val signerEvidence: List<SignerEvidence> = emptyList(),
    val findings: List<CatalogueFinding> = emptyList(),
)

/**
 * A parsed catalogue document together with the exact bytes it was parsed from, which digest and signature
 * verification need.
 */
class ParsedCatalogueDocument<out T>(
    val value: T,
    val rawBytes: ByteArray,
)
