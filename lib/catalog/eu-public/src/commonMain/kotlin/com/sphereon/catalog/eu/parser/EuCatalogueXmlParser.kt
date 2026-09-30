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

package com.sphereon.catalog.eu.parser

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.AttributeDataType
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AttributeFormatBinding
import com.sphereon.catalog.eu.model.AttributeInformation
import com.sphereon.catalog.eu.model.AttributeNamespace
import com.sphereon.catalog.eu.model.AttributeReference
import com.sphereon.catalog.eu.model.AuthenticSource
import com.sphereon.catalog.eu.model.CatalogueDistributionPoint
import com.sphereon.catalog.eu.model.CatalogueExtension
import com.sphereon.catalog.eu.model.CatalogueInformation
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOfSchemes
import com.sphereon.catalog.eu.model.CatalogueOperator
import com.sphereon.catalog.eu.model.CataloguePointer
import com.sphereon.catalog.eu.model.CataloguePointerRef
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EaaType
import com.sphereon.catalog.eu.model.ElectronicAddress
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.LinkedVersion
import com.sphereon.catalog.eu.model.ListOfCatalogues
import com.sphereon.catalog.eu.model.MultiLangString
import com.sphereon.catalog.eu.model.NamespaceEntry
import com.sphereon.catalog.eu.model.ParsedCatalogueDocument
import com.sphereon.catalog.eu.model.PostalAddress
import com.sphereon.catalog.eu.model.ReferenceBody
import com.sphereon.catalog.eu.model.SchemeEntryReference
import com.sphereon.catalog.eu.model.SchemeFormatBinding
import com.sphereon.catalog.eu.model.SchemeOwner
import com.sphereon.catalog.eu.model.SemanticDescription
import com.sphereon.catalog.eu.model.VerificationEndpoint
import com.sphereon.catalog.eu.model.VersionStatus
import com.sphereon.catalog.eu.model.VersionedAttribute
import com.sphereon.catalog.eu.model.VersionedEaaScheme
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.trust.etsi.lote.serialization.LoTEXml
import kotlin.time.Instant

/**
 * Parses the EU catalogue documents. Each result keeps the exact source bytes because entry digests and
 * enveloped signatures are computed over them.
 */
interface EuCatalogueXmlParser {
    fun parseCoa(xml: ByteArray): IdkResult<ParsedCatalogueDocument<CatalogueOfAttributes>, CatalogError>

    fun parseAttributeEntry(xml: ByteArray): IdkResult<ParsedCatalogueDocument<AttributeEntry>, CatalogError>

    fun parseCos(xml: ByteArray): IdkResult<ParsedCatalogueDocument<CatalogueOfSchemes>, CatalogError>

    fun parseSchemeEntry(xml: ByteArray): IdkResult<ParsedCatalogueDocument<EaaSchemeEntry>, CatalogError>

    fun parseLoc(xml: ByteArray): IdkResult<ParsedCatalogueDocument<ListOfCatalogues>, CatalogError>
}

class DefaultEuCatalogueXmlParser : EuCatalogueXmlParser {
    override fun parseCoa(xml: ByteArray) =
        parseDocument(xml, "CatalogueOfAttributes", EuCatalogueConstants.COA_NAMESPACE) { root ->
            val nsList = root.child("Namespaces")?.all("Namespace").orEmpty()
            CatalogueOfAttributes(
                info = parseInfo(root.required("CatalogueInformation", ""), "CatalogueInformation"),
                namespaces = nsList.mapIndexed { i, n -> parseNamespace(n, "Namespaces/Namespace[$i]") },
                hasSignature = root.hasSignature(),
            )
        }

    override fun parseAttributeEntry(xml: ByteArray) =
        parseDocument(xml, "AttributeEntry", EuCatalogueConstants.COA_NAMESPACE) { root -> parseAttributeEntryNode(root) }

    override fun parseCos(xml: ByteArray) =
        parseDocument(xml, "CatalogueOfSchemes", EuCatalogueConstants.COS_NAMESPACE) { root ->
            val refs = root.child("EAASchemeList")?.all("EAASchemeEntryReference").orEmpty()
            CatalogueOfSchemes(
                info = parseInfo(root.required("CatalogueInformation", ""), "CatalogueInformation"),
                schemes = refs.mapIndexed { i, r -> parseSchemeReference(r, "EAASchemeList/EAASchemeEntryReference[$i]") },
                hasSignature = root.hasSignature(),
            )
        }

    override fun parseSchemeEntry(xml: ByteArray) =
        parseDocument(xml, "EAASchemeEntry", EuCatalogueConstants.COS_NAMESPACE) { root -> parseSchemeEntryNode(root) }

    override fun parseLoc(xml: ByteArray): IdkResult<ParsedCatalogueDocument<ListOfCatalogues>, CatalogError> {
        val text = xml.decodeToString().removePrefix("﻿")
        return try {
            val root = parseXmlDocument(text)
            if (root.name != "ListOfTrustedEntities" || root.ns != EuCatalogueConstants.LOTE_NAMESPACE) {
                return failure(CatalogErrorCode.UNEXPECTED_ROOT, "Expected ListOfTrustedEntities in ${EuCatalogueConstants.LOTE_NAMESPACE}, found ${root.name}")
            }
            val lote = LoTEXml.parse(text)
            val info = root.child("ListAndSchemeInformation")
            val pointerNodes = info?.child("PointersToOtherLoTE")?.all("OtherLoTEPointer").orEmpty()
            val loc =
                ListOfCatalogues(
                    lote = lote,
                    loteTag = root.attr("LOTETag"),
                    pointers = pointerNodes.map { parsePointer(it) },
                    nextUpdate = lote.listAndSchemeInformation.nextUpdate,
                    hasSignature = root.hasSignature(),
                    criticalSchemeExtensions = criticalSchemeExtensions(info),
                )
            IdkOkResult(ParsedCatalogueDocument(loc, xml))
        } catch (e: SchemaViolation) {
            failure(CatalogErrorCode.SCHEMA_VIOLATION, e.reason, e.path)
        } catch (e: IllegalArgumentException) {
            failure(CatalogErrorCode.SCHEMA_VIOLATION, e.message ?: "Invalid List of Catalogues")
        } catch (e: Exception) {
            failure(CatalogErrorCode.MALFORMED_XML, e.message ?: "Malformed XML")
        }
    }

    private fun criticalSchemeExtensions(info: XmlNode?): List<String> =
        info
            ?.child("SchemeExtensions")
            ?.all("Extension")
            .orEmpty()
            .filter { e ->
                val flag = (e.attr("Critical") ?: e.childText("Critical"))?.trim()?.lowercase()
                flag == "true" || flag == "1"
            }.map { e ->
                val value = e.children.firstOrNull { it.name != "Critical" }
                if (value != null) "{${value.ns}}${value.name}" else "Extension"
            }

    private fun <T> parseDocument(
        xml: ByteArray,
        rootName: String,
        rootNamespace: String,
        build: (XmlNode) -> T,
    ): IdkResult<ParsedCatalogueDocument<T>, CatalogError> {
        val root =
            try {
                parseXmlDocument(xml.decodeToString())
            } catch (e: Exception) {
                return failure(CatalogErrorCode.MALFORMED_XML, e.message ?: "Malformed XML")
            }
        if (root.name != rootName || root.ns != rootNamespace) {
            return failure(CatalogErrorCode.UNEXPECTED_ROOT, "Expected $rootName in $rootNamespace, found ${root.name} in ${root.ns}")
        }
        return try {
            IdkOkResult(ParsedCatalogueDocument(build(root), xml))
        } catch (e: SchemaViolation) {
            failure(CatalogErrorCode.SCHEMA_VIOLATION, e.reason, e.path)
        }
    }

    private fun <T> failure(
        code: CatalogErrorCode,
        reason: String,
        path: String? = null,
    ): IdkResult<T, CatalogError> = IdkErrorResult(CatalogError(code, reason, path))

    private fun parseInfo(
        n: XmlNode,
        path: String,
    ): CatalogueInformation =
        CatalogueInformation(
            version = n.requiredText("CatalogueVersion", path).toIntOrViolation("$path/CatalogueVersion"),
            sequenceNumber = n.requiredText("CatalogueSequenceNumber", path).toLongOrViolation("$path/CatalogueSequenceNumber"),
            identifier = n.requiredText("CatalogueIdentifier", path),
            name = parseNames(n.required("CatalogueName", path)),
            operator = parseOperator(n.required("CatalogueOperator", path), "$path/CatalogueOperator"),
            informationUri = n.requiredText("CatalogueInformationURI", path),
            statusDeterminationApproach = n.childText("CatalogueEntriesStatusDeterminationApproach"),
            territory = n.requiredText("CatalogueTerritory", path),
            legalNotice = n.requiredText("CatalogueLegalNotice", path),
            historicalInformationPeriod = n.requiredText("HistoricalInformationPeriod", path).toIntOrViolation("$path/HistoricalInformationPeriod"),
            issueDateTime = n.requiredText("CatalogueIssueDateAndTime", path).toInstantOrViolation("$path/CatalogueIssueDateAndTime"),
            distributionPoint = parseDistributionPoint(n.required("CatalogueDistributionPoint", path)),
            extensions = parseExtensions(n.child("CatalogueExtensions"), "$path/CatalogueExtensions").map { it.extension },
        )

    private fun parseOperator(
        n: XmlNode,
        path: String,
    ): CatalogueOperator {
        val address = n.required("CatalogueOperatorAddress", path)
        return CatalogueOperator(
            name = parseNames(n.required("CatalogueOperatorName", path)),
            identifier = n.requiredText("CatalogueOperatorIdentifier", path),
            postalAddresses = parsePostalAddresses(address),
            electronicAddress = parseElectronicAddress(address),
        )
    }

    private fun parsePostalAddresses(holder: XmlNode): List<PostalAddress> {
        val list = holder.child("PostalAddresses") ?: throw SchemaViolation("${holder.name}/PostalAddresses", "Required element PostalAddresses is missing")
        if (list.all("PostalAddress").isEmpty()) throw SchemaViolation("${holder.name}/PostalAddresses", "PostalAddresses needs at least one PostalAddress")
        return list.all("PostalAddress").map { a ->
            PostalAddress(
                lang = a.lang().orEmpty(),
                street = a.requiredText("StreetAddress", "PostalAddress"),
                locality = a.requiredText("Locality", "PostalAddress"),
                stateOrProvince = a.childText("StateOrProvince"),
                postalCode = a.childText("PostalCode"),
                country = a.requiredText("Country", "PostalAddress"),
            )
        }
    }

    private fun parseElectronicAddress(holder: XmlNode): ElectronicAddress =
        ElectronicAddress(holder.child("ElectronicAddress")?.all("URI").orEmpty().map { it.text })

    private fun parseDistributionPoint(n: XmlNode) =
        CatalogueDistributionPoint(
            homePage = n.childText("HomePage"),
            landingPage = n.childText("LandingPage"),
            downloadUrl = n.childText("DownloadURL"),
            accessUrl = n.childText("AccessURL"),
        )

    private fun parseNames(n: XmlNode): InternationalNames =
        InternationalNames(n.all("Name").map { MultiLangString(it.lang().orEmpty(), it.text) })

    private class ParsedExtension(
        val extension: CatalogueExtension,
        val value: XmlNode,
    )

    private fun parseExtensions(
        list: XmlNode?,
        path: String,
    ): List<ParsedExtension> =
        list?.all("Extension").orEmpty().mapIndexed { i, e ->
            val p = "$path/Extension[$i]"
            val critical =
                when (e.requiredText("Critical", p).lowercase()) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> throw SchemaViolation("$p/Critical", "Critical must be an xsd:boolean")
                }
            val value = e.children.firstOrNull { it.name != "Critical" } ?: throw SchemaViolation(p, "Extension has no value element")
            ParsedExtension(CatalogueExtension(critical, "{${value.ns}}${value.name}", value.toXml()), value)
        }

    private fun parseReference(
        n: XmlNode,
        path: String,
    ): EntryReference {
        val ref = n.required("Reference", path)
        val p = "$path/Reference"
        val digestText = ref.requiredText("DigestValue", p).filterNot { it.isWhitespace() }
        val digest =
            try {
                digestText.decodeFromBase64()
            } catch (e: Exception) {
                throw SchemaViolation("$p/DigestValue", "DigestValue is not valid base64")
            }
        return EntryReference(
            uri = ref.attr("URI") ?: throw SchemaViolation(p, "Reference has no URI attribute"),
            transforms = ref.child("Transforms")?.all("Transform").orEmpty().mapNotNull { it.attr("Algorithm") },
            digestMethod = ref.required("DigestMethod", p).attr("Algorithm") ?: throw SchemaViolation("$p/DigestMethod", "DigestMethod has no Algorithm"),
            digestValue = digest,
        )
    }

    private fun parseNamespace(
        n: XmlNode,
        path: String,
    ): AttributeNamespace =
        AttributeNamespace(
            identifier = n.requiredText("NamespaceIdentifier", path),
            entries =
                n.required("NamespaceEntries", path).all("NamespaceEntry").mapIndexed { i, e ->
                    val p = "$path/NamespaceEntries/NamespaceEntry[$i]"
                    NamespaceEntry(
                        attributeIdentifier = e.requiredText("AttributeIdentifier", p),
                        registrationIdentifier = e.childText("AttributeRegistrationIdentifier"),
                        reference = parseReference(e, p),
                    )
                },
        )

    private fun parseAttributeEntryNode(n: XmlNode): AttributeEntry {
        val body = n.required("ReferenceBody", "")
        val versions = n.required("VersionedAttributeList", "").all("VersionedAttribute")
        val extensions = parseExtensions(n.child("AttributeExtensions"), "AttributeExtensions")
        val annex =
            extensions
                .firstOrNull { it.extension.qName == "{${EuCatalogueConstants.COA_NAMESPACE}}${EuCatalogueConstants.EXTENSION_EIDAS_ANNEX_VI_ATTRIBUTE}" }
                ?.value
                ?.childText("eIDASAnnexVIAttributeType")
                ?.toIntOrViolation("AttributeExtensions/eIDASAnnexVIAttribute/eIDASAnnexVIAttributeType")
        return AttributeEntry(
            attributeIdentifier = n.requiredText("AttributeIdentifier", ""),
            registrationIdentifier = n.childText("AttributeRegistrationIdentifier"),
            referenceBody = ReferenceBody(parseNames(body.required("ReferenceBodyName", "ReferenceBody")), body.requiredText("ReferenceBodyWebsite", "ReferenceBody")),
            versions = versions.mapIndexed { i, v -> parseVersionedAttribute(v, "VersionedAttributeList/VersionedAttribute[$i]") },
            eidasAnnexVIAttributeType = annex,
            extensions = extensions.map { it.extension },
        )
    }

    private fun parseVersionedAttribute(
        n: XmlNode,
        path: String,
    ): VersionedAttribute =
        VersionedAttribute(
            version = n.requiredText("AttributeVersion", path),
            status =
                n.child("VersionedAttributeStatusInformation")?.let {
                    parseStatus(it, "VersionedAttributeStatus", "LinkedAttributeAndVersion", "$path/VersionedAttributeStatusInformation") { l ->
                        LinkedVersion(
                            catalogueIdentifier = l.childText("CatalogueIdentifier"),
                            namespace = l.childText("LinkedAttributeNamespace"),
                            identifier = l.childText("LinkedAttributeIdentifier"),
                            registrationIdentifier = l.childText("LinkedAttributeRegistrationIdentifier"),
                            version = l.requiredText("LinkedAttributeVersion", "$path/LinkedAttributeAndVersion"),
                        )
                    }
                },
            information = parseAttributeInformation(n.required("AttributeInformation", path), "$path/AttributeInformation"),
        )

    private fun parseStatus(
        n: XmlNode,
        statusElement: String,
        linkedElement: String,
        path: String,
        linked: (XmlNode) -> LinkedVersion,
    ) = VersionStatus(
        statusUri = n.requiredText(statusElement, path),
        startingAt = n.childText("StatusStartingDateAndTime")?.toInstantOrViolation("$path/StatusStartingDateAndTime"),
        linked = n.child(linkedElement)?.let(linked),
    )

    private fun parseAttributeInformation(
        n: XmlNode,
        path: String,
    ): AttributeInformation {
        val semantic = n.required("AttributeSemanticDescription", path)
        val type = n.required("AttributeDataType", path)
        val spec = type.child("DataTypeSpecification")
        return AttributeInformation(
            name = parseNames(n.required("AttributeName", path)),
            semanticDescription =
                SemanticDescription(
                    description = parseNames(semantic.required("SemanticDescription", "$path/AttributeSemanticDescription")),
                    pointer = semantic.childText("SemanticDescriptionPointer"),
                ),
            dataType =
                AttributeDataType(
                    dataType = type.requiredText("DataType", "$path/AttributeDataType"),
                    hasSpecification = spec != null,
                    specificationPointer = spec?.childText("SpecificationPointer"),
                    formatBindings =
                        spec?.child("FormatSyntaxBindings")?.all("FormatSyntaxBinding").orEmpty().map { b ->
                            AttributeFormatBinding(
                                mediaType = b.requiredText("MediaType", "FormatSyntaxBinding"),
                                bindingDefinitionUri = b.childText("BindingDefinitionURI"),
                                formatSpecificIdentifier = b.requiredText("FormatSyntaxSpecificIdentifier", "FormatSyntaxBinding"),
                            )
                        },
                ),
            authenticSources = n.required("AuthenticSources", path).all("AuthenticSource").map { parseAuthenticSource(it) },
            extensions = parseExtensions(n.child("AttributeInformationExtensions"), "$path/AttributeInformationExtensions").map { it.extension },
        )
    }

    private fun parseAuthenticSource(n: XmlNode): AuthenticSource {
        val access = n.required("AuthenticSourceVerificationAccess", "AuthenticSource")
        return AuthenticSource(
            name = n.child("AuthenticSourceName")?.let { parseNames(it) },
            territory = n.childText("AuthenticSourceTerritory"),
            identifier = n.requiredText("AuthenticSourceIdentifier", "AuthenticSource"),
            verificationEndpoints =
                access.child("VerificationEndpointsList")?.all("VerificationEndpoint").orEmpty().map { e ->
                    VerificationEndpoint(
                        uri = e.requiredText("VerificationEndpointURI", "VerificationEndpoint"),
                        description = parseNames(e.required("VerificationEndpointDescription", "VerificationEndpoint")),
                        responseFormats = e.child("VerificationEndpointResponseFormats")?.all("MediaType").orEmpty().map { it.text },
                    )
                },
            verificationAccessDescriptionUri = access.childText("VerificationAccessDescriptionURI"),
            sourceAttributeIdentifier = n.childText("AuthenticSourceAttributeIdentifier"),
        )
    }

    private fun parseSchemeReference(
        n: XmlNode,
        path: String,
    ) = SchemeEntryReference(
        name = n.requiredText("EAASchemeName", path),
        identifier = n.childText("EAASchemeIdentifier"),
        registrationIdentifier = n.childText("EAASchemeRegistrationIdentifier"),
        reference = parseReference(n, path),
    )

    private fun parseSchemeEntryNode(n: XmlNode): EaaSchemeEntry {
        val owner = n.required("EAASchemeOwnerInformation", "")
        val address = owner.required("EAASchemeOwnerAddress", "EAASchemeOwnerInformation")
        val versions = n.required("VersionedEAASchemeList", "").all("VersionedEAAScheme")
        return EaaSchemeEntry(
            name = n.requiredText("EAASchemeName", ""),
            owner =
                SchemeOwner(
                    name = parseNames(owner.required("EAASchemeOwnerName", "EAASchemeOwnerInformation")),
                    identifier = owner.requiredText("EAASchemeOwnerIdentifier", "EAASchemeOwnerInformation"),
                    postalAddresses = parsePostalAddresses(address),
                    electronicAddress = parseElectronicAddress(address),
                ),
            identifier = n.childText("EAASchemeIdentifier"),
            versions = versions.mapIndexed { i, v -> parseVersionedScheme(v, "VersionedEAASchemeList/VersionedEAAScheme[$i]") },
            registrationIdentifier = n.childText("EAASchemeRegistrationIdentifier"),
        )
    }

    private fun parseVersionedScheme(
        n: XmlNode,
        path: String,
    ): VersionedEaaScheme =
        VersionedEaaScheme(
            version = n.requiredText("EAASchemeVersion", path),
            status =
                parseStatus(n.required("VersionedEAASchemeStatusInformation", path), "VersionedEAASchemeStatus", "LinkedEAASchemeAndVersion", "$path/VersionedEAASchemeStatusInformation") { l ->
                    LinkedVersion(
                        catalogueIdentifier = l.childText("CatalogueIdentifier"),
                        name = l.childText("LinkedEAASchemeName"),
                        identifier = l.childText("LinkedEAASchemeIdentifier"),
                        version = l.requiredText("LinkedEAASchemeVersion", "$path/LinkedEAASchemeAndVersion"),
                    )
                },
            documentUri = n.requiredText("EAASchemeDocumentURI", path),
            eaaTypes = n.required("EAATypeList", path).all("EAAType").mapIndexed { i, t -> parseEaaType(t, "$path/EAATypeList/EAAType[$i]") },
            extensions = parseExtensions(n.child("VersionedEAASchemeExtensions"), "$path/VersionedEAASchemeExtensions").map { it.extension },
        )

    private fun parseEaaType(
        n: XmlNode,
        path: String,
    ): EaaType {
        val extensions = parseExtensions(n.child("EAATypeExtensions"), "$path/EAATypeExtensions")
        val trustModels =
            extensions
                .filter { it.extension.qName == "{${EuCatalogueConstants.COS_NAMESPACE}}${EuCatalogueConstants.EXTENSION_EAA_TYPE_TRUST_MODEL_TYPES}" }
                .flatMap { it.value.all("EAATypeTrustModelType").map { t -> t.text } }
        return EaaType(
            identifier = n.requiredText("EAATypeIdentifier", path),
            name = parseNames(n.required("EAATypeName", path)),
            schemeDefinition = parseNames(n.required("EAATypeSchemeDefinition", path)),
            attributeReferences = n.required("EAATypeAttributeReferences", path).all("AttributeReference").map { parseAttributeReference(it, path) },
            dataModelReference = n.requiredText("EAATypeDataModelReference", path),
            formatBindings =
                n.required("FormatSyntaxBindings", path).all("FormatSyntaxBinding").map { b ->
                    SchemeFormatBinding(
                        mediaType = b.requiredText("MediaType", "$path/FormatSyntaxBinding"),
                        bindingDefinitionUri = b.childText("BindingDefinitionURI"),
                        bindingDefinitionDocumentUri = b.childText("BindingDefinitionDocumentURI"),
                        eaaTypeIdentifier = b.childText("FormatSyntaxSpecificEAATypeIdentifier"),
                    )
                },
            trustModelTypes = trustModels,
            extensions = extensions.map { it.extension },
        )
    }

    private fun parseAttributeReference(
        n: XmlNode,
        path: String,
    ) = AttributeReference(
        definitionPointer = n.childText("AttributeDefinitionPointer"),
        cataloguePointer =
            n.child("CataloguePointer")?.let {
                CataloguePointerRef(
                    location = it.requiredText("CatalogueLocation", "$path/CataloguePointer"),
                    catalogueIdentifier = it.requiredText("CatalogueIdentifier", "$path/CataloguePointer"),
                )
            },
        namespace = n.requiredText("AttributeNamespace", "$path/AttributeReference"),
        identifier = n.requiredText("AttributeIdentifier", "$path/AttributeReference"),
        uniqueIdentifier = n.childText("AttributeUniqueIdentifier"),
        version = n.childText("AttributeVersion"),
    )

    private fun parsePointer(n: XmlNode): CataloguePointer {
        var type: String? = null
        var territory: String? = null
        var mime: String? = null
        val rules = mutableListOf<String>()
        n.child("AdditionalInformation")?.all("OtherInformation")?.forEach { info ->
            val entry = info.children.firstOrNull() ?: return@forEach
            when (entry.name) {
                "LoTEType" -> type = entry.text
                "SchemeTerritory" -> territory = entry.text
                "MimeType" -> mime = entry.text
                "SchemeTypeCommunityRules" -> rules.addAll(entry.all("URI").map { it.text })
            }
        }
        return CataloguePointer(
            loteType = type,
            location = n.requiredText("LoTELocation", "OtherLoTEPointer"),
            signerCertificates = n.child("ServiceDigitalIdentities")?.all("ServiceDigitalIdentity").orEmpty().flatMap { certificates(it) },
            schemeTypeCommunityRules = rules,
            schemeTerritory = territory,
            mimeType = mime,
        )
    }

    private fun certificates(identity: XmlNode): List<String> {
        val fromDigitalId =
            identity.all("DigitalId").mapNotNull { id ->
                val nested = id.child("X509Certificate")
                when {
                    nested != null -> nested.text
                    id.children.isEmpty() && id.text.isNotBlank() -> id.text
                    else -> null
                }
            }
        return (fromDigitalId + identity.all("X509Certificate").map { it.text })
            .map { it.filterNot(Char::isWhitespace) }
            .filter { it.isNotEmpty() }
    }

    private fun XmlNode.hasSignature(): Boolean = children.any { it.name == "Signature" && it.ns == EuCatalogueConstants.XMLDSIG_NAMESPACE }

    private fun XmlNode.required(
        name: String,
        path: String,
    ): XmlNode = child(name) ?: throw SchemaViolation(if (path.isEmpty()) name else "$path/$name", "Required element $name is missing")

    private fun XmlNode.requiredText(
        name: String,
        path: String,
    ): String = required(name, path).text

    private fun String.toIntOrViolation(path: String): Int = toIntOrNull() ?: throw SchemaViolation(path, "Not a valid integer: $this")

    private fun String.toLongOrViolation(path: String): Long = toLongOrNull() ?: throw SchemaViolation(path, "Not a valid integer: $this")

    private fun String.toInstantOrViolation(path: String): Instant =
        try {
            Instant.parse(this)
        } catch (e: IllegalArgumentException) {
            throw SchemaViolation(path, "Not a valid xsd:dateTime: $this")
        }
}

private class SchemaViolation(
    val path: String,
    val reason: String,
) : RuntimeException("$reason (at $path)")
