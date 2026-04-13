/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EntityDiscoveryModelsTest {
    @Test
    fun toContactExtractsEmailsAndPhones() {
        val entity =
            DiscoveredEntityInfo(
                entityIdentifier = "https://issuer.example.com",
                sourceType = TrustAnchorType.OPENID_FEDERATION,
                chainPosition = TrustChainPosition(depth = 0, role = TrustChainNodeRole.LEAF),
                names =
                    listOf(
                        LocalizedString(lang = "en", value = "Example Issuer"),
                        LocalizedString(lang = "nl", value = "Voorbeeld Uitgever"),
                    ),
                display =
                    listOf(
                        EntityDisplay(name = "Example Issuer", locale = "en", logo = EntityLogo(uri = "https://example.com/logo.png", altText = "Logo")),
                    ),
                contacts =
                    listOf(
                        EntityContact(type = ContactType.EMAIL, value = "info@example.com"),
                        EntityContact(type = ContactType.EMAIL, value = "support@example.com"),
                        EntityContact(type = ContactType.PHONE, value = "+31-20-1234567"),
                        EntityContact(type = ContactType.URL, value = "https://example.com/contact"),
                    ),
                addresses =
                    listOf(
                        EntityAddress(streetAddress = "Keizersgracht 100", locality = "Amsterdam", postalCode = "1015 AA", countryName = "NL"),
                    ),
                logos = listOf(EntityLogo(uri = "https://example.com/logo.png")),
                organizationName = "Example Corp",
                jurisdiction = "NL",
                roles = listOf(EntityRole.ISSUER, EntityRole.GENERAL),
            )

        val contact = entity.toContact()

        println("--- toContact() Conversion ---")
        println("  Display name: ${contact.displayName}")
        println("  Organization: ${contact.organizationName}")
        println("  Emails: ${contact.emails}")
        println("  Phones: ${contact.phones}")
        println("  URLs: ${contact.urls}")
        println("  Jurisdiction: ${contact.jurisdiction}")
        println("  Address: ${contact.address?.streetAddress}, ${contact.address?.locality}, ${contact.address?.countryName}")
        println("  Logo: ${contact.logoUri}")

        assertEquals("Example Issuer", contact.displayName)
        assertEquals("Example Corp", contact.organizationName)
        assertEquals(listOf("info@example.com", "support@example.com"), contact.emails)
        assertEquals(listOf("+31-20-1234567"), contact.phones)
        assertEquals(listOf("https://example.com/contact"), contact.urls)
        assertEquals("NL", contact.jurisdiction)
        assertNotNull(contact.address)
        assertEquals("Amsterdam", contact.address?.locality)
        assertEquals("https://example.com/logo.png", contact.logoUri)
    }

    @Test
    fun toContactFallsBackToNamesWhenNoDisplay() {
        val entity =
            DiscoveredEntityInfo(
                entityIdentifier = "CN=Example CA,O=Example Corp,C=US",
                sourceType = TrustAnchorType.X509_CA_BUNDLE,
                chainPosition = TrustChainPosition(depth = 0, role = TrustChainNodeRole.LEAF),
                names =
                    listOf(
                        LocalizedString(lang = "und", value = "Example CA"),
                        LocalizedString(lang = "und", value = "Example Corp"),
                    ),
                organizationName = "Example Corp",
                jurisdiction = "US",
            )

        val contact = entity.toContact()

        println("--- X.509 Contact (no display) ---")
        println("  Display name: ${contact.displayName}")
        println("  Organization: ${contact.organizationName}")
        println("  Jurisdiction: ${contact.jurisdiction}")

        assertEquals("Example CA", contact.displayName, "Should fall back to first name")
        assertEquals("Example Corp", contact.organizationName)
        assertEquals("US", contact.jurisdiction)
    }

    @Test
    fun toContactFallsBackToInformationUrisWhenNoUrlContacts() {
        val entity =
            DiscoveredEntityInfo(
                entityIdentifier = "did:web:example.com",
                sourceType = TrustAnchorType.DID,
                chainPosition = TrustChainPosition(depth = 0, role = TrustChainNodeRole.LEAF),
                informationUris =
                    listOf(
                        LocalizedUri(lang = "und", uri = "https://example.com/about"),
                        LocalizedUri(lang = "und", uri = "https://example.com/api"),
                    ),
                roles = listOf(EntityRole.GENERAL),
            )

        val contact = entity.toContact()

        println("--- DID Contact (informationUris fallback) ---")
        println("  URLs: ${contact.urls}")

        assertEquals(2, contact.urls.size)
        assertTrue(contact.urls.contains("https://example.com/about"))
    }

    @Test
    fun entityRoleMappingOidfed() {
        val issuerKeys = EntityRoleMapping.toOidfedMetadataKeys(EntityRole.ISSUER)
        assertTrue(issuerKeys.contains("openid_credential_issuer"))
        assertTrue(issuerKeys.contains("vc_issuer"))

        val fromKey = EntityRoleMapping.fromOidfedMetadataKey("openid_credential_issuer")
        assertEquals(EntityRole.ISSUER, fromKey)

        val verifierKeys = EntityRoleMapping.toOidfedMetadataKeys(EntityRole.VERIFIER)
        assertTrue(verifierKeys.contains("openid_relying_party"))
    }

    @Test
    fun entityRoleMappingEtsi() {
        val issuerTypes = EntityRoleMapping.toEtsiServiceTypes(EntityRole.ISSUER)
        assertTrue(issuerTypes.any { it.contains("PIDIssuer") })
        assertTrue(issuerTypes.any { it.contains("QEAAIssuer") })

        val fromPid = EntityRoleMapping.fromEtsiServiceType("http://uri.etsi.org/TrstSvc/Svctype/IdV/nothrust/PIDIssuer")
        assertEquals(EntityRole.ISSUER, fromPid)

        val fromWallet = EntityRoleMapping.fromEtsiServiceType("http://uri.etsi.org/something/WalletProvider")
        assertEquals(EntityRole.WALLET, fromWallet)
    }

    @Test
    fun inferContactTypeDetectsTypes() {
        assertEquals(ContactType.EMAIL, inferContactType("info@example.com"))
        assertEquals(ContactType.URL, inferContactType("https://example.com"))
        assertEquals(ContactType.URL, inferContactType("http://example.com"))
        assertEquals(ContactType.PHONE, inferContactType("+31-20-1234567"))
        assertEquals(ContactType.OTHER, inferContactType("some-opaque-identifier"))
    }

    @Test
    fun localizedStringExtensions() {
        val strings =
            listOf(
                LocalizedString(lang = "en", value = "English"),
                LocalizedString(lang = "nl", value = "Nederlands"),
                LocalizedString(lang = "de", value = "Deutsch"),
            )

        assertEquals("English", strings.forLang("en"))
        assertEquals("Nederlands", strings.forLang("NL")) // case-insensitive
        assertEquals(null, strings.forLang("fr"))

        val map = strings.toLangMap()
        assertEquals(3, map.size)
        assertEquals("English", map["en"])
    }
}
