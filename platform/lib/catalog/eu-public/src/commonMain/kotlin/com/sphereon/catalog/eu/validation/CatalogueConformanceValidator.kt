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
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AuthenticSource
import com.sphereon.catalog.eu.model.CatalogueExtension
import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.CatalogueInformation
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOfSchemes
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EaaType
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.NamespaceEntry
import com.sphereon.catalog.eu.model.SchemeEntryReference

object CatalogueFindingCodes {
    const val SIGNATURE_MISSING = "CATALOGUE_SIGNATURE_MISSING"
    const val VERSION_UNSUPPORTED = "CATALOGUE_VERSION_UNSUPPORTED"
    const val SEQUENCE_INVALID = "CATALOGUE_SEQUENCE_INVALID"
    const val IDENTIFIER_MISMATCH = "CATALOGUE_IDENTIFIER_MISMATCH"
    const val IDENTIFIER_RESERVED = "CATALOGUE_IDENTIFIER_RESERVED"
    const val TERRITORY_INVALID = "CATALOGUE_TERRITORY_INVALID"
    const val HISTORY_PERIOD_INVALID = "CATALOGUE_HISTORY_PERIOD_INVALID"
    const val URI_NOT_HTTP = "CATALOGUE_URI_NOT_HTTP"
    const val DISTRIBUTION_POINT_EMPTY = "CATALOGUE_DISTRIBUTION_POINT_EMPTY"
    const val ADDRESS_EMPTY = "CATALOGUE_ADDRESS_EMPTY"
    const val ELECTRONIC_ADDRESS_EMPTY = "CATALOGUE_ELECTRONIC_ADDRESS_EMPTY"
    const val NAME_MISSING = "CATALOGUE_NAME_MISSING"
    const val NAME_LANG_MISSING = "CATALOGUE_NAME_LANG_MISSING"
    const val ADDRESS_LANG_MISSING = "CATALOGUE_ADDRESS_LANG_MISSING"
    const val DUPLICATE_IDENTIFIER = "CATALOGUE_DUPLICATE_IDENTIFIER"
    const val REFERENCE_TRANSFORM_INVALID = "CATALOGUE_REFERENCE_TRANSFORM_INVALID"
    const val REFERENCE_DIGEST_METHOD_UNSUPPORTED = "CATALOGUE_REFERENCE_DIGEST_METHOD_UNSUPPORTED"
    const val REFERENCE_DIGEST_LENGTH_INVALID = "CATALOGUE_REFERENCE_DIGEST_LENGTH_INVALID"
    const val REGISTRATION_IDENTIFIER_MISSING = "CATALOGUE_REGISTRATION_IDENTIFIER_MISSING"
    const val INDEX_ENTRY_MISMATCH = "CATALOGUE_INDEX_ENTRY_MISMATCH"
    const val VERSIONS_EMPTY = "CATALOGUE_VERSIONS_EMPTY"
    const val STATUS_UNKNOWN = "CATALOGUE_STATUS_UNKNOWN"
    const val SEMANTIC_DESCRIPTION_ENGLISH_MISSING = "CATALOGUE_SEMANTIC_DESCRIPTION_ENGLISH_MISSING"
    const val DATA_TYPE_INVALID = "CATALOGUE_DATA_TYPE_INVALID"
    const val DATA_TYPE_SPECIFICATION_EMPTY = "CATALOGUE_DATA_TYPE_SPECIFICATION_EMPTY"
    const val AUTHENTIC_SOURCES_EMPTY = "CATALOGUE_AUTHENTIC_SOURCES_EMPTY"
    const val VERIFICATION_ACCESS_EMPTY = "CATALOGUE_VERIFICATION_ACCESS_EMPTY"
    const val ENDPOINT_DESCRIPTION_ENGLISH_MISSING = "CATALOGUE_ENDPOINT_DESCRIPTION_ENGLISH_MISSING"
    const val SOURCE_ATTRIBUTE_IDENTIFIER_INVALID = "CATALOGUE_SOURCE_ATTRIBUTE_IDENTIFIER_INVALID"
    const val ANNEX_VI_TYPE_INVALID = "CATALOGUE_ANNEX_VI_TYPE_INVALID"
    const val EAA_TYPES_EMPTY = "CATALOGUE_EAA_TYPES_EMPTY"
    const val TRUST_MODEL_TYPES_MISSING = "CATALOGUE_TRUST_MODEL_TYPES_MISSING"
    const val TRUST_MODEL_TYPE_UNKNOWN = "CATALOGUE_TRUST_MODEL_TYPE_UNKNOWN"
    const val TRUST_MODEL_TYPE_DUPLICATE = "CATALOGUE_TRUST_MODEL_TYPE_DUPLICATE"
    const val ATTRIBUTE_REFERENCES_EMPTY = "CATALOGUE_ATTRIBUTE_REFERENCES_EMPTY"
    const val ATTRIBUTE_REFERENCE_CHOICE_INVALID = "CATALOGUE_ATTRIBUTE_REFERENCE_CHOICE_INVALID"
    const val FORMAT_BINDINGS_EMPTY = "CATALOGUE_FORMAT_BINDINGS_EMPTY"
    const val UNKNOWN_CRITICAL_EXTENSION = "CATALOGUE_UNKNOWN_CRITICAL_EXTENSION"
    const val LOC_TAG_INVALID = "LOC_TAG_INVALID"
    const val LOC_TYPE_INVALID = "LOC_TYPE_INVALID"
    const val LOC_VERSION_INVALID = "LOC_VERSION_INVALID"
    const val LOC_SEQUENCE_INVALID = "LOC_SEQUENCE_INVALID"
    const val LOC_TRUSTED_ENTITIES_PRESENT = "LOC_TRUSTED_ENTITIES_PRESENT"
    const val LOC_HISTORICAL_PERIOD_PRESENT = "LOC_HISTORICAL_PERIOD_PRESENT"
    const val LOC_CRITICAL_EXTENSION_PRESENT = "LOC_CRITICAL_EXTENSION_PRESENT"
    const val LOC_TERRITORY_INVALID = "LOC_TERRITORY_INVALID"
    const val LOC_STATUS_APPROACH_INVALID = "LOC_STATUS_APPROACH_INVALID"
    const val LOC_COMMUNITY_RULES_INVALID = "LOC_COMMUNITY_RULES_INVALID"
    const val LOC_POINTERS_INVALID = "LOC_POINTERS_INVALID"
    const val LOC_POINTER_SIGNERS_MISSING = "LOC_POINTER_SIGNERS_MISSING"
    const val LOC_POINTER_LOCATION_INVALID = "LOC_POINTER_LOCATION_INVALID"
    const val LOC_NEXT_UPDATE_TOO_FAR = "LOC_NEXT_UPDATE_TOO_FAR"
    const val LOC_NEXT_UPDATE_PASSED = "LOC_NEXT_UPDATE_PASSED"
}

/**
 * Checks that the XSDs leave open but the CoA and CoS specifications mandate: the enveloped signature,
 * registration identifiers, trust model types, choice and at-least-one rules, English descriptions,
 * `xml:lang` on names and the rule that an unknown critical extension voids the catalogue.
 *
 * The EU-only rules (fixed identifiers, EU territory, registration identifiers, signature) come from the
 * [CatalogueProfile]; pass [CatalogueProfile.custom] to check a tenant-authored catalogue before it is published.
 */
class CatalogueConformanceValidator {
    fun validateCoa(
        coa: CatalogueOfAttributes,
        profile: CatalogueProfile = CatalogueProfile.EU_COA,
    ): List<CatalogueFinding> {
        val f = FindingCollector()
        validateInfo(coa.info, profile, f)
        if (profile.requireSignature && !coa.hasSignature) f.error(CatalogueFindingCodes.SIGNATURE_MISSING, "ds:Signature", "The catalogue must carry an enveloped signature")
        val namespaceIds = mutableSetOf<String>()
        coa.namespaces.forEachIndexed { i, ns ->
            val p = "Namespaces/Namespace[$i]"
            if (!namespaceIds.add(ns.identifier)) f.error(CatalogueFindingCodes.DUPLICATE_IDENTIFIER, "$p/NamespaceIdentifier", "Namespace ${ns.identifier} occurs more than once")
            val attributeIds = mutableSetOf<String>()
            ns.entries.forEachIndexed { j, e ->
                val ep = "$p/NamespaceEntries/NamespaceEntry[$j]"
                if (!attributeIds.add(e.attributeIdentifier)) f.error(CatalogueFindingCodes.DUPLICATE_IDENTIFIER, "$ep/AttributeIdentifier", "Attribute ${e.attributeIdentifier} occurs more than once in ${ns.identifier}")
                validateReference(e.reference, "$ep/Reference", f)
            }
        }
        return f.findings
    }

    fun validateAttributeEntry(
        entry: AttributeEntry,
        indexEntry: NamespaceEntry? = null,
        profile: CatalogueProfile = CatalogueProfile.EU_COA,
    ): List<CatalogueFinding> {
        val f = FindingCollector()
        if (profile.requireRegistrationIdentifier && entry.registrationIdentifier.isNullOrBlank()) {
            f.error(CatalogueFindingCodes.REGISTRATION_IDENTIFIER_MISSING, "AttributeRegistrationIdentifier", "The Commission-issued registration identifier must be present")
        }
        if (indexEntry != null) {
            if (indexEntry.attributeIdentifier != entry.attributeIdentifier) {
                f.error(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH, "AttributeIdentifier", "Index lists ${indexEntry.attributeIdentifier}, entry file has ${entry.attributeIdentifier}")
            }
            if (indexEntry.registrationIdentifier != entry.registrationIdentifier) {
                f.error(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH, "AttributeRegistrationIdentifier", "Index and entry file disagree on the registration identifier")
            }
        }
        names(entry.referenceBody.name, "ReferenceBody/ReferenceBodyName", f)
        httpUri(entry.referenceBody.website, "ReferenceBody/ReferenceBodyWebsite", f)
        if (entry.versions.isEmpty()) f.error(CatalogueFindingCodes.VERSIONS_EMPTY, "VersionedAttributeList", "At least one VersionedAttribute is required")
        val versionIds = mutableSetOf<String>()
        entry.versions.forEachIndexed { i, v ->
            val p = "VersionedAttributeList/VersionedAttribute[$i]"
            if (!versionIds.add(v.version)) f.error(CatalogueFindingCodes.DUPLICATE_IDENTIFIER, "$p/AttributeVersion", "Version ${v.version} occurs more than once")
            v.status?.let {
                if (it.statusUri !in EuCatalogueConstants.ATTRIBUTE_STATUSES) {
                    f.warning(CatalogueFindingCodes.STATUS_UNKNOWN, "$p/VersionedAttributeStatusInformation", "Status ${it.statusUri} is not one of the published status URIs")
                }
            }
            val info = v.information
            val ip = "$p/AttributeInformation"
            names(info.name, "$ip/AttributeName", f)
            names(info.semanticDescription.description, "$ip/AttributeSemanticDescription/SemanticDescription", f)
            if (!info.semanticDescription.description.hasLang("en")) {
                f.error(CatalogueFindingCodes.SEMANTIC_DESCRIPTION_ENGLISH_MISSING, "$ip/AttributeSemanticDescription/SemanticDescription", "An English semantic description is required")
            }
            info.semanticDescription.pointer?.let { httpUri(it, "$ip/AttributeSemanticDescription/SemanticDescriptionPointer", f) }
            if (info.dataType.dataType.isBlank()) f.error(CatalogueFindingCodes.DATA_TYPE_INVALID, "$ip/AttributeDataType/DataType", "DataType must not be empty")
            if (info.dataType.hasSpecification && info.dataType.specificationPointer.isNullOrBlank() && info.dataType.formatBindings.isEmpty()) {
                f.error(CatalogueFindingCodes.DATA_TYPE_SPECIFICATION_EMPTY, "$ip/AttributeDataType/DataTypeSpecification", "A specification needs a SpecificationPointer or at least one FormatSyntaxBinding")
            }
            if (info.authenticSources.isEmpty()) f.error(CatalogueFindingCodes.AUTHENTIC_SOURCES_EMPTY, "$ip/AuthenticSources", "At least one AuthenticSource is required")
            info.authenticSources.forEachIndexed { j, s -> validateAuthenticSource(s, entry.attributeIdentifier, "$ip/AuthenticSources/AuthenticSource[$j]", f) }
            criticalExtensions(info.extensions, "$ip/AttributeInformationExtensions", f)
        }
        entry.eidasAnnexVIAttributeType?.let {
            if (it !in EuCatalogueConstants.EIDAS_ANNEX_VI_ATTRIBUTE_TYPE_MIN..EuCatalogueConstants.EIDAS_ANNEX_VI_ATTRIBUTE_TYPE_MAX) {
                f.error(CatalogueFindingCodes.ANNEX_VI_TYPE_INVALID, "AttributeExtensions/eIDASAnnexVIAttribute", "eIDASAnnexVIAttributeType $it is outside 1..11")
            }
        }
        criticalExtensions(entry.extensions, "AttributeExtensions", f)
        return f.findings
    }

    fun validateCos(
        cos: CatalogueOfSchemes,
        profile: CatalogueProfile = CatalogueProfile.EU_COS,
    ): List<CatalogueFinding> {
        val f = FindingCollector()
        validateInfo(cos.info, profile, f)
        if (profile.requireSignature && !cos.hasSignature) f.error(CatalogueFindingCodes.SIGNATURE_MISSING, "ds:Signature", "The catalogue must carry an enveloped signature")
        val names = mutableSetOf<String>()
        cos.schemes.forEachIndexed { i, s ->
            val p = "EAASchemeList/EAASchemeEntryReference[$i]"
            if (!names.add(s.name)) f.error(CatalogueFindingCodes.DUPLICATE_IDENTIFIER, "$p/EAASchemeName", "Scheme ${s.name} occurs more than once")
            validateReference(s.reference, "$p/Reference", f)
        }
        return f.findings
    }

    fun validateSchemeEntry(
        entry: EaaSchemeEntry,
        indexEntry: SchemeEntryReference? = null,
        profile: CatalogueProfile = CatalogueProfile.EU_COS,
    ): List<CatalogueFinding> {
        val f = FindingCollector()
        if (profile.requireRegistrationIdentifier && entry.registrationIdentifier.isNullOrBlank()) {
            f.error(CatalogueFindingCodes.REGISTRATION_IDENTIFIER_MISSING, "EAASchemeRegistrationIdentifier", "The Commission-issued registration identifier must be present")
        }
        if (indexEntry != null) {
            if (indexEntry.name != entry.name) f.error(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH, "EAASchemeName", "Index lists ${indexEntry.name}, entry file has ${entry.name}")
            if (indexEntry.identifier != entry.identifier) {
                f.error(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH, "EAASchemeIdentifier", "Index and entry file disagree on the scheme identifier")
            }
            if (indexEntry.registrationIdentifier != entry.registrationIdentifier) {
                f.error(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH, "EAASchemeRegistrationIdentifier", "Index and entry file disagree on the registration identifier")
            }
        }
        names(entry.owner.name, "EAASchemeOwnerInformation/EAASchemeOwnerName", f)
        if (entry.owner.postalAddresses.isEmpty()) {
            f.error(CatalogueFindingCodes.ADDRESS_EMPTY, "EAASchemeOwnerInformation/EAASchemeOwnerAddress/PostalAddresses", "At least one PostalAddress is required")
        }
        entry.owner.postalAddresses.forEachIndexed { i, a ->
            if (a.lang.isBlank()) f.error(CatalogueFindingCodes.ADDRESS_LANG_MISSING, "EAASchemeOwnerInformation/EAASchemeOwnerAddress/PostalAddresses/PostalAddress[$i]", "PostalAddress needs xml:lang")
        }
        if (entry.owner.electronicAddress.uris.isEmpty()) {
            f.error(CatalogueFindingCodes.ELECTRONIC_ADDRESS_EMPTY, "EAASchemeOwnerInformation/EAASchemeOwnerAddress/ElectronicAddress", "At least one URI is required")
        }
        if (entry.versions.isEmpty()) f.error(CatalogueFindingCodes.VERSIONS_EMPTY, "VersionedEAASchemeList", "At least one VersionedEAAScheme is required")
        val versionIds = mutableSetOf<String>()
        entry.versions.forEachIndexed { i, v ->
            val p = "VersionedEAASchemeList/VersionedEAAScheme[$i]"
            if (!versionIds.add(v.version)) f.error(CatalogueFindingCodes.DUPLICATE_IDENTIFIER, "$p/EAASchemeVersion", "Version ${v.version} occurs more than once")
            if (v.status.statusUri !in EuCatalogueConstants.SCHEME_STATUSES) {
                f.warning(CatalogueFindingCodes.STATUS_UNKNOWN, "$p/VersionedEAASchemeStatusInformation", "Status ${v.status.statusUri} is not one of the published status URIs")
            }
            httpUri(v.documentUri, "$p/EAASchemeDocumentURI", f)
            if (v.eaaTypes.isEmpty()) f.error(CatalogueFindingCodes.EAA_TYPES_EMPTY, "$p/EAATypeList", "At least one EAAType is required")
            val typeIds = mutableSetOf<String>()
            v.eaaTypes.forEachIndexed { j, t ->
                val tp = "$p/EAATypeList/EAAType[$j]"
                if (!typeIds.add(t.identifier)) f.error(CatalogueFindingCodes.DUPLICATE_IDENTIFIER, "$tp/EAATypeIdentifier", "EAAType ${t.identifier} occurs more than once in the version")
                validateEaaType(t, tp, f)
            }
            criticalExtensions(v.extensions, "$p/VersionedEAASchemeExtensions", f)
        }
        return f.findings
    }

    private fun validateEaaType(
        t: EaaType,
        p: String,
        f: FindingCollector,
    ) {
        names(t.name, "$p/EAATypeName", f)
        names(t.schemeDefinition, "$p/EAATypeSchemeDefinition", f)
        httpUri(t.dataModelReference, "$p/EAATypeDataModelReference", f)
        if (t.attributeReferences.isEmpty()) f.error(CatalogueFindingCodes.ATTRIBUTE_REFERENCES_EMPTY, "$p/EAATypeAttributeReferences", "At least one AttributeReference is required")
        t.attributeReferences.forEachIndexed { i, r ->
            val hasDefinition = !r.definitionPointer.isNullOrBlank()
            val hasCatalogue = r.cataloguePointer != null
            if (hasDefinition == hasCatalogue) {
                f.error(CatalogueFindingCodes.ATTRIBUTE_REFERENCE_CHOICE_INVALID, "$p/EAATypeAttributeReferences/AttributeReference[$i]", "Exactly one of AttributeDefinitionPointer and CataloguePointer is required")
            }
        }
        if (t.formatBindings.isEmpty()) f.error(CatalogueFindingCodes.FORMAT_BINDINGS_EMPTY, "$p/FormatSyntaxBindings", "At least one FormatSyntaxBinding is required")
        if (t.trustModelTypes.isEmpty()) {
            f.error(CatalogueFindingCodes.TRUST_MODEL_TYPES_MISSING, "$p/EAATypeExtensions", "EAATypeExtensions must carry EAATypeTrustModelTypes")
        }
        val seen = mutableSetOf<String>()
        for (model in t.trustModelTypes) {
            if (model !in EuCatalogueConstants.TRUST_MODEL_TYPES) {
                f.error(CatalogueFindingCodes.TRUST_MODEL_TYPE_UNKNOWN, "$p/EAATypeExtensions", "Unknown trust model type $model")
            }
            if (!seen.add(model)) f.error(CatalogueFindingCodes.TRUST_MODEL_TYPE_DUPLICATE, "$p/EAATypeExtensions", "Trust model type $model occurs more than once")
        }
        criticalExtensions(t.extensions, "$p/EAATypeExtensions", f)
    }

    private fun validateAuthenticSource(
        s: AuthenticSource,
        attributeIdentifier: String,
        p: String,
        f: FindingCollector,
    ) {
        s.name?.let { names(it, "$p/AuthenticSourceName", f) }
        if (s.verificationEndpoints.isEmpty() && s.verificationAccessDescriptionUri.isNullOrBlank()) {
            f.error(CatalogueFindingCodes.VERIFICATION_ACCESS_EMPTY, "$p/AuthenticSourceVerificationAccess", "A verification endpoint list or an access description URI is required")
        }
        s.verificationEndpoints.forEachIndexed { i, e ->
            val ep = "$p/AuthenticSourceVerificationAccess/VerificationEndpointsList/VerificationEndpoint[$i]"
            names(e.description, "$ep/VerificationEndpointDescription", f)
            if (!e.description.hasLang("en")) {
                f.error(CatalogueFindingCodes.ENDPOINT_DESCRIPTION_ENGLISH_MISSING, "$ep/VerificationEndpointDescription", "An English endpoint description is required")
            }
        }
        if (s.sourceAttributeIdentifier != null && s.sourceAttributeIdentifier == attributeIdentifier) {
            f.error(CatalogueFindingCodes.SOURCE_ATTRIBUTE_IDENTIFIER_INVALID, "$p/AuthenticSourceAttributeIdentifier", "The source attribute identifier must differ from the catalogue attribute identifier")
        }
    }

    private fun validateInfo(
        info: CatalogueInformation,
        profile: CatalogueProfile,
        f: FindingCollector,
    ) {
        val p = "CatalogueInformation"
        if (info.version != EuCatalogueConstants.SUPPORTED_CATALOGUE_VERSION) {
            f.error(CatalogueFindingCodes.VERSION_UNSUPPORTED, "$p/CatalogueVersion", "Unsupported catalogue version ${info.version}")
        }
        if (info.sequenceNumber < 1) f.error(CatalogueFindingCodes.SEQUENCE_INVALID, "$p/CatalogueSequenceNumber", "The sequence number must be positive")
        val expected = profile.expectedIdentifier
        if (expected != null && info.identifier != expected) {
            f.error(CatalogueFindingCodes.IDENTIFIER_MISMATCH, "$p/CatalogueIdentifier", "Expected $expected, found ${info.identifier}")
        }
        if (info.identifier.isBlank()) {
            f.error(CatalogueFindingCodes.IDENTIFIER_MISMATCH, "$p/CatalogueIdentifier", "The catalogue identifier must not be empty")
        }
        if (info.identifier in profile.forbiddenIdentifiers) {
            f.error(CatalogueFindingCodes.IDENTIFIER_RESERVED, "$p/CatalogueIdentifier", "The identifier ${info.identifier} is reserved for another catalogue")
        }
        names(info.name, "$p/CatalogueName", f)
        names(info.operator.name, "$p/CatalogueOperator/CatalogueOperatorName", f)
        if (info.operator.postalAddresses.isEmpty()) {
            f.error(CatalogueFindingCodes.ADDRESS_EMPTY, "$p/CatalogueOperator/CatalogueOperatorAddress/PostalAddresses", "At least one PostalAddress is required")
        }
        info.operator.postalAddresses.forEachIndexed { i, a ->
            if (a.lang.isBlank()) f.error(CatalogueFindingCodes.ADDRESS_LANG_MISSING, "$p/CatalogueOperator/CatalogueOperatorAddress/PostalAddresses/PostalAddress[$i]", "PostalAddress needs xml:lang")
        }
        if (info.operator.electronicAddress.uris.isEmpty()) {
            f.error(CatalogueFindingCodes.ELECTRONIC_ADDRESS_EMPTY, "$p/CatalogueOperator/CatalogueOperatorAddress/ElectronicAddress", "At least one URI is required")
        }
        httpUri(info.informationUri, "$p/CatalogueInformationURI", f)
        info.statusDeterminationApproach?.let { httpUri(it, "$p/CatalogueEntriesStatusDeterminationApproach", f) }
        httpUri(info.legalNotice, "$p/CatalogueLegalNotice", f)
        val territory = profile.expectedTerritory
        if ((territory != null && info.territory != territory) || info.territory.isBlank()) {
            f.error(CatalogueFindingCodes.TERRITORY_INVALID, "$p/CatalogueTerritory", "Expected ${territory ?: "a territory"}, found ${info.territory}")
        }
        val history = info.historicalInformationPeriod
        if (profile.requireHistoryForever && history != EuCatalogueConstants.HISTORY_FOREVER_MONTHS) {
            f.error(CatalogueFindingCodes.HISTORY_PERIOD_INVALID, "$p/HistoricalInformationPeriod", "The history must be kept forever (${EuCatalogueConstants.HISTORY_FOREVER_MONTHS})")
        } else if (history !in 0..EuCatalogueConstants.HISTORY_FOREVER_MONTHS) {
            f.error(CatalogueFindingCodes.HISTORY_PERIOD_INVALID, "$p/HistoricalInformationPeriod", "The history period must be between 0 and ${EuCatalogueConstants.HISTORY_FOREVER_MONTHS}")
        }
        if (!info.distributionPoint.hasAny) {
            f.error(CatalogueFindingCodes.DISTRIBUTION_POINT_EMPTY, "$p/CatalogueDistributionPoint", "At least one distribution point URL is required")
        }
        criticalExtensions(info.extensions, "$p/CatalogueExtensions", f)
    }

    private fun validateReference(
        ref: EntryReference,
        p: String,
        f: FindingCollector,
    ) {
        if (ref.transforms != listOf(EuCatalogueConstants.EXC_C14N_TRANSFORM)) {
            f.error(CatalogueFindingCodes.REFERENCE_TRANSFORM_INVALID, "$p/Transforms", "The only transform must be ${EuCatalogueConstants.EXC_C14N_TRANSFORM}")
        }
        if (ref.digestMethod !in EuCatalogueConstants.ACCEPTED_DIGEST_METHODS) {
            f.error(CatalogueFindingCodes.REFERENCE_DIGEST_METHOD_UNSUPPORTED, "$p/DigestMethod", "Unsupported digest method ${ref.digestMethod}")
        }
        if (ref.digestValue.size != EuCatalogueConstants.SHA512_DIGEST_LENGTH) {
            f.error(CatalogueFindingCodes.REFERENCE_DIGEST_LENGTH_INVALID, "$p/DigestValue", "A SHA-512 digest has ${EuCatalogueConstants.SHA512_DIGEST_LENGTH} bytes, found ${ref.digestValue.size}")
        }
    }

    private fun names(
        value: InternationalNames,
        path: String,
        f: FindingCollector,
    ) {
        if (value.names.isEmpty()) {
            f.error(CatalogueFindingCodes.NAME_MISSING, path, "At least one Name is required")
        }
        value.names.forEachIndexed { i, n ->
            if (n.lang.isBlank()) f.error(CatalogueFindingCodes.NAME_LANG_MISSING, "$path/Name[$i]", "Name needs xml:lang")
        }
    }

    private fun httpUri(
        value: String,
        path: String,
        f: FindingCollector,
    ) {
        val v = value.trim().lowercase()
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            f.error(CatalogueFindingCodes.URI_NOT_HTTP, path, "Expected an http or https URI, found $value")
        }
    }

    private fun criticalExtensions(
        extensions: List<CatalogueExtension>,
        path: String,
        f: FindingCollector,
    ) {
        extensions.forEachIndexed { i, e ->
            if (e.critical && e.qName !in EuCatalogueConstants.UNDERSTOOD_EXTENSIONS) {
                f.error(CatalogueFindingCodes.UNKNOWN_CRITICAL_EXTENSION, "$path/Extension[$i]", "Critical extension ${e.qName} is not understood; the catalogue must be disregarded")
            }
        }
    }

    companion object {
        fun hasErrors(findings: List<CatalogueFinding>): Boolean = findings.any { it.severity == FindingSeverity.ERROR }

        fun hasUnknownCriticalExtension(findings: List<CatalogueFinding>): Boolean =
            findings.any { it.code == CatalogueFindingCodes.UNKNOWN_CRITICAL_EXTENSION }
    }
}

internal class FindingCollector {
    val findings = mutableListOf<CatalogueFinding>()

    fun error(
        code: String,
        path: String,
        message: String,
    ) {
        findings.add(CatalogueFinding(code, FindingSeverity.ERROR, path, message))
    }

    fun warning(
        code: String,
        path: String,
        message: String,
    ) {
        findings.add(CatalogueFinding(code, FindingSeverity.WARNING, path, message))
    }
}
