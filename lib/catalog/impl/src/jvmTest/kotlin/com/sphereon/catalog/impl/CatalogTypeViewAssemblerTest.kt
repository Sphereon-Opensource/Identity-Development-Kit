/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.impl

import com.sphereon.catalog.client.IssuerBindingSnapshot
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogCardFace
import com.sphereon.catalog.model.CatalogCardSource
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogIssuerBindingView
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CatalogTypeViewAssemblerTest {
    @Test
    fun pidVctmCardIsVctMetadataWithPidTitle() =
        runTest {
            val view =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record =
                        record(
                            formats = listOf("dc+sd-jwt"),
                            documents = listOf(formatDocument("dc+sd-jwt", fixture("webuild-consortium/pid.vctm.json"))),
                        ),
                )
            val card = assertNotNull(view.card)
            assertEquals(CatalogCardSource.VCT_METADATA, card.source)
            assertEquals("Person Identification Data (PID)", view.title)
            assertEquals(AttestationTypeKeyKind.VCT, view.typeKey.kind)
            assertEquals("urn:eudi:pid:1", view.typeKey.value)
            val country = view.claims.first { it.path == "issuing_country" }
            assertEquals("Issuing Country", country.label)
            assertEquals(true, country.mandatory)
        }

    @Test
    fun mdocClaimsCarryExampleValuesFromTheFormatDocument() =
        runTest {
            val view =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record =
                        record(
                            formats = listOf("mso_mdoc"),
                            documents = listOf(formatDocument("mso_mdoc", fixture("webuild-consortium/pid.mdoc.json"))),
                        ),
                )
            val givenName = view.claims.first { it.path == "given_name" }
            assertEquals("Alice", givenName.example)
        }

    @Test
    fun mdocOnlyWithoutDesignHasNoCard() =
        runTest {
            val view =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record =
                        record(
                            formats = listOf("mso_mdoc"),
                            documents = listOf(formatDocument("mso_mdoc", fixture("webuild-consortium/pid.mdoc.json"))),
                        ),
                )
            assertNull(view.card)
            assertEquals(AttestationTypeKeyKind.DOCTYPE, view.typeKey.kind)
            assertEquals("eu.europa.ec.eudi.pid.1", view.typeKey.value)
        }

    @Test
    fun linkedDesignStubUsesLinkedDesignCardSource() =
        runTest {
            val view =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record =
                        record(
                            formats = listOf("mso_mdoc"),
                            documents = listOf(formatDocument("mso_mdoc", fixture("webuild-consortium/pid.mdoc.json"))),
                            provenance = CatalogSchemaProvenance.LINKED_DESIGN,
                            linkedDesignId = "design-stub",
                        ),
                )
            val card = assertNotNull(view.card)
            assertEquals(CatalogCardSource.LINKED_DESIGN, card.source)
        }

    @Test
    fun issuerBindingWinsCardOverVctMetadata() =
        runTest {
            val view =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record =
                        record(
                            formats = listOf("dc+sd-jwt"),
                            documents = listOf(formatDocument("dc+sd-jwt", fixture("webuild-consortium/pid.vctm.json"))),
                        ),
                    issuerBindings =
                        IssuerBindingSnapshot(
                            bindings =
                                listOf(
                                    CatalogIssuerBindingView(
                                        instanceId = "issuer-1",
                                        instanceDisplayName = "PID issuer",
                                        credentialConfigurationId = "pid",
                                    ),
                                ),
                            card =
                                CatalogCardFace(
                                    displayName = "Issuer PID",
                                    seed = "issuer-pid",
                                    source = CatalogCardSource.ISSUER_CONFIG,
                                ),
                        ),
                )
            val card = assertNotNull(view.card)
            assertEquals(CatalogCardSource.ISSUER_CONFIG, card.source)
            assertEquals("Issuer PID", card.displayName)
            assertEquals("Person Identification Data (PID)", view.title)
            assertEquals("urn:eudi:pid:1", view.typeKey.value)
        }

    @Test
    fun vctmNullPathPartsAreSkippedAndLocaleSelectsDisplay() =
        runTest {
            val bytes =
                """
                {
                  "vct": "urn:eudi:pid:1",
                  "name": "PID",
                  "display": [
                    {"locale":"en","name":"Person Identification Data (PID)"},
                    {"locale":"nl","name":"Persoonsidentificatiegegevens"}
                  ],
                  "claims": [
                    {"path":["given_name"],"display":[{"locale":"en","label":"Given name","description":"Official given name"},{"locale":"nl","label":"Voornaam","description":"Officiële voornaam"}]},
                    {"path":[null,"family_name"],"display":[{"locale":"en","label":"Family name"}]}
                  ]
                }
                """.trimIndent().encodeToByteArray()
            val english =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record = record(formats = listOf("dc+sd-jwt"), documents = listOf(formatDocument("dc+sd-jwt", bytes))),
                    locale = "en",
                )
            val dutch =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record = record(formats = listOf("dc+sd-jwt"), documents = listOf(formatDocument("dc+sd-jwt", bytes))),
                    locale = "nl",
                )
            assertEquals("Person Identification Data (PID)", english.title)
            assertEquals("Persoonsidentificatiegegevens", dutch.title)
            assertEquals(listOf("given_name", "family_name"), english.claims.map { it.path })
            assertEquals("Given name", english.claims.first().label)
            assertEquals("Official given name", english.claims.first().description)
            assertEquals("Voornaam", dutch.claims.first().label)
            assertEquals("Officiële voornaam", dutch.claims.first().description)
            assertEquals("Family name", english.claims.last().label)
        }

    @Test
    fun mdocClaimLabelsUseDescriptionWhenTitleIsAbsent() =
        runTest {
            val view =
                CatalogTypeViewAssembler().assemble(
                    catalog = catalog(),
                    record =
                        record(
                            formats = listOf("mso_mdoc"),
                            documents = listOf(formatDocument("mso_mdoc", fixture("webuild-consortium/pid.mdoc.json"))),
                        ),
                )
            val family = view.claims.single { it.path == "family_name" }
            assertEquals("Current last name(s) or surname(s) of the user.", family.label)
        }

    private fun catalog() =
        AttestationCatalog(
            id = "cat-pid",
            slug = "pid-catalog",
            displayName = "PID catalog",
        )

    private fun record(
        formats: List<String>,
        documents: List<AttestationSchemaDocument>,
        provenance: CatalogSchemaProvenance = CatalogSchemaProvenance.AUTHORED,
        linkedDesignId: String? = null,
    ) = AttestationSchemaRecord(
        catalogId = "cat-pid",
        schema =
            SchemaMeta(
                id = "schema-pid",
                version = "1.0.0",
                rulebookURI = "https://example.test/rulebook",
                attestationLoS = "iso_18045_high",
                bindingType = "key",
                supportedFormats = formats,
                schemaURIs = formats.map { SchemaUriRef(it, "https://example.test/$it") },
            ),
        provenance = provenance,
        linkedDesignId = linkedDesignId,
        documents = documents,
    )

    private fun formatDocument(
        formatIdentifier: String,
        bytes: ByteArray,
    ) = AttestationSchemaDocument(
        kind = CatalogDocumentKind.FORMAT,
        formatIdentifier = formatIdentifier,
        mediaType = "application/json",
        bytes = bytes,
    )

    private fun fixture(path: String): ByteArray {
        val resource = "ts11-public-registry/$path"
        val stream =
            requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
                "missing fixture $resource"
            }
        return stream.use { it.readBytes() }
    }
}
