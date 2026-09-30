package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.model.AttributeDataType
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AttributeFormatBinding
import com.sphereon.catalog.eu.model.AttributeInformation
import com.sphereon.catalog.eu.model.AttributeNamespace
import com.sphereon.catalog.eu.model.AuthenticSource
import com.sphereon.catalog.eu.model.CatalogueDistributionPoint
import com.sphereon.catalog.eu.model.CatalogueInformation
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOperator
import com.sphereon.catalog.eu.model.ElectronicAddress
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.MultiLangString
import com.sphereon.catalog.eu.model.NamespaceEntry
import com.sphereon.catalog.eu.model.PostalAddress
import com.sphereon.catalog.eu.model.ReferenceBody
import com.sphereon.catalog.eu.model.SemanticDescription
import com.sphereon.catalog.eu.model.VersionedAttribute
import com.sphereon.catalog.eu.validation.CatalogueConformanceValidator
import com.sphereon.catalog.eu.validation.CatalogueFindingCodes
import com.sphereon.catalog.eu.validation.CatalogueProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AuthoredCatalogueConformanceTest {
    private val validator = CatalogueConformanceValidator()

    private fun names(value: String) = InternationalNames(listOf(MultiLangString("en", value)))

    private fun info(identifier: String) =
        CatalogueInformation(
            version = 1,
            sequenceNumber = 1,
            identifier = identifier,
            name = names("Tenant attributes"),
            operator = CatalogueOperator(names("Tenant"), "NTRNL-1", listOf(PostalAddress("en", "Street 1", "Amsterdam", null, null, "NL")), electronicAddress = ElectronicAddress(listOf("mailto:ops@tenant.example"))),
            informationUri = "https://tenant.example/info",
            territory = "NL",
            legalNotice = "https://tenant.example/legal",
            historicalInformationPeriod = 24,
            issueDateTime = Instant.parse("2026-09-29T00:00:00Z"),
            distributionPoint = CatalogueDistributionPoint(downloadUrl = "https://tenant.example/catalogs/attrs.xml"),
        )

    private fun authoredCatalogue(identifier: String) =
        CatalogueOfAttributes(
            info = info(identifier),
            namespaces =
                listOf(
                    AttributeNamespace(
                        "org.tenant.employee.1",
                        listOf(
                            NamespaceEntry(
                                "employee_id",
                                null,
                                EntryReference(
                                    "attributes/org.tenant.employee.1/employee_id.xml",
                                    listOf(EuCatalogueConstants.EXC_C14N_TRANSFORM),
                                    EuCatalogueConstants.DIGEST_SHA512_XMLENC,
                                    ByteArray(64),
                                ),
                            ),
                        ),
                    ),
                ),
        )

    private fun authoredAttribute() =
        AttributeEntry(
            attributeIdentifier = "employee_id",
            referenceBody = ReferenceBody(names("Tenant HR"), "https://tenant.example/hr"),
            versions =
                listOf(
                    VersionedAttribute(
                        version = "1",
                        information =
                            AttributeInformation(
                                name = names("Employee id"),
                                semanticDescription = SemanticDescription(names("Identifier of an employee")),
                                dataType =
                                    AttributeDataType(
                                        dataType = "https://tenant.example/types/string",
                                        hasSpecification = true,
                                        formatBindings = listOf(AttributeFormatBinding("application/dc+sd-jwt", null, "employee_id")),
                                    ),
                                authenticSources =
                                    listOf(AuthenticSource(identifier = "hr-system", verificationAccessDescriptionUri = "https://tenant.example/hr/verify")),
                            ),
                    ),
                ),
        )

    @Test
    fun unsignedAuthoredCatalogueConformsBeforePublish() {
        val profile = CatalogueProfile.custom()
        assertEquals(emptyList(), validator.validateCoa(authoredCatalogue("https://tenant.example/catalogues/attributes"), profile))
        assertEquals(emptyList(), validator.validateAttributeEntry(authoredAttribute(), profile = profile))
    }

    @Test
    fun signatureIsRequiredOncePublishing() {
        val findings = validator.validateCoa(authoredCatalogue("https://tenant.example/catalogues/attributes"), CatalogueProfile.custom(requireSignature = true))
        assertEquals(listOf(CatalogueFindingCodes.SIGNATURE_MISSING), findings.map { it.code })
    }

    @Test
    fun authoredCatalogueCannotUseTheEuIdentifiers() {
        val findings = validator.validateCoa(authoredCatalogue(EuCatalogueConstants.COA_IDENTIFIER), CatalogueProfile.custom())
        assertEquals(listOf(CatalogueFindingCodes.IDENTIFIER_RESERVED), findings.map { it.code })
        val cos = validator.validateCoa(authoredCatalogue(EuCatalogueConstants.COS_IDENTIFIER), CatalogueProfile.custom())
        assertEquals(listOf(CatalogueFindingCodes.IDENTIFIER_RESERVED), cos.map { it.code })
    }

    @Test
    fun euProfileStillRejectsACustomCatalogue() {
        val codes = validator.validateCoa(authoredCatalogue("https://tenant.example/catalogues/attributes")).map { it.code }.toSet()
        assertEquals(
            setOf(
                CatalogueFindingCodes.IDENTIFIER_MISMATCH,
                CatalogueFindingCodes.TERRITORY_INVALID,
                CatalogueFindingCodes.HISTORY_PERIOD_INVALID,
                CatalogueFindingCodes.SIGNATURE_MISSING,
            ),
            codes,
        )
        assertEquals(setOf(CatalogueFindingCodes.REGISTRATION_IDENTIFIER_MISSING), validator.validateAttributeEntry(authoredAttribute()).map { it.code }.toSet())
    }

    @Test
    fun outOfRangeHistoryPeriodIsRejectedForCustomCatalogues() {
        val catalogue = authoredCatalogue("https://tenant.example/catalogues/attributes")
        val bad = catalogue.copy(info = catalogue.info.copy(historicalInformationPeriod = 70000))
        assertEquals(listOf(CatalogueFindingCodes.HISTORY_PERIOD_INVALID), validator.validateCoa(bad, CatalogueProfile.custom()).map { it.code })
    }
}
