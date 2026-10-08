package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.model.ListOfCatalogues
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.testutil.readTestResource
import com.sphereon.catalog.eu.validation.CatalogueFindingCodes
import com.sphereon.catalog.eu.validation.LocProfileValidator
import com.sphereon.trust.etsi.lote.model.TrustedEntity
import com.sphereon.trust.etsi.lote.model.TrustedEntityInformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.sphereon.trust.etsi.lote.model.MultiLangString as LoteMultiLangString

class LocProfileValidatorTest {
    private val parser = DefaultEuCatalogueXmlParser()
    private val validator = LocProfileValidator()

    private fun loc(path: String): ListOfCatalogues {
        val result = parser.parseLoc(readTestResource("eu-catalogues/$path").encodeToByteArray())
        assertTrue(result.isOk, "$path: ${result.takeIf { it.isErr }?.error}")
        return result.value.value
    }

    @Test
    fun liveLocConformsToTheProfile() {
        assertEquals(emptyList(), validator.validate(loc("live/loc.xml"), now = Instant.parse("2026-09-29T00:00:00Z")))
    }

    @Test
    fun passedNextUpdateIsReported() {
        val findings = validator.validate(loc("live/loc.xml"), now = Instant.parse("2027-02-10T00:00:00Z"))
        assertEquals(listOf(CatalogueFindingCodes.LOC_NEXT_UPDATE_PASSED), findings.map { it.code })
        assertEquals(FindingSeverity.ERROR, findings.single().severity)
    }

    @Test
    fun nextUpdateIsNotCheckedWithoutAClock() {
        assertEquals(emptyList(), validator.validate(loc("live/loc.xml")))
    }

    @Test
    fun negativeLocReportsEveryProfileDeviation() {
        val codes = validator.validate(loc("negative/loc-defects.xml")).map { it.code }.toSet()
        assertEquals(
            setOf(
                CatalogueFindingCodes.LOC_TAG_INVALID,
                CatalogueFindingCodes.LOC_TYPE_INVALID,
                CatalogueFindingCodes.LOC_TERRITORY_INVALID,
                CatalogueFindingCodes.LOC_STATUS_APPROACH_INVALID,
                CatalogueFindingCodes.LOC_COMMUNITY_RULES_INVALID,
                CatalogueFindingCodes.LOC_HISTORICAL_PERIOD_PRESENT,
                CatalogueFindingCodes.LOC_POINTERS_INVALID,
                CatalogueFindingCodes.LOC_POINTER_SIGNERS_MISSING,
                CatalogueFindingCodes.LOC_POINTER_LOCATION_INVALID,
                CatalogueFindingCodes.LOC_NEXT_UPDATE_TOO_FAR,
            ),
            codes,
        )
    }

    @Test
    fun trustedEntitiesListMustBeEmpty() {
        val live = loc("live/loc.xml")
        val entity = TrustedEntity(TrustedEntityInformation(name = listOf(LoteMultiLangString("en", "Example"))))
        val withEntities = live.copy(lote = live.lote.copy(trustedEntitiesList = listOf(entity)))
        assertEquals(listOf(CatalogueFindingCodes.LOC_TRUSTED_ENTITIES_PRESENT), validator.validate(withEntities).map { it.code })
    }

    @Test
    fun nextUpdateExactlySixMonthsAfterIssueIsAccepted() {
        val live = loc("live/loc.xml")
        val issue = live.lote.listAndSchemeInformation.listIssueDateTime
        assertEquals("2026-08-18T00:00:00Z", issue.toString())
        val atLimit = live.copy(nextUpdate = Instant.parse("2027-02-18T00:00:00Z"))
        assertEquals(emptyList(), validator.validate(atLimit))
        val beyond = live.copy(nextUpdate = Instant.parse("2027-02-18T00:00:01Z"))
        assertEquals(listOf(CatalogueFindingCodes.LOC_NEXT_UPDATE_TOO_FAR), validator.validate(beyond).map { it.code })
    }

    private fun locWithSchemeExtension(critical: Boolean): ListOfCatalogues {
        val xml =
            readTestResource("eu-catalogues/live/loc.xml").replace(
                "    </ListAndSchemeInformation>",
                "<SchemeExtensions><Extension Critical=\"$critical\"><x:Unknown xmlns:x=\"urn:example:ext\">v</x:Unknown></Extension></SchemeExtensions></ListAndSchemeInformation>",
            )
        val result = parser.parseLoc(xml.encodeToByteArray())
        assertTrue(result.isOk, "${result.takeIf { it.isErr }?.error}")
        return result.value.value
    }

    @Test
    fun criticalSchemeExtensionIsRejected() {
        val findings = validator.validate(locWithSchemeExtension(critical = true))
        assertEquals(listOf(CatalogueFindingCodes.LOC_CRITICAL_EXTENSION_PRESENT), findings.map { it.code })
        assertEquals(FindingSeverity.ERROR, findings.single().severity)
    }

    @Test
    fun nonCriticalSchemeExtensionIsTolerated() {
        assertEquals(emptyList(), validator.validate(locWithSchemeExtension(critical = false)))
    }
}
