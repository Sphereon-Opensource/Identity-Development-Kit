/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.publication

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.model.CatalogueDistributionPoint
import com.sphereon.catalog.eu.model.CatalogueOperator
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EaaType
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.MultiLangString
import com.sphereon.catalog.eu.model.SchemeFormatBinding
import com.sphereon.catalog.eu.model.SchemeOwner
import com.sphereon.catalog.eu.model.VersionedEaaScheme
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogTypeKeys
import com.sphereon.catalog.publication.CatalogPublication
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError

/** The catalogue level data of the CoS of a TS 11 catalog. The sequence number belongs to the catalogue revision. */
class CosCatalogueHeader(
    val identifier: String,
    val name: InternationalNames,
    val operator: CatalogueOperator,
    val informationUri: String,
    val territory: String,
    val legalNotice: String,
    val historicalInformationPeriod: Int,
    val distributionPoint: CatalogueDistributionPoint,
)

class CosAssembly(
    val header: CosCatalogueHeader,
    val entries: List<EaaSchemeEntry>,
)

/**
 * Builds the CoS of a published TS 11 catalog from the CoS fields of its schema records. A TS 11 record and a CoS scheme
 * version describe the same attestation type, so every record that carries CoS fields becomes one EAA type of a scheme
 * version:
 * - the scheme is the record's scheme name, the version is the SchemaMeta version and the scheme document is the (hosted)
 *   rulebook URI,
 * - the format bindings name the vct of the `dc+sd-jwt` format document and the docType of the `mso_mdoc` one,
 * - records of the same scheme must agree on owner, identifier and registration identifier, and records of the same scheme
 *   version on status and rulebook.
 *
 * The catalogue is identified by, and distributed at, the public URL of the catalog. Its operator is the owner of the
 * schemes, which therefore have to share one. Records without CoS fields stay TS 11 only. Conformance against the CoS rules
 * is checked by the caller after assembly.
 */
object CosCatalogueAssembler {
    private const val SD_JWT = "dc+sd-jwt"
    private const val MDOC = "mso_mdoc"

    /** @return null when no record of the catalog carries CoS fields. */
    fun assemble(publication: CatalogPublication): IdkResult<CosAssembly?, IdkError> {
        val records = publication.records.filter { it.cos != null }
        if (records.isEmpty()) return Ok(null)
        val problems = mutableListOf<String>()
        val entries = records.groupBy { it.cos!!.schemeName.orEmpty() }.entries.sortedBy { it.key }.mapNotNull { (name, group) -> scheme(name, group, problems) }
        if (problems.isNotEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The catalog cannot be served as a catalogue of schemes: ${problems.joinToString("; ")}"))
        }
        val owners = entries.map { it.owner }.distinct()
        if (owners.size != 1) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "The catalog cannot be served as a catalogue of schemes: its schemes have ${owners.size} different owners, but the catalogue operator is the one owner of its schemes",
                ),
            )
        }
        val owner = owners.single()
        val country = owner.postalAddresses.firstOrNull()?.country
        if (country.isNullOrBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The catalog cannot be served as a catalogue of schemes: the scheme owner has no postal address with a country"))
        }
        val base = publication.publicUrl.trimEnd('/')
        val header =
            CosCatalogueHeader(
                identifier = base,
                name = InternationalNames(listOf(MultiLangString("en", publication.catalog.displayName))),
                operator = CatalogueOperator(owner.name, owner.identifier, owner.postalAddresses, owner.electronicAddress),
                informationUri = "$base/presentation",
                territory = country,
                legalNotice = "$base/presentation",
                historicalInformationPeriod = EuCatalogueConstants.HISTORY_FOREVER_MONTHS,
                distributionPoint = CatalogueDistributionPoint(downloadUrl = "$base/${EuCatalogueConstants.COS_PUBLIC_FILE}"),
            )
        return Ok(CosAssembly(header, entries))
    }

    private fun scheme(
        name: String,
        group: List<AttestationSchemaRecord>,
        problems: MutableList<String>,
    ): EaaSchemeEntry? {
        if (name.isBlank()) {
            problems += "record ${group.first().schema.id} has CoS fields without a schemeName"
            return null
        }
        val first = group.first().cos!!
        val owner: SchemeOwner? = first.owner
        if (owner == null) problems += "scheme $name has no owner"
        for (record in group) {
            val cos = record.cos!!
            if (cos.owner != owner || cos.schemeIdentifier != first.schemeIdentifier || cos.registrationIdentifier != first.registrationIdentifier) {
                problems += "records of scheme $name disagree on owner, identifier or registration identifier (schema ${record.schema.id})"
            }
        }
        val versions =
            group.groupBy { it.schema.version }.entries.sortedBy { it.key }.mapNotNull { (version, versionGroup) -> version(name, version, versionGroup, problems) }
        if (owner == null) return null
        return EaaSchemeEntry(
            name = name,
            owner = owner,
            identifier = first.schemeIdentifier,
            versions = versions,
            registrationIdentifier = first.registrationIdentifier,
        )
    }

    private fun version(
        scheme: String,
        version: String,
        group: List<AttestationSchemaRecord>,
        problems: MutableList<String>,
    ): VersionedEaaScheme? {
        val status = group.first().cos!!.versionStatus
        if (status == null) problems += "scheme $scheme version $version has no version status"
        val rulebook = group.first().schema.rulebookURI
        for (record in group) {
            if (record.cos!!.versionStatus != status || record.schema.rulebookURI != rulebook) {
                problems += "records of scheme $scheme version $version disagree on status or rulebook (schema ${record.schema.id})"
            }
        }
        val types = group.mapNotNull { eaaType(scheme, version, it, problems) }
        if (status == null) return null
        return VersionedEaaScheme(version = version, status = status, documentUri = rulebook, eaaTypes = types)
    }

    private fun eaaType(
        scheme: String,
        version: String,
        record: AttestationSchemaRecord,
        problems: MutableList<String>,
    ): EaaType? {
        val where = "scheme $scheme version $version (schema ${record.schema.id})"
        val type = record.cos!!.eaaType
        if (type == null) {
            problems += "$where has no EAA type"
            return null
        }
        val identifier = type.identifier
        val name = type.name
        val definition = type.schemeDefinition
        val model = type.dataModelReference
        if (identifier.isNullOrBlank()) problems += "$where has no EAA type identifier"
        if (name == null) problems += "$where has no EAA type name"
        if (definition == null) problems += "$where has no EAA type scheme definition"
        if (model.isNullOrBlank()) problems += "$where has no EAA type data model reference"
        val bindings = bindings(where, record, problems)
        if (bindings.isEmpty()) problems += "$where has no dc+sd-jwt or mso_mdoc format to bind"
        if (identifier.isNullOrBlank() || name == null || definition == null || model.isNullOrBlank() || bindings.isEmpty()) return null
        return EaaType(
            identifier = identifier,
            name = name,
            schemeDefinition = definition,
            attributeReferences = type.attributeReferences,
            dataModelReference = model,
            formatBindings = bindings,
            trustModelTypes = type.trustModelTypes,
        )
    }

    private fun bindings(
        where: String,
        record: AttestationSchemaRecord,
        problems: MutableList<String>,
    ): List<SchemeFormatBinding> =
        listOfNotNull(
            binding(where, record, SD_JWT, problems) { CatalogTypeKeys.vctValue(it) },
            binding(where, record, MDOC, problems) { CatalogTypeKeys.docTypeValue(it) },
        )

    /**
     * The format-specific type identifier is the `vct` or `docType` of the format document. The schema URI is where the
     * format document is served, not a type identifier, so it is never used as one: a format that is declared without
     * a readable `vct`/`docType` is a conformance problem.
     */
    private fun binding(
        where: String,
        record: AttestationSchemaRecord,
        format: String,
        problems: MutableList<String>,
        typeOf: (ByteArray) -> String?,
    ): SchemeFormatBinding? {
        val document = record.documents.firstOrNull { it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == format }
        val reference = record.schema.schemaURIs.firstOrNull { it.formatIdentifier == format }?.uri?.takeIf { it.isNotBlank() }
        val typeIdentifier = document?.bytes?.let(typeOf)
        if (typeIdentifier.isNullOrBlank()) {
            if (document != null || reference != null) problems += "$where declares format $format but its format document has no vct or docType"
            return null
        }
        val definition = reference?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return SchemeFormatBinding(mediaType = format, bindingDefinitionUri = definition, eaaTypeIdentifier = typeIdentifier)
    }
}
