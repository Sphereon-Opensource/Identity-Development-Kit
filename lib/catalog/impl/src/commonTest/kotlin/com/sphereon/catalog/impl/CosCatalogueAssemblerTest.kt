/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.model.AttributeReference
import com.sphereon.catalog.eu.model.ElectronicAddress
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.MultiLangString
import com.sphereon.catalog.eu.model.PostalAddress
import com.sphereon.catalog.eu.model.SchemeOwner
import com.sphereon.catalog.eu.model.VersionStatus
import com.sphereon.catalog.impl.publication.CosCatalogueAssembler
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.CosEaaTypeFields
import com.sphereon.catalog.model.CosSchemeFields
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.publication.CatalogPublication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CosCatalogueAssemblerTest {
    private val base = "https://catalogs.example/public/catalogs/pid-catalog"
    private val catalog = AttestationCatalog(id = "cat-1", slug = "pid-catalog", displayName = "PID catalog")

    private fun names(text: String) = InternationalNames(listOf(MultiLangString("en", text)))

    private val owner =
        SchemeOwner(
            name = names("Owner"),
            identifier = "owner-1",
            postalAddresses = listOf(PostalAddress("en", "Street 1", "City", country = "NL")),
            electronicAddress = ElectronicAddress(listOf("https://owner.example")),
        )

    private fun cos(
        scheme: String = "pid",
        typeId: String = "type-1",
        owner: SchemeOwner? = this.owner,
        status: String = EuCatalogueConstants.SCHEME_STATUS_INFORCE,
    ) = CosSchemeFields(
        schemeName = scheme,
        schemeIdentifier = "urn:example:scheme:$scheme",
        owner = owner,
        registrationIdentifier = "REG-$scheme",
        versionStatus = VersionStatus(status),
        eaaType =
            CosEaaTypeFields(
                identifier = typeId,
                name = names("Type $typeId"),
                schemeDefinition = names("Definition"),
                attributeReferences = listOf(AttributeReference(definitionPointer = "https://attrs.example/family_name.xml", namespace = "pid.1", identifier = "family_name")),
                dataModelReference = "https://models.example/pid",
                trustModelTypes = listOf(EuCatalogueConstants.TRUST_MODEL_QEAA),
            ),
    )

    private fun record(
        id: String,
        version: String = "1.0",
        rulebook: String = "$base/api/v1/schemas/$id/rulebook",
        cos: CosSchemeFields? = cos(),
        vct: String? = "urn:example:vct:$id",
        docType: String? = null,
    ): AttestationSchemaRecord {
        val formats = listOfNotNull(vct?.let { "dc+sd-jwt" }, docType?.let { "mso_mdoc" })
        return AttestationSchemaRecord(
            catalogId = "cat-1",
            schema =
                SchemaMeta(
                    id = id,
                    version = version,
                    rulebookURI = rulebook,
                    attestationLoS = "iso_18045_high",
                    bindingType = "key",
                    supportedFormats = formats,
                    schemaURIs = formats.map { SchemaUriRef(it, "$base/api/v1/schemas/$id/formats/$it") },
                ),
            provenance = CatalogSchemaProvenance.AUTHORED,
            documents =
                listOfNotNull(
                    vct?.let { AttestationSchemaDocument(CatalogDocumentKind.FORMAT, "dc+sd-jwt", "application/json", "{\"vct\":\"$it\"}".encodeToByteArray()) },
                    docType?.let { AttestationSchemaDocument(CatalogDocumentKind.FORMAT, "mso_mdoc", "application/json", "{\"docType\":\"$it\"}".encodeToByteArray()) },
                ),
            cos = cos,
        )
    }

    private fun publication(vararg records: AttestationSchemaRecord) = CatalogPublication(catalog, records.toList(), base)

    @Test
    fun aCatalogWithoutCosFieldsIsNotServedAsCos() {
        val result = CosCatalogueAssembler.assemble(publication(record("a", cos = null)))
        assertTrue(result.isOk)
        assertNull(result.value)
    }

    @Test
    fun oneRecordBecomesOneSchemeVersionWithOneEaaTypeAndTheTypeBindings() {
        val assembly = assertNotNull(CosCatalogueAssembler.assemble(publication(record("a", vct = "urn:example:vct:pid", docType = "eu.example.pid.1"))).value)
        val entry = assembly.entries.single()
        assertEquals("pid", entry.name)
        assertEquals("REG-pid", entry.registrationIdentifier)
        assertEquals(owner, entry.owner)
        val version = entry.versions.single()
        assertEquals("1.0", version.version)
        assertEquals("$base/api/v1/schemas/a/rulebook", version.documentUri)
        val type = version.eaaTypes.single()
        assertEquals("type-1", type.identifier)
        assertEquals(listOf(EuCatalogueConstants.TRUST_MODEL_QEAA), type.trustModelTypes)
        assertEquals(listOf("dc+sd-jwt" to "urn:example:vct:pid", "mso_mdoc" to "eu.example.pid.1"), type.formatBindings.map { it.mediaType to it.eaaTypeIdentifier })
        assertEquals("$base/api/v1/schemas/a/formats/dc+sd-jwt", type.formatBindings.first().bindingDefinitionUri)
    }

    @Test
    fun theCatalogueIsIdentifiedAndDistributedAtTheCatalogUrlAndOperatedByTheSchemeOwner() {
        val header = assertNotNull(CosCatalogueAssembler.assemble(publication(record("a"))).value).header
        assertEquals(base, header.identifier)
        assertEquals("PID catalog", header.name.forLang("en"))
        assertEquals("$base/cos.xml", header.distributionPoint.downloadUrl)
        assertEquals("$base/presentation", header.informationUri)
        assertEquals("NL", header.territory)
        assertEquals("owner-1", header.operator.identifier)
        assertEquals(EuCatalogueConstants.HISTORY_FOREVER_MONTHS, header.historicalInformationPeriod)
    }

    @Test
    fun recordsOfOneSchemeGroupIntoVersionsAndTypes() {
        val shared = "$base/api/v1/schemas/b/rulebook"
        val result =
            CosCatalogueAssembler.assemble(
                publication(
                    record("a", version = "2.0"),
                    record("b", version = "1.0", rulebook = shared, cos = cos(typeId = "type-a")),
                    record("c", version = "1.0", rulebook = shared, cos = cos(typeId = "type-b")),
                    record("d", cos = cos(scheme = "permit")),
                ),
            )
        assertTrue(result.isOk, "assembly failed: ${if (result.isErr) result.error.message.defaultMessage else ""}")
        val entries = assertNotNull(result.value).entries
        assertEquals(listOf("permit", "pid"), entries.map { it.name })
        val pid = entries.first { it.name == "pid" }
        assertEquals(listOf("1.0", "2.0"), pid.versions.map { it.version })
        assertEquals(listOf("type-a", "type-b"), pid.versions.first().eaaTypes.map { it.identifier })
    }

    @Test
    fun conflictingOwnersStatusesAndRulebooksAreRefused() {
        val other = owner.copy(identifier = "owner-2")
        val owners = CosCatalogueAssembler.assemble(publication(record("a"), record("b", cos = cos(scheme = "permit", owner = other))))
        assertTrue(owners.isErr)
        assertTrue(owners.error.message.defaultMessage.contains("owners"))

        val schemeOwner = CosCatalogueAssembler.assemble(publication(record("a"), record("b", cos = cos(owner = other))))
        assertTrue(schemeOwner.isErr)

        val status = CosCatalogueAssembler.assemble(publication(record("a"), record("b", cos = cos(typeId = "t2", status = EuCatalogueConstants.SCHEME_STATUS_DEPRECATED))))
        assertTrue(status.isErr)
        assertTrue(status.error.message.defaultMessage.contains("disagree"))

        val rulebook = CosCatalogueAssembler.assemble(publication(record("a"), record("b", cos = cos(typeId = "t2"))))
        assertTrue(rulebook.isErr)
    }

    @Test
    fun missingRequiredCosFieldsAreListed() {
        val result = CosCatalogueAssembler.assemble(publication(record("a", cos = CosSchemeFields(schemeName = "pid"))))
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("no owner"))
        assertTrue(result.error.message.defaultMessage.contains("no version status"))

        val unnamed = CosCatalogueAssembler.assemble(publication(record("a", cos = CosSchemeFields())))
        assertTrue(unnamed.isErr)
        assertTrue(unnamed.error.message.defaultMessage.contains("schemeName"))
    }

    @Test
    fun aRecordWithoutAnyBindableFormatIsRefused() {
        val result = CosCatalogueAssembler.assemble(publication(record("a", vct = null)))
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("format"))
    }

    @Test
    fun aDeclaredFormatWithoutAVctOrDocTypeIsRefusedInsteadOfUsingTheSchemaUri() {
        val base = record("a", vct = "urn:example:vct:a")
        val withoutDocument = base.copy(documents = base.documents.filterNot { it.kind == CatalogDocumentKind.FORMAT })
        val result = CosCatalogueAssembler.assemble(publication(withoutDocument))
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("vct or docType"), result.error.message.defaultMessage)
    }
}
