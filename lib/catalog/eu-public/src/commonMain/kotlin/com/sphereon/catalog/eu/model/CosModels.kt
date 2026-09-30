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

@Serializable
data class CatalogueOfSchemes(
    val info: CatalogueInformation,
    val schemes: List<SchemeEntryReference> = emptyList(),
    val hasSignature: Boolean = false,
)

@Serializable
data class SchemeEntryReference(
    val name: String,
    val identifier: String? = null,
    val registrationIdentifier: String? = null,
    val reference: EntryReference,
)

@Serializable
data class EaaSchemeEntry(
    val name: String,
    val owner: SchemeOwner,
    val identifier: String? = null,
    val versions: List<VersionedEaaScheme> = emptyList(),
    val registrationIdentifier: String? = null,
)

@Serializable
data class SchemeOwner(
    val name: InternationalNames,
    val identifier: String,
    val postalAddresses: List<PostalAddress> = emptyList(),
    val electronicAddress: ElectronicAddress = ElectronicAddress(),
)

@Serializable
data class VersionedEaaScheme(
    val version: String,
    val status: VersionStatus,
    val documentUri: String,
    val eaaTypes: List<EaaType> = emptyList(),
    val extensions: List<CatalogueExtension> = emptyList(),
)

@Serializable
data class EaaType(
    val identifier: String,
    val name: InternationalNames,
    val schemeDefinition: InternationalNames,
    val attributeReferences: List<AttributeReference> = emptyList(),
    val dataModelReference: String,
    val formatBindings: List<SchemeFormatBinding> = emptyList(),
    val trustModelTypes: List<String> = emptyList(),
    val extensions: List<CatalogueExtension> = emptyList(),
)

@Serializable
data class AttributeReference(
    val definitionPointer: String? = null,
    val cataloguePointer: CataloguePointerRef? = null,
    val namespace: String,
    val identifier: String,
    val uniqueIdentifier: String? = null,
    val version: String? = null,
)

@Serializable
data class CataloguePointerRef(
    val location: String,
    val catalogueIdentifier: String,
)

@Serializable
data class SchemeFormatBinding(
    val mediaType: String,
    val bindingDefinitionUri: String? = null,
    val bindingDefinitionDocumentUri: String? = null,
    val eaaTypeIdentifier: String? = null,
)
