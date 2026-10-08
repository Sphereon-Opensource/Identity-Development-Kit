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

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MultiLangString(
    val lang: String,
    val value: String,
)

@Serializable
data class InternationalNames(
    val names: List<MultiLangString> = emptyList(),
) {
    fun forLang(lang: String): String? = names.firstOrNull { it.lang.equals(lang, ignoreCase = true) }?.value

    fun hasLang(lang: String): Boolean = names.any { it.lang.equals(lang, ignoreCase = true) && it.value.isNotBlank() }
}

@Serializable
data class PostalAddress(
    val lang: String,
    val street: String,
    val locality: String,
    val stateOrProvince: String? = null,
    val postalCode: String? = null,
    val country: String,
)

@Serializable
data class ElectronicAddress(
    val uris: List<String> = emptyList(),
)

@Serializable
data class CatalogueOperator(
    val name: InternationalNames,
    val identifier: String,
    val postalAddresses: List<PostalAddress> = emptyList(),
    val electronicAddress: ElectronicAddress = ElectronicAddress(),
)

@Serializable
data class CatalogueDistributionPoint(
    val homePage: String? = null,
    val landingPage: String? = null,
    val downloadUrl: String? = null,
    val accessUrl: String? = null,
) {
    val hasAny: Boolean get() = listOf(homePage, landingPage, downloadUrl, accessUrl).any { !it.isNullOrBlank() }
}

/**
 * A catalogue extension. [qName] is `{namespace}localName` of the extension value element and [rawXml] is that
 * element serialized from the document.
 */
@Serializable
data class CatalogueExtension(
    val critical: Boolean,
    val qName: String,
    val rawXml: String,
)

@Serializable
data class CatalogueInformation(
    val version: Int,
    val sequenceNumber: Long,
    val identifier: String,
    val name: InternationalNames,
    val operator: CatalogueOperator,
    val informationUri: String,
    val statusDeterminationApproach: String? = null,
    val territory: String,
    val legalNotice: String,
    val historicalInformationPeriod: Int,
    val issueDateTime: Instant,
    val distributionPoint: CatalogueDistributionPoint,
    val extensions: List<CatalogueExtension> = emptyList(),
)

/**
 * The `ds:Reference` that binds an entry file to the signed main catalogue.
 */
@Serializable
data class EntryReference(
    val uri: String,
    val transforms: List<String> = emptyList(),
    val digestMethod: String,
    val digestValue: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is EntryReference &&
            uri == other.uri &&
            transforms == other.transforms &&
            digestMethod == other.digestMethod &&
            digestValue.contentEquals(other.digestValue)

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + transforms.hashCode()
        result = 31 * result + digestMethod.hashCode()
        result = 31 * result + digestValue.contentHashCode()
        return result
    }
}

@Serializable
enum class CatalogueKind {
    LOC,
    COA,
    COS,
}
