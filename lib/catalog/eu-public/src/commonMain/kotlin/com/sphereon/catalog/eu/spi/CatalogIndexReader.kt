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

package com.sphereon.catalog.eu.spi

import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EaaType
import com.sphereon.core.api.IdkResult
import kotlinx.serialization.Serializable

/**
 * Selects the catalogue index to read. Tenant and domain always come from the request, never from ambient state.
 */
@Serializable
data class CatalogScope(
    val tenantId: String,
    val domainId: String,
    val sourceId: String? = null,
)

@Serializable
enum class CatalogOrigin {
    SYNCED,
    AUTHORED,
}

/**
 * The signer evidence field on each match holds the DER certificates that signed the catalogue revision the entry came from (for an authored
 * catalogue, the published signer). It is empty only when no signer exists, for example an unpublished authored draft.
 */
@Serializable
data class CatalogAttributeMatch(
    val origin: CatalogOrigin,
    val catalogueIdentifier: String,
    val namespace: String,
    val entry: AttributeEntry,
    val signerCertificates: List<ByteArray> = emptyList(),
)

@Serializable
data class CatalogSchemeMatch(
    val origin: CatalogOrigin,
    val catalogueIdentifier: String,
    val entry: EaaSchemeEntry,
    val signerCertificates: List<ByteArray> = emptyList(),
)

@Serializable
data class CatalogEaaTypeMatch(
    val origin: CatalogOrigin,
    val catalogueIdentifier: String,
    val schemeName: String,
    val schemeVersion: String,
    val eaaType: EaaType,
    val signerCertificates: List<ByteArray> = emptyList(),
)

/**
 * Read access to the active catalogue index of a trust domain, covering synced and authored catalogues. Implementations return null when nothing matches
 * and an error only when the index itself cannot be read.
 */
interface CatalogIndexReader {
    /**
     * @param catalogueIdentifier when given, only the entry of that catalogue matches, so an entry of another catalogue
     * with the same namespace and identifier cannot hide it.
     */
    suspend fun findAttribute(
        scope: CatalogScope,
        namespace: String,
        attributeIdentifier: String,
        catalogueIdentifier: String? = null,
    ): IdkResult<CatalogAttributeMatch?, CatalogError>

    suspend fun findAttributeByRegistrationIdentifier(
        scope: CatalogScope,
        registrationIdentifier: String,
    ): IdkResult<CatalogAttributeMatch?, CatalogError>

    suspend fun findScheme(
        scope: CatalogScope,
        schemeName: String,
    ): IdkResult<CatalogSchemeMatch?, CatalogError>

    suspend fun findSchemeByRegistrationIdentifier(
        scope: CatalogScope,
        registrationIdentifier: String,
    ): IdkResult<CatalogSchemeMatch?, CatalogError>

    /**
     * Finds the EAA types whose format binding carries [formatSpecificIdentifier] (an SD-JWT VC `vct` or an mdoc `docType`),
     * optionally narrowed by [mediaType].
     */
    suspend fun findEaaTypesByFormatIdentifier(
        scope: CatalogScope,
        formatSpecificIdentifier: String,
        mediaType: String? = null,
    ): IdkResult<List<CatalogEaaTypeMatch>, CatalogError>
}
