package com.sphereon.core.api.service.contract

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceInstanceTest {
    @Test
    fun dslBuildsInstancesCorrectly() {
        val instances =
            resourceInstances {
                resource("kms.provider") {
                    id("software-default")
                    attr("provider_id", "software-default")
                }
                resource("kms.key") {
                    id("provider:software-default:alias:my-key")
                    attr("provider_id", "software-default")
                    attr("alias", "my-key")
                    attrIfNotNull("algorithm", "EdDSA")
                    attrIfNotNull("max_lifetime_days", 365)
                    attrIfNotNull("optional_field", null as String?)
                }
            }

        assertEquals(2, instances.size)

        val provider = instances[0]
        assertEquals("kms.provider", provider.resourceType)
        assertEquals("software-default", provider.resourceId)
        assertEquals(JsonPrimitive("software-default"), provider.attributes["provider_id"])

        val key = instances[1]
        assertEquals("kms.key", key.resourceType)
        assertEquals("provider:software-default:alias:my-key", key.resourceId)
        assertEquals(4, key.attributes.size)
        assertEquals(JsonPrimitive("EdDSA"), key.attributes["algorithm"])
        assertEquals(JsonPrimitive(365), key.attributes["max_lifetime_days"])
        assertNull(key.attributes["optional_field"])
    }

    @Test
    fun emptyDslProducesEmptyList() {
        val instances = resourceInstances { }
        assertTrue(instances.isEmpty())
    }

    @Test
    fun resourceWithNoAttributesIsValid() {
        val instances =
            resourceInstances {
                resource("health.check") { }
            }
        assertEquals(1, instances.size)
        assertNull(instances[0].resourceId)
        assertTrue(instances[0].attributes.isEmpty())
    }
}
