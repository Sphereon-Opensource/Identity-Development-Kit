package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.testutil.readTestResource
import com.sphereon.catalog.eu.validation.CatalogueConformanceValidator
import com.sphereon.catalog.eu.validation.CatalogueFindingCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogueConformanceValidatorTest {
    private val parser = DefaultEuCatalogueXmlParser()
    private val validator = CatalogueConformanceValidator()

    private fun bytes(path: String): ByteArray = readTestResource("eu-catalogues/$path").encodeToByteArray()

    private fun coa(path: String) = parser.parseCoa(bytes(path)).also { assertTrue(it.isOk, "$path: ${it.takeIf { r -> r.isErr }?.error}") }.value.value

    private fun cos(path: String) = parser.parseCos(bytes(path)).also { assertTrue(it.isOk) }.value.value

    private fun attribute(path: String) = parser.parseAttributeEntry(bytes(path)).also { assertTrue(it.isOk, "$path: ${it.takeIf { r -> r.isErr }?.error}") }.value.value

    private fun scheme(path: String) = parser.parseSchemeEntry(bytes(path)).also { assertTrue(it.isOk, "$path: ${it.takeIf { r -> r.isErr }?.error}") }.value.value

    private fun List<CatalogueFinding>.codes(): Set<String> = map { it.code }.toSet()

    private fun List<CatalogueFinding>.errors(): List<CatalogueFinding> = filter { it.severity == FindingSeverity.ERROR }

    @Test
    fun liveCatalogueHeadersConform() {
        assertEquals(emptyList(), validator.validateCoa(coa("live/coa.xml")))
        assertEquals(emptyList(), validator.validateCos(cos("live/cos.xml")))
    }

    @Test
    fun syntheticPopulatedCatalogueIndexesConform() {
        assertEquals(emptyList(), validator.validateCoa(coa("synthetic/coa-populated.xml")))
        assertEquals(emptyList(), validator.validateCos(cos("synthetic/cos-populated.xml")))
    }

    @Test
    fun syntheticEntryFilesConform() {
        val family = attribute("synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml")
        val birth = attribute("synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml")
        val scheme = scheme("synthetic/schemes/eu-pid.xml")
        assertEquals(emptyList(), validator.validateAttributeEntry(family))
        assertEquals(emptyList(), validator.validateAttributeEntry(birth))
        assertEquals(emptyList(), validator.validateSchemeEntry(scheme))
    }

    @Test
    fun entryFilesMatchTheirIndexEntries() {
        val index = coa("synthetic/coa-populated.xml").namespaces.single().entries
        val family = attribute("synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml")
        assertEquals(emptyList(), validator.validateAttributeEntry(family, index[0]))
        val mismatch = validator.validateAttributeEntry(family, index[1])
        assertEquals(setOf(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH), mismatch.codes())

        val schemeRef = cos("synthetic/cos-populated.xml").schemes.single()
        assertEquals(emptyList(), validator.validateSchemeEntry(scheme("synthetic/schemes/eu-pid.xml"), schemeRef))
        assertEquals(
            setOf(CatalogueFindingCodes.INDEX_ENTRY_MISMATCH),
            validator.validateSchemeEntry(scheme("synthetic/schemes/eu-pid.xml"), schemeRef.copy(name = "other")).codes(),
        )
    }

    @Test
    fun negativeCoaReportsSignatureIdentifierHistoryAndReferenceDefects() {
        val findings = validator.validateCoa(coa("negative/coa-defects.xml"))
        assertEquals(
            setOf(
                CatalogueFindingCodes.SIGNATURE_MISSING,
                CatalogueFindingCodes.IDENTIFIER_MISMATCH,
                CatalogueFindingCodes.HISTORY_PERIOD_INVALID,
                CatalogueFindingCodes.REFERENCE_TRANSFORM_INVALID,
                CatalogueFindingCodes.REFERENCE_DIGEST_LENGTH_INVALID,
            ),
            findings.codes(),
        )
        assertTrue(CatalogueConformanceValidator.hasErrors(findings))
    }

    @Test
    fun negativeAttributeReportsSpecMandatoryChecksTheXsdLeavesOpen() {
        val findings = validator.validateAttributeEntry(attribute("negative/attribute-defects.xml"))
        assertEquals(
            setOf(
                CatalogueFindingCodes.REGISTRATION_IDENTIFIER_MISSING,
                CatalogueFindingCodes.SEMANTIC_DESCRIPTION_ENGLISH_MISSING,
                CatalogueFindingCodes.NAME_LANG_MISSING,
                CatalogueFindingCodes.VERIFICATION_ACCESS_EMPTY,
                CatalogueFindingCodes.SOURCE_ATTRIBUTE_IDENTIFIER_INVALID,
                CatalogueFindingCodes.ANNEX_VI_TYPE_INVALID,
            ),
            findings.codes(),
        )
    }

    @Test
    fun negativeSchemeReportsRegistrationTrustModelAndChoiceDefects() {
        val findings = validator.validateSchemeEntry(scheme("negative/scheme-defects.xml"))
        assertEquals(
            setOf(
                CatalogueFindingCodes.REGISTRATION_IDENTIFIER_MISSING,
                CatalogueFindingCodes.TRUST_MODEL_TYPES_MISSING,
                CatalogueFindingCodes.ATTRIBUTE_REFERENCE_CHOICE_INVALID,
            ),
            findings.codes(),
        )
    }

    @Test
    fun unknownCriticalAttributeExtensionVoidsTheEntry() {
        val findings = validator.validateAttributeEntry(attribute("negative/attribute-unknown-critical-extension.xml"))
        assertTrue(CatalogueConformanceValidator.hasUnknownCriticalExtension(findings))
        val finding = findings.single { it.code == CatalogueFindingCodes.UNKNOWN_CRITICAL_EXTENSION }
        assertEquals(FindingSeverity.ERROR, finding.severity)
        assertTrue(finding.message.contains("{urn:example:future}futureRule"))
    }

    @Test
    fun unknownTrustModelAndCriticalSchemeExtensionAreReported() {
        val findings = validator.validateSchemeEntry(scheme("negative/scheme-unknown-critical-extension.xml"))
        assertEquals(setOf(CatalogueFindingCodes.TRUST_MODEL_TYPE_UNKNOWN, CatalogueFindingCodes.UNKNOWN_CRITICAL_EXTENSION), findings.codes())
    }

    @Test
    fun nonCriticalUnknownExtensionIsIgnored() {
        val findings = validator.validateAttributeEntry(attribute("synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"))
        assertFalse(CatalogueConformanceValidator.hasUnknownCriticalExtension(findings))
        assertTrue(findings.errors().isEmpty())
    }

    @Test
    fun unpublishedStatusUriIsAWarningNotAnError() {
        val family = attribute("synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml")
        val changed = family.copy(versions = family.versions.map { it.copy(status = it.status?.copy(statusUri = "urn:example:custom")) })
        val findings = validator.validateAttributeEntry(changed)
        assertEquals(setOf(CatalogueFindingCodes.STATUS_UNKNOWN), findings.codes())
        assertTrue(findings.all { it.severity == FindingSeverity.WARNING })
        assertFalse(CatalogueConformanceValidator.hasErrors(findings))
    }

    @Test
    fun duplicateTrustModelTypeIsRejected() {
        val entry = scheme("synthetic/schemes/eu-pid.xml")
        val version = entry.versions.single()
        val type = version.eaaTypes.single()
        val doubled = entry.copy(versions = listOf(version.copy(eaaTypes = listOf(type.copy(trustModelTypes = type.trustModelTypes + type.trustModelTypes.first())))))
        assertEquals(setOf(CatalogueFindingCodes.TRUST_MODEL_TYPE_DUPLICATE), validator.validateSchemeEntry(doubled).codes())
    }

    @Test
    fun duplicateNamespaceAndAttributeIdentifiersAreRejected() {
        val doc = coa("synthetic/coa-populated.xml")
        val ns = doc.namespaces.single()
        val doubled = doc.copy(namespaces = listOf(ns.copy(entries = ns.entries + ns.entries.first()), ns))
        val findings = validator.validateCoa(doubled)
        assertEquals(setOf(CatalogueFindingCodes.DUPLICATE_IDENTIFIER), findings.codes())
        assertEquals(2, findings.size)
    }
}
