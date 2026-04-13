package com.sphereon.core.api.service.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComplianceProfileTest {
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = true
        }

    @Test
    fun noneProfileHasAllDefaults() {
        val profile = ComplianceProfile.NONE
        assertTrue(profile.applicableFrameworks.isEmpty())
        assertTrue(profile.dataProcessingBasis.isEmpty())
        assertTrue(profile.requiredConsents.isEmpty())
        assertFalse(profile.dataProcessingActivity)
        assertNull(profile.retentionDays)
        assertFalse(profile.requiresDpia)
    }

    @Test
    fun dslBuildsFullProfile() {
        val profile =
            complianceProfile {
                frameworks(listOf(RegulatoryFramework.GDPR, RegulatoryFramework.EIDAS2))
                processingBasis(DataProcessingBasis.EXPLICIT_CONSENT)
                requiresConsent("biometric_processing", "data_sharing")
                dataProcessingActivity()
                retentionDays(90)
                requiresDpia()
            }

        assertEquals(setOf(RegulatoryFramework.GDPR, RegulatoryFramework.EIDAS2), profile.applicableFrameworks)
        assertEquals(setOf(DataProcessingBasis.EXPLICIT_CONSENT), profile.dataProcessingBasis)
        assertEquals(setOf("biometric_processing", "data_sharing"), profile.requiredConsents)
        assertTrue(profile.dataProcessingActivity)
        assertEquals(90, profile.retentionDays)
        assertTrue(profile.requiresDpia)
    }

    @Test
    fun dslWithFrameworkOnly() {
        val profile =
            complianceProfile {
                framework(RegulatoryFramework.EIDAS)
            }
        assertEquals(setOf(RegulatoryFramework.EIDAS), profile.applicableFrameworks)
        assertTrue(profile.dataProcessingBasis.isEmpty())
        assertFalse(profile.dataProcessingActivity)
    }

    @Test
    fun regulatoryFrameworkIsExtensible() {
        val custom = RegulatoryFramework("my_country.data_protection_act")
        assertEquals("my_country.data_protection_act", custom.value)
    }

    @Test
    fun dataProcessingBasisIsExtensible() {
        val custom = DataProcessingBasis("lgpd.art7.consent")
        assertEquals("lgpd.art7.consent", custom.value)
    }

    @Test
    fun standardFrameworkValues() {
        assertEquals("gdpr", RegulatoryFramework.GDPR.value)
        assertEquals("eidas", RegulatoryFramework.EIDAS.value)
        assertEquals("eidas2", RegulatoryFramework.EIDAS2.value)
        assertEquals("nis2", RegulatoryFramework.NIS2.value)
        assertEquals("dora", RegulatoryFramework.DORA.value)
        assertEquals("iso27001", RegulatoryFramework.ISO27001.value)
        assertEquals("amlr", RegulatoryFramework.AMLR.value)
    }

    @Test
    fun standardProcessingBasisValues() {
        assertEquals("gdpr.art6.1a.consent", DataProcessingBasis.CONSENT.value)
        assertEquals("gdpr.art6.1b.contract", DataProcessingBasis.CONTRACT.value)
        assertEquals("gdpr.art9.2a.explicit_consent", DataProcessingBasis.EXPLICIT_CONSENT.value)
        assertEquals("amlr.art56.record_keeping", DataProcessingBasis.AML_RECORD_KEEPING.value)
    }

    @Test
    fun serializationRoundTrip() {
        val profile =
            complianceProfile {
                frameworks(listOf(RegulatoryFramework.GDPR, RegulatoryFramework.EIDAS))
                processingBasis(DataProcessingBasis.CONTRACT)
                requiresConsent("data_sharing")
                dataProcessingActivity()
                retentionDays(365)
            }

        val serialized = json.encodeToString(profile)
        val deserialized = json.decodeFromString<ComplianceProfile>(serialized)

        assertEquals(profile.applicableFrameworks, deserialized.applicableFrameworks)
        assertEquals(profile.dataProcessingBasis, deserialized.dataProcessingBasis)
        assertEquals(profile.requiredConsents, deserialized.requiredConsents)
        assertEquals(profile.dataProcessingActivity, deserialized.dataProcessingActivity)
        assertEquals(profile.retentionDays, deserialized.retentionDays)
        assertEquals(profile.requiresDpia, deserialized.requiresDpia)
    }
}
