/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.extractor

import com.sphereon.trust.core.model.ContactType
import com.sphereon.trust.core.model.EntityRole
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.toContact
import com.sphereon.trust.etsi.model.ETSIServiceType
import com.sphereon.trust.etsi.parser.StreamingETSITrustListParser
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.FIDES_LOTL_URL
import com.sphereon.trust.etsi.testutil.FIDES_TL_URL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Entity info extraction tests using real FIDES LOTL/TL data.
 *
 * Fetches https://raw.githubusercontent.com/FIDEScommunity/fides-trust-list/main/FIDES-LOTL.xml
 * and the NL trust list it references, then extracts entity contact info from actual trusted entities.
 */
class EtsiEntityInfoExtractorTest {
    private val ctx = EtsiTestContext("etsi-entity-info-test", this)
    private val parser = StreamingETSITrustListParser()
    private val extractor = EtsiEntityInfoExtractor()

    // -- LOTL: Scheme Operator Info --

    @Test
    fun extractsSchemeOperatorFromLotl() =
        runTest {
            val lotlXml = ctx.fetchUrl(FIDES_LOTL_URL)
            val lotl = parser.parseFromString(lotlXml)

            val operatorInfo = extractor.mapSchemeOperator(lotl, depth = 0)

            println("--- LOTL Scheme Operator ---")
            println("  Entity: ${operatorInfo.entityIdentifier}")
            println("  Source type: ${operatorInfo.sourceType}")
            println("  Chain position: depth=${operatorInfo.chainPosition.depth}, role=${operatorInfo.chainPosition.role}")
            println("  Names: ${operatorInfo.names.map { "${it.lang}=${it.value}" }}")
            println("  Jurisdiction: ${operatorInfo.jurisdiction}")
            println("  Contacts: ${operatorInfo.contacts.map { "${it.type}: ${it.value}" }}")
            println("  Addresses: ${operatorInfo.addresses.map { "${it.streetAddress}, ${it.locality}, ${it.countryName}" }}")
            println("  Info URIs: ${operatorInfo.informationUris.map { "${it.lang}=${it.uri}" }}")
            println("  Roles: ${operatorInfo.roles.map { it.value }}")

            assertEquals(TrustAnchorType.ETSI_TSL, operatorInfo.sourceType)
            assertEquals(TrustChainNodeRole.TRUST_ANCHOR, operatorInfo.chainPosition.role)
            assertEquals("NL", operatorInfo.jurisdiction)
            assertTrue(operatorInfo.names.isNotEmpty(), "Scheme operator should have names")
            assertNotNull(operatorInfo.organizationName, "Should have organization name")

            val contact = operatorInfo.toContact()
            println("  -> Contact: displayName=${contact.displayName}, org=${contact.organizationName}, jurisdiction=${contact.jurisdiction}")
        }

    // -- NL Trust List: Entity Info --

    @Test
    fun extractsAllNlEntitiesFromFidesTl() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)

            println("--- NL Trust List: ${tl.trustedEntities.size} entities ---")
            println("  Territory: ${tl.schemeTerritory}")
            println()

            for (entity in tl.trustedEntities) {
                val serviceType =
                    entity.trustedEntityServices
                        .firstOrNull()
                        ?.serviceInformation
                        ?.serviceTypeIdentifier

                val info =
                    extractor.mapEntity(
                        entity = entity,
                        territory = tl.schemeTerritory,
                        matchedServiceType = serviceType,
                        depth = 0,
                        nodeRole = TrustChainNodeRole.LEAF,
                    )

                val contact = info.toContact()

                println("  [${info.entityIdentifier}]")
                println("    Organization: ${contact.organizationName}")
                println("    Names: ${info.names.map { "${it.lang}=${it.value}" }}")
                println("    Emails: ${contact.emails}")
                println("    URLs: ${contact.urls}")
                println("    Addresses: ${info.addresses.map { "${it.streetAddress}, ${it.locality}, ${it.countryName}" }}")
                println("    Jurisdiction: ${contact.jurisdiction}")
                println("    Roles: ${info.roles.map { it.value }}")
                println("    Service type: $serviceType")
                println()

                // Every entity should have at least a name and jurisdiction
                assertTrue(info.names.isNotEmpty(), "${info.entityIdentifier} should have names")
                assertEquals("NL", info.jurisdiction)
                assertNotNull(info.organizationName)
            }
        }

    @Test
    fun extractsFidesLabsEntityInfo() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val fidesEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "FIDES Labs"
                }

            val info =
                extractor.mapEntity(
                    entity = fidesEntity,
                    territory = tl.schemeTerritory,
                    matchedServiceType =
                        fidesEntity.trustedEntityServices
                            .first()
                            .serviceInformation.serviceTypeIdentifier,
                    depth = 0,
                    nodeRole = TrustChainNodeRole.LEAF,
                )

            println("--- FIDES Labs Entity Info ---")
            println("  Identifier: ${info.entityIdentifier}")
            println("  Organization: ${info.organizationName}")
            println("  Names: ${info.names.map { "${it.lang}=${it.value}" }}")
            println("  Trade names: ${info.tradeNames.map { "${it.lang}=${it.value}" }}")
            println("  Contacts: ${info.contacts.map { "${it.type}: ${it.value}" }}")
            println("  Addresses: ${info.addresses.map { addr -> "${addr.streetAddress}, ${addr.locality} ${addr.postalCode}, ${addr.countryName}" }}")
            println("  Info URIs: ${info.informationUris.map { "${it.lang}=${it.uri}" }}")
            println("  Jurisdiction: ${info.jurisdiction}")
            println("  Roles: ${info.roles.map { it.value }}")

            val contact = info.toContact()
            println("\n  -> DiscoveredContact:")
            println("     Display name: ${contact.displayName}")
            println("     Organization: ${contact.organizationName}")
            println("     Emails: ${contact.emails}")
            println("     URLs: ${contact.urls}")
            println("     Jurisdiction: ${contact.jurisdiction}")
            println("     Address: ${contact.address?.let { "${it.streetAddress}, ${it.locality} ${it.postalCode}, ${it.countryName}" }}")
            println("     Logo: ${contact.logoUri}")

            assertEquals("FIDES Labs", info.organizationName)
            assertEquals("NL", info.jurisdiction)
            assertEquals(TrustAnchorType.ETSI_TSL, info.sourceType)
        }

    @Test
    fun extractsKvkEntityInfo() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val kvkEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "Kamer van Koophandel"
                }

            val info =
                extractor.mapEntity(
                    entity = kvkEntity,
                    territory = tl.schemeTerritory,
                    matchedServiceType =
                        kvkEntity.trustedEntityServices
                            .first()
                            .serviceInformation.serviceTypeIdentifier,
                    depth = 0,
                    nodeRole = TrustChainNodeRole.LEAF,
                )

            println("--- Kamer van Koophandel Entity Info ---")
            println("  Organization: ${info.organizationName}")
            println("  Names: ${info.names.map { "${it.lang}=${it.value}" }}")
            println("  Contacts: ${info.contacts.map { "${it.type}: ${it.value}" }}")
            println("  Addresses: ${info.addresses.map { addr -> "${addr.streetAddress}, ${addr.locality} ${addr.postalCode}, ${addr.countryName}" }}")
            println("  Jurisdiction: ${info.jurisdiction}")
            println("  Roles: ${info.roles.map { it.value }}")

            val contact = info.toContact()
            println("\n  -> DiscoveredContact:")
            println("     Display name: ${contact.displayName}")
            println("     Organization: ${contact.organizationName}")
            println("     Emails: ${contact.emails}")
            println("     URLs: ${contact.urls}")
            println("     Address: ${contact.address?.let { "${it.streetAddress}, ${it.locality} ${it.postalCode}, ${it.countryName}" }}")

            assertEquals("Kamer van Koophandel", info.organizationName)
            assertEquals("NL", info.jurisdiction)
        }

    // -- LOTL → NL TL navigation --

    @Test
    fun navigatesLotlToNlTlAndExtractsEntities() =
        runTest {
            // Parse LOTL
            val lotlXml = ctx.fetchUrl(FIDES_LOTL_URL)
            val lotl = parser.parseFromString(lotlXml)

            // Find NL pointer in LOTL
            val nlPointer = lotl.pointersToOtherLoTE.firstOrNull { it.schemeTerritory == "NL" }
            assertNotNull(nlPointer, "LOTL should have NL pointer")

            println("--- LOTL → NL Trust List Navigation ---")
            println("  LOTL territory: ${lotl.schemeTerritory}")
            println("  NL pointer location: ${nlPointer.location}")
            println("  NL pointer operator: ${nlPointer.schemeOperatorName.firstOrNull()?.value}")

            // Fetch and parse NL TL via the pointer
            val nlTlXml = ctx.fetchUrl(nlPointer.location)
            val nlTl = parser.parseFromString(nlTlXml)

            println("  NL TL territory: ${nlTl.schemeTerritory}")
            println("  NL TL entities: ${nlTl.trustedEntities.size}")
            println()

            // Extract entity info from LOTL operator (trust anchor level)
            val lotlOperator = extractor.mapSchemeOperator(lotl, depth = 1)

            // Extract entity info from all NL entities
            val nlEntities =
                nlTl.trustedEntities.mapIndexed { index, entity ->
                    extractor.mapEntity(
                        entity = entity,
                        territory = nlTl.schemeTerritory,
                        matchedServiceType =
                            entity.trustedEntityServices
                                .firstOrNull()
                                ?.serviceInformation
                                ?.serviceTypeIdentifier,
                        depth = 0,
                        nodeRole = TrustChainNodeRole.LEAF,
                    )
                }

            // Print full chain: entities + anchor
            println("  Trust chain entity info:")
            for (entityInfo in nlEntities) {
                val contact = entityInfo.toContact()
                println("    [depth=${entityInfo.chainPosition.depth}, role=${entityInfo.chainPosition.role}] ${contact.organizationName}")
                println("      Emails: ${contact.emails}, URLs: ${contact.urls}")
                println("      Address: ${contact.address?.let { "${it.streetAddress}, ${it.locality}, ${it.countryName}" } ?: "none"}")
                println("      Roles: ${entityInfo.roles.map { it.value }}")
            }

            val anchorContact = lotlOperator.toContact()
            println("    [depth=${lotlOperator.chainPosition.depth}, role=${lotlOperator.chainPosition.role}] ${anchorContact.organizationName} (scheme operator)")
            println("      Emails: ${anchorContact.emails}, URLs: ${anchorContact.urls}")

            assertTrue(nlEntities.isNotEmpty(), "Should have NL entities")
            assertTrue(nlEntities.all { it.jurisdiction == "NL" }, "All NL entities should have NL jurisdiction")
        }
}
