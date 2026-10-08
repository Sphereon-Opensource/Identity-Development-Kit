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

package com.sphereon.catalog.eu.serializer

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AttributeInformation
import com.sphereon.catalog.eu.model.AttributeReference
import com.sphereon.catalog.eu.model.AuthenticSource
import com.sphereon.catalog.eu.model.CatalogueExtension
import com.sphereon.catalog.eu.model.CatalogueInformation
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOfSchemes
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EaaType
import com.sphereon.catalog.eu.model.ElectronicAddress
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.PostalAddress
import com.sphereon.catalog.eu.model.VersionStatus
import com.sphereon.catalog.eu.model.VersionedAttribute
import com.sphereon.catalog.eu.model.VersionedEaaScheme
import com.sphereon.catalog.eu.parser.parseXmlDocument
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.encodeToBase64

/**
 * Writes the CoA and CoS documents and their entry files from the model, in the element order of the catalogue
 * XSDs. The output is unsigned; entry digests come from the [EntryReference]s of the index model and the enveloped
 * signature is added afterwards. Extensions are written back verbatim from [CatalogueExtension.rawXml].
 */
interface EuCatalogueXmlSerializer {
    fun serializeCoa(coa: CatalogueOfAttributes): IdkResult<String, CatalogError>

    fun serializeAttributeEntry(entry: AttributeEntry): IdkResult<String, CatalogError>

    fun serializeCos(cos: CatalogueOfSchemes): IdkResult<String, CatalogError>

    fun serializeSchemeEntry(entry: EaaSchemeEntry): IdkResult<String, CatalogError>
}

class DefaultEuCatalogueXmlSerializer : EuCatalogueXmlSerializer {
    override fun serializeCoa(coa: CatalogueOfAttributes): IdkResult<String, CatalogError> =
        write(EuCatalogueConstants.COA_NAMESPACE) {
            open("CatalogueOfAttributes", " xmlns=\"${EuCatalogueConstants.COA_NAMESPACE}\" xmlns:ds=\"${EuCatalogueConstants.XMLDSIG_NAMESPACE}\"")
            information(coa.info)
            if (coa.namespaces.isNotEmpty()) {
                open("Namespaces")
                for (ns in coa.namespaces) {
                    open("Namespace")
                    leaf("NamespaceIdentifier", ns.identifier)
                    open("NamespaceEntries")
                    for (entry in ns.entries) {
                        open("NamespaceEntry")
                        leaf("AttributeIdentifier", entry.attributeIdentifier)
                        entry.registrationIdentifier?.let { leaf("AttributeRegistrationIdentifier", it) }
                        reference(entry.reference)
                        close("NamespaceEntry")
                    }
                    close("NamespaceEntries")
                    close("Namespace")
                }
                close("Namespaces")
            }
            close("CatalogueOfAttributes")
        }

    override fun serializeCos(cos: CatalogueOfSchemes): IdkResult<String, CatalogError> =
        write(EuCatalogueConstants.COS_NAMESPACE) {
            open("CatalogueOfSchemes", " xmlns=\"${EuCatalogueConstants.COS_NAMESPACE}\" xmlns:ds=\"${EuCatalogueConstants.XMLDSIG_NAMESPACE}\"")
            information(cos.info)
            if (cos.schemes.isNotEmpty()) {
                open("EAASchemeList")
                for (scheme in cos.schemes) {
                    open("EAASchemeEntryReference")
                    leaf("EAASchemeName", scheme.name)
                    scheme.identifier?.let { leaf("EAASchemeIdentifier", it) }
                    scheme.registrationIdentifier?.let { leaf("EAASchemeRegistrationIdentifier", it) }
                    reference(scheme.reference)
                    close("EAASchemeEntryReference")
                }
                close("EAASchemeList")
            }
            close("CatalogueOfSchemes")
        }

    override fun serializeAttributeEntry(entry: AttributeEntry): IdkResult<String, CatalogError> =
        write(EuCatalogueConstants.COA_NAMESPACE) {
            open("AttributeEntry", " xmlns=\"${EuCatalogueConstants.COA_NAMESPACE}\"")
            leaf("AttributeIdentifier", entry.attributeIdentifier)
            entry.registrationIdentifier?.let { leaf("AttributeRegistrationIdentifier", it) }
            open("ReferenceBody")
            names("ReferenceBodyName", entry.referenceBody.name)
            leaf("ReferenceBodyWebsite", entry.referenceBody.website)
            close("ReferenceBody")
            open("VersionedAttributeList")
            entry.versions.forEach { versionedAttribute(it) }
            close("VersionedAttributeList")
            extensions("AttributeExtensions", withAnnexVi(entry.extensions, entry.eidasAnnexVIAttributeType))
            close("AttributeEntry")
        }

    override fun serializeSchemeEntry(entry: EaaSchemeEntry): IdkResult<String, CatalogError> =
        write(EuCatalogueConstants.COS_NAMESPACE) {
            open("EAASchemeEntry", " xmlns=\"${EuCatalogueConstants.COS_NAMESPACE}\"")
            leaf("EAASchemeName", entry.name)
            open("EAASchemeOwnerInformation")
            names("EAASchemeOwnerName", entry.owner.name)
            leaf("EAASchemeOwnerIdentifier", entry.owner.identifier)
            address("EAASchemeOwnerAddress", entry.owner.postalAddresses, entry.owner.electronicAddress)
            close("EAASchemeOwnerInformation")
            entry.identifier?.let { leaf("EAASchemeIdentifier", it) }
            open("VersionedEAASchemeList")
            entry.versions.forEach { versionedScheme(it) }
            close("VersionedEAASchemeList")
            entry.registrationIdentifier?.let { leaf("EAASchemeRegistrationIdentifier", it) }
            close("EAASchemeEntry")
        }

    private fun write(
        defaultNamespace: String,
        block: Writer.() -> Unit,
    ): IdkResult<String, CatalogError> =
        try {
            val writer = Writer(defaultNamespace)
            writer.line("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            writer.block()
            IdkOkResult(writer.toString())
        } catch (e: SerializationViolation) {
            IdkErrorResult(CatalogError(CatalogErrorCode.SCHEMA_VIOLATION, e.reason, e.path))
        }

    // ---- shared blocks ------------------------------------------------------------------------------------------

    private fun Writer.information(info: CatalogueInformation) {
        open("CatalogueInformation")
        leaf("CatalogueVersion", info.version.toString())
        leaf("CatalogueSequenceNumber", info.sequenceNumber.toString())
        leaf("CatalogueIdentifier", info.identifier)
        names("CatalogueName", info.name)
        open("CatalogueOperator")
        names("CatalogueOperatorName", info.operator.name)
        leaf("CatalogueOperatorIdentifier", info.operator.identifier)
        address("CatalogueOperatorAddress", info.operator.postalAddresses, info.operator.electronicAddress)
        close("CatalogueOperator")
        leaf("CatalogueInformationURI", info.informationUri)
        info.statusDeterminationApproach?.let { leaf("CatalogueEntriesStatusDeterminationApproach", it) }
        leaf("CatalogueTerritory", info.territory)
        leaf("CatalogueLegalNotice", info.legalNotice)
        leaf("HistoricalInformationPeriod", info.historicalInformationPeriod.toString())
        leaf("CatalogueIssueDateAndTime", info.issueDateTime.toString())
        open("CatalogueDistributionPoint")
        info.distributionPoint.homePage?.let { leaf("HomePage", it) }
        info.distributionPoint.landingPage?.let { leaf("LandingPage", it) }
        info.distributionPoint.downloadUrl?.let { leaf("DownloadURL", it) }
        info.distributionPoint.accessUrl?.let { leaf("AccessURL", it) }
        close("CatalogueDistributionPoint")
        extensions("CatalogueExtensions", info.extensions)
        close("CatalogueInformation")
    }

    private fun Writer.reference(ref: EntryReference) {
        open("ds:Reference", " URI=\"${escape(ref.uri)}\"")
        if (ref.transforms.isNotEmpty()) {
            open("ds:Transforms")
            ref.transforms.forEach { line("<ds:Transform Algorithm=\"${escape(it)}\"/>") }
            close("ds:Transforms")
        }
        line("<ds:DigestMethod Algorithm=\"${escape(ref.digestMethod)}\"/>")
        leaf("ds:DigestValue", ref.digestValue.encodeToBase64())
        close("ds:Reference")
    }

    private fun Writer.names(
        wrapper: String,
        names: InternationalNames,
    ) {
        open(wrapper)
        names.names.forEach { leaf("Name", it.value, if (it.lang.isEmpty()) "" else " xml:lang=\"${escape(it.lang)}\"") }
        close(wrapper)
    }

    private fun Writer.address(
        wrapper: String,
        postal: List<PostalAddress>,
        electronic: ElectronicAddress,
    ) {
        if (postal.isEmpty()) throw SerializationViolation(wrapper, "$wrapper needs at least one PostalAddress")
        if (electronic.uris.isEmpty()) throw SerializationViolation(wrapper, "$wrapper needs at least one ElectronicAddress URI")
        open(wrapper)
        open("PostalAddresses")
        for (a in postal) {
            open("PostalAddress", if (a.lang.isEmpty()) "" else " xml:lang=\"${escape(a.lang)}\"")
            leaf("StreetAddress", a.street)
            leaf("Locality", a.locality)
            a.stateOrProvince?.let { leaf("StateOrProvince", it) }
            a.postalCode?.let { leaf("PostalCode", it) }
            leaf("Country", a.country)
            close("PostalAddress")
        }
        close("PostalAddresses")
        open("ElectronicAddress")
        electronic.uris.forEach { leaf("URI", it) }
        close("ElectronicAddress")
        close(wrapper)
    }

    private fun Writer.extensions(
        wrapper: String,
        extensions: List<CatalogueExtension>,
    ) {
        if (extensions.isEmpty()) return
        open(wrapper)
        for ((i, ext) in extensions.withIndex()) {
            val root =
                try {
                    parseXmlDocument("<w xmlns=\"$defaultNamespace\">${ext.rawXml}</w>").children.single()
                } catch (e: Exception) {
                    throw SerializationViolation("$wrapper/Extension[$i]", "Extension ${ext.qName} is not well-formed standalone XML: ${e.message}")
                }
            if ("{${root.ns}}${root.name}" != ext.qName) {
                throw SerializationViolation("$wrapper/Extension[$i]", "Extension raw XML is ${root.name} in ${root.ns}, not ${ext.qName}")
            }
            open("Extension")
            leaf("Critical", ext.critical.toString())
            line(ext.rawXml)
            close("Extension")
        }
        close(wrapper)
    }

    // ---- attribute entry ----------------------------------------------------------------------------------------

    private fun Writer.versionedAttribute(v: VersionedAttribute) {
        open("VersionedAttribute")
        leaf("AttributeVersion", v.version)
        v.status?.let { status ->
            status(status, "VersionedAttributeStatusInformation", "VersionedAttributeStatus", "LinkedAttributeAndVersion") {
                val l = status.linked!!
                l.catalogueIdentifier?.let { leaf("CatalogueIdentifier", it) }
                l.namespace?.let { leaf("LinkedAttributeNamespace", it) }
                l.identifier?.let { leaf("LinkedAttributeIdentifier", it) }
                l.registrationIdentifier?.let { leaf("LinkedAttributeRegistrationIdentifier", it) }
                leaf("LinkedAttributeVersion", l.version)
            }
        }
        attributeInformation(v.information)
        close("VersionedAttribute")
    }

    private fun Writer.status(
        status: VersionStatus,
        wrapper: String,
        statusElement: String,
        linkedElement: String,
        linked: Writer.() -> Unit,
    ) {
        open(wrapper)
        leaf(statusElement, status.statusUri)
        status.startingAt?.let { leaf("StatusStartingDateAndTime", it.toString()) }
        if (status.linked != null) {
            open(linkedElement)
            linked()
            close(linkedElement)
        }
        close(wrapper)
    }

    private fun Writer.attributeInformation(info: AttributeInformation) {
        open("AttributeInformation")
        names("AttributeName", info.name)
        open("AttributeSemanticDescription")
        names("SemanticDescription", info.semanticDescription.description)
        info.semanticDescription.pointer?.let { leaf("SemanticDescriptionPointer", it) }
        close("AttributeSemanticDescription")
        open("AttributeDataType")
        val type = info.dataType
        leaf("DataType", type.dataType)
        if (type.hasSpecification || type.specificationPointer != null || type.formatBindings.isNotEmpty()) {
            open("DataTypeSpecification")
            type.specificationPointer?.let { leaf("SpecificationPointer", it) }
            if (type.formatBindings.isNotEmpty()) {
                open("FormatSyntaxBindings")
                for (b in type.formatBindings) {
                    open("FormatSyntaxBinding")
                    leaf("MediaType", b.mediaType)
                    b.bindingDefinitionUri?.let { leaf("BindingDefinitionURI", it) }
                    leaf("FormatSyntaxSpecificIdentifier", b.formatSpecificIdentifier)
                    close("FormatSyntaxBinding")
                }
                close("FormatSyntaxBindings")
            }
            close("DataTypeSpecification")
        }
        close("AttributeDataType")
        open("AuthenticSources")
        info.authenticSources.forEach { authenticSource(it) }
        close("AuthenticSources")
        extensions("AttributeInformationExtensions", info.extensions)
        close("AttributeInformation")
    }

    private fun Writer.authenticSource(s: AuthenticSource) {
        open("AuthenticSource")
        s.name?.let { names("AuthenticSourceName", it) }
        s.territory?.let { leaf("AuthenticSourceTerritory", it) }
        leaf("AuthenticSourceIdentifier", s.identifier)
        open("AuthenticSourceVerificationAccess")
        if (s.verificationEndpoints.isNotEmpty()) {
            open("VerificationEndpointsList")
            for (e in s.verificationEndpoints) {
                open("VerificationEndpoint")
                leaf("VerificationEndpointURI", e.uri)
                names("VerificationEndpointDescription", e.description)
                if (e.responseFormats.isNotEmpty()) {
                    open("VerificationEndpointResponseFormats")
                    e.responseFormats.forEach { leaf("MediaType", it) }
                    close("VerificationEndpointResponseFormats")
                }
                close("VerificationEndpoint")
            }
            close("VerificationEndpointsList")
        }
        s.verificationAccessDescriptionUri?.let { leaf("VerificationAccessDescriptionURI", it) }
        close("AuthenticSourceVerificationAccess")
        s.sourceAttributeIdentifier?.let { leaf("AuthenticSourceAttributeIdentifier", it) }
        close("AuthenticSource")
    }

    // ---- scheme entry -------------------------------------------------------------------------------------------

    private fun Writer.versionedScheme(v: VersionedEaaScheme) {
        open("VersionedEAAScheme")
        leaf("EAASchemeVersion", v.version)
        status(v.status, "VersionedEAASchemeStatusInformation", "VersionedEAASchemeStatus", "LinkedEAASchemeAndVersion") {
            val l = v.status.linked!!
            l.catalogueIdentifier?.let { leaf("CatalogueIdentifier", it) }
            l.name?.let { leaf("LinkedEAASchemeName", it) }
            l.identifier?.let { leaf("LinkedEAASchemeIdentifier", it) }
            leaf("LinkedEAASchemeVersion", l.version)
        }
        leaf("EAASchemeDocumentURI", v.documentUri)
        open("EAATypeList")
        v.eaaTypes.forEach { eaaType(it) }
        close("EAATypeList")
        extensions("VersionedEAASchemeExtensions", v.extensions)
        close("VersionedEAAScheme")
    }

    private fun Writer.eaaType(t: EaaType) {
        open("EAAType")
        leaf("EAATypeIdentifier", t.identifier)
        names("EAATypeName", t.name)
        names("EAATypeSchemeDefinition", t.schemeDefinition)
        open("EAATypeAttributeReferences")
        t.attributeReferences.forEach { attributeReference(it) }
        close("EAATypeAttributeReferences")
        leaf("EAATypeDataModelReference", t.dataModelReference)
        open("FormatSyntaxBindings")
        for (b in t.formatBindings) {
            open("FormatSyntaxBinding")
            leaf("MediaType", b.mediaType)
            b.bindingDefinitionUri?.let { leaf("BindingDefinitionURI", it) }
            b.bindingDefinitionDocumentUri?.let { leaf("BindingDefinitionDocumentURI", it) }
            b.eaaTypeIdentifier?.let { leaf("FormatSyntaxSpecificEAATypeIdentifier", it) }
            close("FormatSyntaxBinding")
        }
        close("FormatSyntaxBindings")
        extensions("EAATypeExtensions", withTrustModels(t.extensions, t.trustModelTypes))
        close("EAAType")
    }

    private fun Writer.attributeReference(r: AttributeReference) {
        open("AttributeReference")
        r.definitionPointer?.let { leaf("AttributeDefinitionPointer", it) }
        r.cataloguePointer?.let {
            open("CataloguePointer")
            leaf("CatalogueLocation", it.location)
            leaf("CatalogueIdentifier", it.catalogueIdentifier)
            close("CataloguePointer")
        }
        leaf("AttributeNamespace", r.namespace)
        leaf("AttributeIdentifier", r.identifier)
        r.uniqueIdentifier?.let { leaf("AttributeUniqueIdentifier", it) }
        r.version?.let { leaf("AttributeVersion", it) }
        close("AttributeReference")
    }

    // ---- typed extensions ---------------------------------------------------------------------------------------

    /** Keeps a parsed annex VI extension as is while it still matches the typed field, otherwise writes it from the field. */
    private fun withAnnexVi(
        extensions: List<CatalogueExtension>,
        type: Int?,
    ): List<CatalogueExtension> {
        if (type == null) return extensions
        val qName = "{${EuCatalogueConstants.COA_NAMESPACE}}${EuCatalogueConstants.EXTENSION_EIDAS_ANNEX_VI_ATTRIBUTE}"
        val existing = extensions.firstOrNull { it.qName == qName }
        val declared = existing?.let { runCatching { parseXmlDocument(it.rawXml).childText("eIDASAnnexVIAttributeType")?.toIntOrNull() }.getOrNull() }
        if (declared == type) return extensions
        val tag = EuCatalogueConstants.EXTENSION_EIDAS_ANNEX_VI_ATTRIBUTE
        val raw = "<$tag><eIDASAnnexVIAttributeType>$type</eIDASAnnexVIAttributeType></$tag>"
        return listOf(CatalogueExtension(true, qName, raw)) + extensions.filterNot { it.qName == qName }
    }

    private fun withTrustModels(
        extensions: List<CatalogueExtension>,
        models: List<String>,
    ): List<CatalogueExtension> {
        if (models.isEmpty()) return extensions
        val qName = "{${EuCatalogueConstants.COS_NAMESPACE}}${EuCatalogueConstants.EXTENSION_EAA_TYPE_TRUST_MODEL_TYPES}"
        val declared =
            extensions
                .filter { it.qName == qName }
                .flatMap { ext -> runCatching { parseXmlDocument(ext.rawXml).all("EAATypeTrustModelType").map { it.text } }.getOrDefault(emptyList()) }
        if (declared == models) return extensions
        val tag = EuCatalogueConstants.EXTENSION_EAA_TYPE_TRUST_MODEL_TYPES
        val raw = "<$tag>" + models.joinToString("") { "<EAATypeTrustModelType>${escape(it)}</EAATypeTrustModelType>" } + "</$tag>"
        return listOf(CatalogueExtension(true, qName, raw)) + extensions.filterNot { it.qName == qName }
    }
}

private class SerializationViolation(
    val path: String,
    val reason: String,
) : RuntimeException("$reason (at $path)")

private fun escape(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private class Writer(
    val defaultNamespace: String,
) {
    private val sb = StringBuilder()
    private var depth = 0

    fun line(text: String) {
        repeat(depth) { sb.append("    ") }
        sb.append(text).append('\n')
    }

    fun open(
        tag: String,
        attributes: String = "",
    ) {
        line("<$tag$attributes>")
        depth++
    }

    fun close(tag: String) {
        depth--
        line("</$tag>")
    }

    fun leaf(
        tag: String,
        value: String,
        attributes: String = "",
    ) = line("<$tag$attributes>${escape(value)}</$tag>")

    override fun toString(): String = sb.toString()
}
