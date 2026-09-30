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
data class CatalogueOfAttributes(
    val info: CatalogueInformation,
    val namespaces: List<AttributeNamespace> = emptyList(),
    val hasSignature: Boolean = false,
)

@Serializable
data class AttributeNamespace(
    val identifier: String,
    val entries: List<NamespaceEntry> = emptyList(),
)

@Serializable
data class NamespaceEntry(
    val attributeIdentifier: String,
    val registrationIdentifier: String? = null,
    val reference: EntryReference,
)

@Serializable
data class AttributeEntry(
    val attributeIdentifier: String,
    val registrationIdentifier: String? = null,
    val referenceBody: ReferenceBody,
    val versions: List<VersionedAttribute> = emptyList(),
    val eidasAnnexVIAttributeType: Int? = null,
    val extensions: List<CatalogueExtension> = emptyList(),
)

@Serializable
data class ReferenceBody(
    val name: InternationalNames,
    val website: String,
)

@Serializable
data class VersionedAttribute(
    val version: String,
    val status: VersionStatus? = null,
    val information: AttributeInformation,
)

@Serializable
data class VersionStatus(
    val statusUri: String,
    val startingAt: Instant? = null,
    val linked: LinkedVersion? = null,
)

@Serializable
data class LinkedVersion(
    val catalogueIdentifier: String? = null,
    val namespace: String? = null,
    val identifier: String? = null,
    val registrationIdentifier: String? = null,
    val name: String? = null,
    val version: String,
)

@Serializable
data class AttributeInformation(
    val name: InternationalNames,
    val semanticDescription: SemanticDescription,
    val dataType: AttributeDataType,
    val authenticSources: List<AuthenticSource> = emptyList(),
    val extensions: List<CatalogueExtension> = emptyList(),
)

@Serializable
data class SemanticDescription(
    val description: InternationalNames,
    val pointer: String? = null,
)

@Serializable
data class AttributeDataType(
    val dataType: String,
    val hasSpecification: Boolean = false,
    val specificationPointer: String? = null,
    val formatBindings: List<AttributeFormatBinding> = emptyList(),
)

@Serializable
data class AttributeFormatBinding(
    val mediaType: String,
    val bindingDefinitionUri: String? = null,
    val formatSpecificIdentifier: String,
)

@Serializable
data class AuthenticSource(
    val name: InternationalNames? = null,
    val territory: String? = null,
    val identifier: String,
    val verificationEndpoints: List<VerificationEndpoint> = emptyList(),
    val verificationAccessDescriptionUri: String? = null,
    val sourceAttributeIdentifier: String? = null,
)

@Serializable
data class VerificationEndpoint(
    val uri: String,
    val description: InternationalNames,
    val responseFormats: List<String> = emptyList(),
)
