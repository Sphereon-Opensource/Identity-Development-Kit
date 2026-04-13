package com.sphereon.core.api.service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceTargetDescriptorTest {
    @Test
    fun dslBuildsDescriptorCorrectly() {
        val target =
            resourceTarget("kms.key") {
                parent("kms.provider")
                identifier("provider_id", required = true)
                identifier("alias")
                constraint("algorithm")
                constraint("max_lifetime_days")
                context("tenant_id")
            }

        assertEquals("kms.key", target.resourceType)
        assertEquals(listOf("kms.provider"), target.parentResources)
        assertEquals(5, target.attributeSchema.size)

        val providerAttr = target.attributeSchema.first { it.name == "provider_id" }
        assertTrue(providerAttr.required)
        assertEquals(AttributeKind.IDENTIFIER, providerAttr.kind)

        val algoAttr = target.attributeSchema.first { it.name == "algorithm" }
        assertEquals(AttributeKind.CONSTRAINT, algoAttr.kind)

        val tenantAttr = target.attributeSchema.first { it.name == "tenant_id" }
        assertEquals(AttributeKind.CONTEXT, tenantAttr.kind)
    }

    @Test
    fun unspecifiedHasDefaultValues() {
        val unspecified = ResourceTargetDescriptor.UNSPECIFIED
        assertEquals("unspecified", unspecified.resourceType)
        assertTrue(unspecified.attributeSchema.isEmpty())
        assertTrue(unspecified.parentResources.isEmpty())
    }

    @Test
    fun emptyDslProducesMinimalDescriptor() {
        val target = resourceTarget("blob.object")
        assertEquals("blob.object", target.resourceType)
        assertTrue(target.attributeSchema.isEmpty())
        assertTrue(target.parentResources.isEmpty())
    }

    @Test
    fun multipleParentsArePreserved() {
        val target =
            resourceTarget("eidas.signature") {
                parent("kms.key")
                parent("eidas.config")
            }
        assertEquals(listOf("kms.key", "eidas.config"), target.parentResources)
    }
}
