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

package com.sphereon.mdoc.data.mdl

import com.sphereon.cbor.CDDL
import com.sphereon.mdoc.data.Presence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for MDL definitions and related classes.
 */
class MdlDefinitionsTest {
    @Test
    fun testMdlNamespaceLiteral() {
        assertEquals("org.iso.18013.5.1.mDL", Mdl.MDL_NAMESPACE_LITERAL)
    }

    @Test
    fun testMdlNamespace() {
        assertEquals("org.iso.18013.5.1.mDL", Mdl.MDL_NAMESPACE.toString())
    }

    @Test
    fun testMdlNamespaceCbor() {
        assertEquals("org.iso.18013.5.1.mDL", Mdl.MDL_NAMESPACE_CBOR.value)
    }

    // FamilyNameMdlDef tests

    @Test
    fun testFamilyNameMdlDef() {
        val def = FamilyNameMdlDef()
        assertEquals("family_name", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Family name", def.details)
        assertTrue(def.cddls.contains(CDDL.tstr))
    }

    @Test
    fun testFamilyNameMdlDefCddl() {
        val def = FamilyNameMdlDef()
        assertEquals(CDDL.tstr, def.cddl)
    }

    // GivenNameMdlDef tests

    @Test
    fun testGivenNameMdlDef() {
        val def = GivenNameMdlDef()
        assertEquals("given_name", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Given name", def.details)
        assertTrue(def.cddls.contains(CDDL.tstr))
    }

    // BirthDateMdlDef tests

    @Test
    fun testBirthDateMdlDef() {
        val def = BirthDateMdlDef()
        assertEquals("birth_date", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Date of birth", def.details)
        assertTrue(def.cddls.contains(CDDL.full_date))
    }

    // IssueDateMdlDef tests

    @Test
    fun testIssueDateMdlDef() {
        val def = IssueDateMdlDef()
        assertEquals("issue_date", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Date of issuance", def.details)
        assertTrue(def.cddls.contains(CDDL.full_date))
        assertTrue(def.cddls.contains(CDDL.tdate))
    }

    // ExpiryDateMdlDef tests

    @Test
    fun testExpiryDateMdlDef() {
        val def = ExpiryDateMdlDef()
        assertEquals("expiry_date", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Date of expiration", def.details)
        assertTrue(def.cddls.contains(CDDL.full_date))
        assertTrue(def.cddls.contains(CDDL.tdate))
    }

    // IssuingCountryMdlDef tests

    @Test
    fun testIssuingCountryMdlDef() {
        val def = IssuingCountryMdlDef()
        assertEquals("issuing_country", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Country of issuance", def.details)
        assertTrue(def.cddls.contains(CDDL.tstr))
    }

    // IssuingAuthorityMdlDef tests

    @Test
    fun testIssuingAuthorityMdlDef() {
        val def = IssuingAuthorityMdlDef()
        assertEquals("issuing_authority", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Authority of issuance", def.details)
        assertTrue(def.cddls.contains(CDDL.tstr))
    }

    // DocumentNumberMdlDef tests

    @Test
    fun testDocumentNumberMdlDef() {
        val def = DocumentNumberMdlDef()
        assertEquals("document_number", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Document number", def.details)
        assertTrue(def.cddls.contains(CDDL.tstr))
    }

    // PortraitMdlDef tests

    @Test
    fun testPortraitMdlDef() {
        val def = PortraitMdlDef()
        assertEquals("portrait", def.identifier.toString())
        assertEquals(Mdl.MDL_NAMESPACE, def.nameSpace)
        assertEquals(Presence.MANDATORY, def.presence)
        assertEquals("Portrait of holder", def.details)
        assertTrue(def.cddls.contains(CDDL.bstr))
    }

    // Mdl.Def object tests

    @Test
    fun testMdlDefFamilyName() {
        assertNotNull(Mdl.Def.family_name)
        assertEquals(
            "family_name",
            Mdl.Def.family_name.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefGivenName() {
        assertNotNull(Mdl.Def.given_name)
        assertEquals(
            "given_name",
            Mdl.Def.given_name.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefBirthDate() {
        assertNotNull(Mdl.Def.birth_date)
        assertEquals(
            "birth_date",
            Mdl.Def.birth_date.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefIssueDate() {
        assertNotNull(Mdl.Def.issue_date)
        assertEquals(
            "issue_date",
            Mdl.Def.issue_date.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefExpiryDate() {
        assertNotNull(Mdl.Def.expiry_date)
        assertEquals(
            "expiry_date",
            Mdl.Def.expiry_date.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefIssuingCountry() {
        assertNotNull(Mdl.Def.issuing_country)
        assertEquals(
            "issuing_country",
            Mdl.Def.issuing_country.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefIssuingAuthority() {
        assertNotNull(Mdl.Def.issuing_authority)
        assertEquals(
            "issuing_authority",
            Mdl.Def.issuing_authority.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefDocumentNumber() {
        assertNotNull(Mdl.Def.document_number)
        assertEquals(
            "document_number",
            Mdl.Def.document_number.identifier
                .toString(),
        )
    }

    @Test
    fun testMdlDefPortrait() {
        assertNotNull(Mdl.Def.portrait)
        assertEquals(
            "portrait",
            Mdl.Def.portrait.identifier
                .toString(),
        )
    }

    // Mdl.asDef wrapper tests

    @Test
    fun testAsDefWrapper() {
        val def = Mdl.asDef(FamilyNameMdlDef())
        assertTrue(def.isMandatory)
        assertEquals(Mdl.MDL_NAMESPACE.toString(), def.nameSpaceStr)
        assertEquals("family_name", def.identifierStr)
    }

    @Test
    fun testAsDefWrapperDelegation() {
        val original = GivenNameMdlDef()
        val wrapped = Mdl.asDef(original)

        assertEquals(original.nameSpace, wrapped.nameSpace)
        assertEquals(original.identifier, wrapped.identifier)
        assertEquals(original.presence, wrapped.presence)
        assertEquals(original.details, wrapped.details)
    }

    // All definitions have correct namespace

    @Test
    fun testAllDefinitionsHaveCorrectNamespace() {
        val definitions =
            listOf(
                FamilyNameMdlDef(),
                GivenNameMdlDef(),
                BirthDateMdlDef(),
                IssueDateMdlDef(),
                ExpiryDateMdlDef(),
                IssuingCountryMdlDef(),
                IssuingAuthorityMdlDef(),
                DocumentNumberMdlDef(),
                PortraitMdlDef(),
            )

        definitions.forEach { def ->
            assertEquals(
                Mdl.MDL_NAMESPACE,
                def.nameSpace,
                "Definition ${def.identifier} should have MDL namespace",
            )
        }
    }

    // All mandatory definitions

    @Test
    fun testAllDefinitionsAreMandatory() {
        val definitions =
            listOf(
                FamilyNameMdlDef(),
                GivenNameMdlDef(),
                BirthDateMdlDef(),
                IssueDateMdlDef(),
                ExpiryDateMdlDef(),
                IssuingCountryMdlDef(),
                IssuingAuthorityMdlDef(),
                DocumentNumberMdlDef(),
                PortraitMdlDef(),
            )

        definitions.forEach { def ->
            assertEquals(
                Presence.MANDATORY,
                def.presence,
                "Definition ${def.identifier} should be mandatory",
            )
        }
    }
}
