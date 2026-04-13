package com.sphereon.core.api.service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssuranceRequirementsTest {
    @Test
    fun unspecifiedHasAllDefaults() {
        val req = AssuranceRequirements.UNSPECIFIED
        assertNull(req.minimumAal)
        assertNull(req.maxAuthAgeSecs)
        assertTrue(req.requiredAmr.isEmpty())
        assertFalse(req.requiresDualControl)
    }

    @Test
    fun dslBuildsRequirements() {
        val req =
            assurance {
                aal2()
                requireAmr("mfa", "hwk")
                maxAuthAge(300)
                requireDualControl()
            }
        assertEquals(AuthAssuranceLevel.AAL2, req.minimumAal)
        assertEquals(setOf("mfa", "hwk"), req.requiredAmr)
        assertEquals(300L, req.maxAuthAgeSecs)
        assertTrue(req.requiresDualControl)
    }

    @Test
    fun authAssuranceLevelAcrValues() {
        assertEquals("urn:nist:sp:800-63:aal1", AuthAssuranceLevel.AAL1.acr)
        assertEquals("urn:nist:sp:800-63:aal2", AuthAssuranceLevel.AAL2.acr)
        assertEquals("urn:nist:sp:800-63:aal3", AuthAssuranceLevel.AAL3.acr)
    }

    @Test
    fun dslWithOnlyAal() {
        val req = assurance { aal3() }
        assertEquals(AuthAssuranceLevel.AAL3, req.minimumAal)
        assertNull(req.maxAuthAgeSecs)
        assertTrue(req.requiredAmr.isEmpty())
    }
}
