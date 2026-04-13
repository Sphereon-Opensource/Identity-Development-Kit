package com.sphereon.core.api.service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaOverlayTest {
    @Test
    fun dslBuildsOverlayCorrectly() {
        val overlay =
            schemaOverlay {
                "providerId" {
                    label("Provider")
                    description("KMS provider identifier")
                    identifier("provider_id", resource = "kms.provider")
                }
                "alias" {
                    label("Key Alias")
                    identifier("alias")
                }
                "algorithm" {
                    label("Algorithm")
                    constraint("algorithm")
                    example("EdDSA")
                }
                "email" {
                    confidential()
                }
                "nationalId" {
                    regulated()
                }
            }

        assertEquals(5, overlay.fields.size)

        val provider = overlay.fields["providerId"]!!
        assertEquals("Provider", provider.label)
        assertEquals("KMS provider identifier", provider.description)
        assertNotNull(provider.policyMapping)
        assertEquals("provider_id", provider.policyMapping!!.attributeName)
        assertEquals("kms.provider", provider.policyMapping!!.resourceType)
        assertEquals(AttributeKind.IDENTIFIER, provider.policyMapping!!.kind)

        val algo = overlay.fields["algorithm"]!!
        assertEquals("EdDSA", algo.example)
        assertEquals(AttributeKind.CONSTRAINT, algo.policyMapping!!.kind)
        assertNull(algo.policyMapping!!.resourceType)

        val email = overlay.fields["email"]!!
        assertEquals(SensitivityClassification.CONFIDENTIAL, email.sensitivity)

        val nationalId = overlay.fields["nationalId"]!!
        assertEquals(SensitivityClassification.REGULATED, nationalId.sensitivity)
    }

    @Test
    fun emptyOverlayIsEmpty() {
        val overlay = SchemaOverlay.EMPTY
        assertTrue(overlay.fields.isEmpty())
    }

    @Test
    fun dslWithNoFieldsProducesEmpty() {
        val overlay = schemaOverlay { }
        assertTrue(overlay.fields.isEmpty())
    }
}
