/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.mdoc.data.eu

import com.sphereon.cbor.CDDL
import com.sphereon.mdoc.data.Presence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for PID definitions and related classes.
 */
class PIDDefinitionsTest {
    @Test
    fun testPidNamespaceLiteral() {
        assertEquals("eu.europa.ec.eudi.pid.1", Pid.NAMESPACE_LITERAL)
    }

    @Test
    fun testPidNamespace() {
        assertEquals("eu.europa.ec.eudi.pid.1", Pid.NAMESPACE.toString())
    }

    @Test
    fun testPidNamespaceCbor() {
        assertEquals("eu.europa.ec.eudi.pid.1", Pid.NAMESPACE_CBOR.value)
    }

    @Test
    fun testPidDocType() {
        assertEquals("eu.europa.ec.eudi.pid.1", Pid.DOCTYPE.toString())
    }

    // FamilyNamePidDef tests
    @Test
    fun testFamilyNamePidDef() {
        val def = FamilyNamePidDef()
        assertEquals("family_name", def.identifier.toString())
        assertEquals(Pid.NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertTrue(def.details.contains("last name"))
        assertTrue(def.cddls.contains(CDDL.tstr))
    }

    // GivenNamePidDef tests
    @Test
    fun testGivenNamePidDef() {
        val def = GivenNamePidDef()
        assertEquals("given_name", def.identifier.toString())
        assertEquals(Pid.NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertTrue(def.details.contains("first name"))
    }

    // BirthDatePidDef tests
    @Test
    fun testBirthDatePidDef() {
        val def = BirthDatePidDef()
        assertEquals("birth_date", def.identifier.toString())
        assertEquals(Pid.NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertTrue(def.cddls.contains(CDDL.full_date))
    }

    // AgeOverDef tests
    @Test
    fun testAgeOverDef() {
        val def = AgeOverDef(21)
        assertEquals("age_over_21", def.identifier.toString())
        assertEquals(21, def.age)
        assertEquals(Pid.NAMESPACE, def.nameSpace)
        assertEquals(Presence.OPTIONAL, def.presence)
        assertTrue(def.cddls.contains(CDDL.bool))
    }

    @Test
    fun testAgeOver18() {
        val def = AgeOver18()
        assertEquals("age_over_18", def.identifier.toString())
        assertEquals(18, def.age)
        assertEquals(Presence.OPTIONAL, def.presence)
        assertTrue(def.details.contains("adult"))
    }

    @Test
    fun testAgeOverNN() {
        val def = Pid.Def.age_over_NN(25)
        assertEquals("age_over_25", def.identifier.toString())
        assertEquals(25, def.age)
    }

    // AgeInYearsPidDef tests
    @Test
    fun testAgeInYearsPidDef() {
        val def = AgeInYearsPidDef()
        assertEquals("age_in_years", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
        assertTrue(def.cddls.contains(CDDL.uint))
    }

    // AgeBirthYearPidDef tests
    @Test
    fun testAgeBirthYearPidDef() {
        val def = AgeBirthYearPidDef()
        assertEquals("age_birth_year", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
        assertTrue(def.cddls.contains(CDDL.uint))
    }

    // FamilyNameBirthPidDef tests
    @Test
    fun testFamilyNameBirthPidDef() {
        val def = FamilyNameBirthPidDef()
        assertEquals("family_name_birth", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // GivenNameBirthPidDef tests
    @Test
    fun testGivenNameBirthPidDef() {
        val def = GivenNameBirthPidDef()
        assertEquals("given_name_birth", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // BirthPlacePidDef tests
    @Test
    fun testBirthPlacePidDef() {
        val def = BirthPlacePidDef()
        assertEquals("birth_place", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // BirthCountryPidDef tests
    @Test
    fun testBirthCountryPidDef() {
        val def = BirthCountryPidDef()
        assertEquals("birth_country", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
        assertTrue(def.details.contains("Alpha-2"))
    }

    // BirthStatePidDef tests
    @Test
    fun testBirthStatePidDef() {
        val def = BirthStatePidDef()
        assertEquals("birth_state", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // BirthCityPidDef tests
    @Test
    fun testBirthCityPidDef() {
        val def = BirthCityPidDef()
        assertEquals("birth_city", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentAddressPidDef tests
    @Test
    fun testResidentAddressPidDef() {
        val def = ResidentAddressPidDef()
        assertEquals("resident_address", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentCountryPidDef tests
    @Test
    fun testResidentCountryPidDef() {
        val def = ResidentCountryPidDef()
        assertEquals("resident_country", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentStatePidDef tests
    @Test
    fun testResidentStatePidDef() {
        val def = ResidentStatePidDef()
        assertEquals("resident_state", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentCityPidDef tests
    @Test
    fun testResidentCityPidDef() {
        val def = ResidentCityPidDef()
        assertEquals("resident_city", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentPostalCodePidDef tests
    @Test
    fun testResidentPostalCodePidDef() {
        val def = ResidentPostalCodePidDef()
        assertEquals("resident_postal_code", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentStreetPidDef tests
    @Test
    fun testResidentStreetPidDef() {
        val def = ResidentStreetPidDef()
        assertEquals("resident_street", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // ResidentHouseNumberPidDef tests
    @Test
    fun testResidentHouseNumberPidDef() {
        val def = ResidentHouseNumberPidDef()
        assertEquals("resident_house_number", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // GenderPidDef tests
    @Test
    fun testGenderPidDef() {
        val def = GenderPidDef()
        assertEquals("gender", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
        assertTrue(def.cddls.contains(CDDL.uint))
    }

    // NationalityPidDef tests
    @Test
    fun testNationalityPidDef() {
        val def = NationalityPidDef()
        assertEquals("nationality", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // IssuanceDatePidDef tests
    @Test
    fun testIssuanceDatePidDef() {
        val def = IssuanceDatePidDef()
        assertEquals("issuance_date", def.identifier.toString())
        assertEquals(Presence.MANDATORY, def.presence)
        assertTrue(def.cddls.contains(CDDL.full_date))
    }

    // ExpiryDatePidDef tests
    @Test
    fun testExpiryDatePidDef() {
        val def = ExpiryDatePidDef()
        assertEquals("expiry_date", def.identifier.toString())
        assertEquals(Presence.MANDATORY, def.presence)
    }

    // IssuingAuthorityPidDef tests
    @Test
    fun testIssuingAuthorityPidDef() {
        val def = IssuingAuthorityPidDef()
        assertEquals("issuing_authority", def.identifier.toString())
        assertEquals(Presence.MANDATORY, def.presence)
    }

    // DocumentNumberPidDef tests
    @Test
    fun testDocumentNumberPidDef() {
        val def = DocumentNumberPidDef()
        assertEquals("document_number", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // AdministrativeNumberPidDef tests
    @Test
    fun testAdministrativeNumberPidDef() {
        val def = AdministrativeNumberPidDef()
        assertEquals("document_number", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // IssuingCountryPidDef tests
    @Test
    fun testIssuingCountryPidDef() {
        val def = IssuingCountryPidDef()
        assertEquals("issuing_country", def.identifier.toString())
        assertEquals(Presence.MANDATORY, def.presence)
    }

    // IssuingJurisdictionPidDef tests
    @Test
    fun testIssuingJurisdictionPidDef() {
        val def = IssuingJurisdictionPidDef()
        assertEquals("document_number", def.identifier.toString())
        assertEquals(Presence.OPTIONAL, def.presence)
    }

    // Pid.Def object tests
    @Test
    fun testPidDefFamilyName() {
        assertNotNull(Pid.Def.family_name)
        assertEquals(
            "family_name",
            Pid.Def.family_name.identifier
                .toString(),
        )
    }

    @Test
    fun testPidDefGivenName() {
        assertNotNull(Pid.Def.given_name)
        assertEquals(
            "given_name",
            Pid.Def.given_name.identifier
                .toString(),
        )
    }

    @Test
    fun testPidDefBirthDate() {
        assertNotNull(Pid.Def.birth_date)
        assertEquals(
            "birth_date",
            Pid.Def.birth_date.identifier
                .toString(),
        )
    }

    @Test
    fun testPidDefAgeOver18() {
        assertNotNull(Pid.Def.age_over_18)
        assertEquals(
            "age_over_18",
            Pid.Def.age_over_18.identifier
                .toString(),
        )
    }

    @Test
    fun testPidAsDefsList() {
        val defs = Pid.asDefs
        assertTrue(defs.size >= 27, "Expected at least 27 definitions")
    }

    // Pid.asDef wrapper tests
    @Test
    fun testAsDefWrapper() {
        val def = Pid.asDef(FamilyNamePidDef())
        assertTrue(def.isMandatory)
        assertEquals(Pid.NAMESPACE.toString(), def.nameSpaceStr)
        assertEquals("family_name", def.identifierStr)
    }

    @Test
    fun testAsDefWrapperOptional() {
        val def = Pid.asDef(AgeInYearsPidDef())
        assertFalse(def.isMandatory)
    }

    // All definitions have correct namespace
    @Test
    fun testAllDefinitionsHaveCorrectNamespace() {
        val definitions =
            listOf(
                FamilyNamePidDef(),
                GivenNamePidDef(),
                BirthDatePidDef(),
                AgeOver18(),
                AgeInYearsPidDef(),
                AgeBirthYearPidDef(),
                FamilyNameBirthPidDef(),
                GivenNameBirthPidDef(),
                BirthPlacePidDef(),
                BirthCountryPidDef(),
                BirthStatePidDef(),
                BirthCityPidDef(),
                ResidentAddressPidDef(),
                ResidentCountryPidDef(),
                ResidentStatePidDef(),
                ResidentCityPidDef(),
                ResidentPostalCodePidDef(),
                ResidentStreetPidDef(),
                ResidentHouseNumberPidDef(),
                GenderPidDef(),
                NationalityPidDef(),
                IssuanceDatePidDef(),
                ExpiryDatePidDef(),
                IssuingAuthorityPidDef(),
                DocumentNumberPidDef(),
                AdministrativeNumberPidDef(),
                IssuingCountryPidDef(),
                IssuingJurisdictionPidDef(),
            )

        definitions.forEach { def ->
            assertEquals(
                Pid.NAMESPACE,
                def.nameSpace,
                "Definition ${def.identifier} should have PID namespace",
            )
        }
    }

    // Test mandatory fields
    @Test
    fun testMandatoryFields() {
        val mandatoryDefs =
            listOf(
                FamilyNamePidDef(),
                GivenNamePidDef(),
                BirthDatePidDef(),
                IssuanceDatePidDef(),
                ExpiryDatePidDef(),
                IssuingAuthorityPidDef(),
                IssuingCountryPidDef(),
            )

        mandatoryDefs.forEach { def ->
            assertEquals(
                Presence.MANDATORY,
                def.presence,
                "Definition ${def.identifier} should be mandatory",
            )
        }
    }

    // Test optional fields
    @Test
    fun testOptionalFields() {
        val optionalDefs =
            listOf(
                AgeOver18(),
                AgeInYearsPidDef(),
                AgeBirthYearPidDef(),
                FamilyNameBirthPidDef(),
                GivenNameBirthPidDef(),
                BirthPlacePidDef(),
                BirthCountryPidDef(),
                BirthStatePidDef(),
                BirthCityPidDef(),
                ResidentAddressPidDef(),
                ResidentCountryPidDef(),
                ResidentStatePidDef(),
                ResidentCityPidDef(),
                ResidentPostalCodePidDef(),
                ResidentStreetPidDef(),
                ResidentHouseNumberPidDef(),
                GenderPidDef(),
                NationalityPidDef(),
                DocumentNumberPidDef(),
                AdministrativeNumberPidDef(),
                IssuingJurisdictionPidDef(),
            )

        optionalDefs.forEach { def ->
            assertEquals(
                Presence.OPTIONAL,
                def.presence,
                "Definition ${def.identifier} should be optional",
            )
        }
    }
}
