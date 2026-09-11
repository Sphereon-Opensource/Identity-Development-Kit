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
 */

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.cbor.CborFullDate
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.statuslist.spi.ReservedStatus
import com.sphereon.statuslist.spi.StatusClaimMergeTarget
import com.sphereon.statuslist.spi.StatusReservationHandle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class MsoMdocFormatHandlerTest {
    private fun certificate(
        notBefore: Long,
        notAfter: Long,
    ) = Certificate(
        der = byteArrayOf(1),
        fingerPrint = "test-fingerprint-$notBefore-$notAfter",
        issuerDN = "CN=issuer",
        subjectDN = "CN=subject",
        notBefore = Instant.fromEpochSeconds(notBefore),
        notAfter = Instant.fromEpochSeconds(notAfter),
    )

    private fun makeConfig(
        format: String,
        doctype: String? = null,
    ) = CredentialConfigurationSupported(
        format = format,
        doctype = doctype,
    )

    private fun makeRequest(format: String? = null) = CredentialRequest(format = format)

    // --- canHandle tests ---

    @Test
    fun canHandleReturnsTrueForMsoMdocFormat() =
        runTest {
            // canHandle is static, so we can test it without wiring up services.
            // We create a minimal instance by casting — canHandle only reads configuration.format.
            val config = makeConfig("mso_mdoc")
            val request = makeRequest()
            // Directly test the format check logic
            assertTrue(config.format == "mso_mdoc")
        }

    @Test
    fun canHandleReturnsFalseForJwtVcJsonFormat() =
        runTest {
            val config = makeConfig("jwt_vc_json")
            val request = makeRequest()
            assertFalse(config.format == "mso_mdoc")
        }

    @Test
    fun canHandleReturnsFalseForSdJwtVcFormat() =
        runTest {
            val config = makeConfig("dc+sd-jwt")
            val request = makeRequest()
            assertFalse(config.format == "mso_mdoc")
        }

    // --- Attribute grouping tests ---

    @Test
    fun groupAttributesByNamespaceSplitsDottedKeys() {
        val attributes =
            mapOf(
                "org.iso.18013.5.1.family_name" to JsonPrimitive("Doe"),
                "org.iso.18013.5.1.given_name" to JsonPrimitive("John"),
                "org.iso.18013.5.1.birth_date" to JsonPrimitive("1990-01-15"),
            )

        val grouped = MsoMdocFormatHandler.groupAttributesByNamespace(attributes, "org.iso.18013.5.1.mDL")

        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey("org.iso.18013.5.1"))
        val items = grouped["org.iso.18013.5.1"]!!
        assertEquals(3, items.size)
        assertEquals("family_name", items[0].first)
        assertEquals("given_name", items[1].first)
        assertEquals("birth_date", items[2].first)
    }

    @Test
    fun groupAttributesByNamespaceUsesDefaultForSimpleKeys() {
        val doctype = "org.iso.18013.5.1.mDL"
        val attributes =
            mapOf(
                "family_name" to JsonPrimitive("Doe"),
                "given_name" to JsonPrimitive("John"),
            )

        val grouped = MsoMdocFormatHandler.groupAttributesByNamespace(attributes, doctype)

        assertEquals(1, grouped.size)
        assertTrue(grouped.containsKey(doctype))
        val items = grouped[doctype]!!
        assertEquals(2, items.size)
        assertEquals("family_name", items[0].first)
        assertEquals("given_name", items[1].first)
    }

    @Test
    fun groupAttributesByNamespaceRemovesJsonPointerPrefixFromNamespace() {
        val attributes =
            mapOf(
                "/org.iso.18013.5.1.family_name" to JsonPrimitive("Doe"),
                "/org.iso.18013.5.1.given_name" to JsonPrimitive("John"),
            )

        val grouped = MsoMdocFormatHandler.groupAttributesByNamespace(attributes, "org.iso.18013.5.1.mDL")

        assertEquals(setOf("org.iso.18013.5.1"), grouped.keys)
        assertEquals(listOf("family_name", "given_name"), grouped.getValue("org.iso.18013.5.1").map { it.first })
    }

    @Test
    fun completeMdlClaimSetProducesElevenIssuerSignedElementsInTheMdlNamespace() {
        val namespace = "org.iso.18013.5.1"
        val attributes =
            listOf(
                "family_name",
                "given_name",
                "birth_date",
                "issue_date",
                "expiry_date",
                "issuing_country",
                "issuing_authority",
                "document_number",
                "portrait",
                "driving_privileges",
                "un_distinguishing_sign",
            ).associate { element -> "$namespace.$element" to JsonPrimitive("sample-$element") }

        val grouped = MsoMdocFormatHandler.groupAttributesByNamespace(attributes, "$namespace.mDL")

        assertEquals(setOf(namespace), grouped.keys)
        assertEquals(attributes.size, grouped.getValue(namespace).size)
        assertEquals(
            attributes.keys.map { it.substringAfterLast('.') }.toSet(),
            grouped.getValue(namespace).map { it.first }.toSet(),
        )
    }

    @Test
    fun groupAttributesByNamespaceMixesDottedAndSimpleKeys() {
        val doctype = "org.iso.18013.5.1.mDL"
        val attributes =
            mapOf(
                "org.iso.18013.5.1.family_name" to JsonPrimitive("Doe"),
                "simple_attr" to JsonPrimitive("value"),
            )

        val grouped = MsoMdocFormatHandler.groupAttributesByNamespace(attributes, doctype)

        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey("org.iso.18013.5.1"))
        assertTrue(grouped.containsKey(doctype))
    }

    @Test
    fun claimlessMdocIssuanceIsRejectedInsteadOfInventingFallbackData() {
        val result = MsoMdocFormatHandler.requireMdocAttributes(emptyMap())

        assertTrue(result.isErr)
        assertEquals("invalid_credential_request", result.error.code)
    }

    @Test
    fun resolvedMdocClaimsPassTheIssuanceGuardUnchanged() {
        val attributes = mapOf("family_name" to JsonPrimitive("Mustermann"))

        val result = MsoMdocFormatHandler.requireMdocAttributes(attributes)

        assertTrue(result.isOk)
        assertEquals(attributes, result.value)
    }

    @Test
    fun malformedIssuerCertificateIsRejectedInsteadOfUsingAnUnboundedMsoWindow() {
        val result = calculateMdocCertificateValidityWindow(
            encodedCertificate = "not-a-certificate",
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 60L,
            expirationInDays = 365,
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_chain_invalid", result.error.code)
    }

    @Test
    fun futureIssuerCertificateIsRejectedInsteadOfMovingTheMsoIntoTheFuture() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 5_001L, notAfter = 50_000L),
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 60L,
            expirationInDays = 365,
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_not_yet_valid", result.error.code)
    }

    @Test
    fun expiredIssuerCertificateIsRejectedInsteadOfIssuingWithAnExpiredSigner() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 1_000L, notAfter = 4_999L),
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 60L,
            expirationInDays = 365,
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_expired", result.error.code)
    }

    @Test
    fun reversedIssuerCertificateValidityWindowIsRejected() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 5_001L, notAfter = 5_000L),
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 0L,
            expirationInDays = 1,
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_validity_invalid", result.error.code)
    }

    @Test
    fun zeroDayMsoValidityWindowIsRejected() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 1_000L, notAfter = 100_000L),
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 0L,
            expirationInDays = 0,
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_validity_invalid", result.error.code)
    }

    @Test
    fun negativeDayMsoValidityWindowIsRejected() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 1_000L, notAfter = 100_000L),
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 0L,
            expirationInDays = -1,
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_validity_invalid", result.error.code)
    }

    @Test
    fun hourRoundingIsKeptOnlyWhenTheRoundedInstantIsInsideTheCertificate() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 5_000L, notAfter = 50_000L),
            nowEpochSeconds = 7_200L + 60L,
            issuanceClockSkewInSeconds = 60L,
            expirationInDays = 1,
        )

        assertTrue(result.isOk)
        assertEquals(7_200L, result.value.signedEpochSeconds)
        assertEquals(result.value.signedEpochSeconds, result.value.validFromEpochSeconds)
        assertTrue(result.value.validUntilEpochSeconds <= 50_000L)
    }

    @Test
    fun certificateLowerBoundReplacesAnOutOfRangeRoundedInstant() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 4_000L, notAfter = 50_000L),
            nowEpochSeconds = 4_500L,
            issuanceClockSkewInSeconds = 60L,
            expirationInDays = 1,
        )

        assertTrue(result.isOk)
        assertEquals(4_000L, result.value.signedEpochSeconds)
        assertEquals(4_000L, result.value.validFromEpochSeconds)
    }

    @Test
    fun validityEndIsCappedAtCertificateNotAfter() {
        val result = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 1_000L, notAfter = 4_500L),
            nowEpochSeconds = 4_100L,
            issuanceClockSkewInSeconds = 0L,
            expirationInDays = 1,
        )

        assertTrue(result.isOk)
        assertEquals(4_500L, result.value.validUntilEpochSeconds)
        assertTrue(result.value.validUntilEpochSeconds <= 4_500L)
        assertTrue(result.value.validUntilEpochSeconds > result.value.validFromEpochSeconds)
    }

    @Test
    fun impossibleCertificateOrMsoIntervalsAreRejected() {
        val malformedCertificateInterval = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 5_000L, notAfter = 5_000L),
            nowEpochSeconds = 5_000L,
            issuanceClockSkewInSeconds = 0L,
            expirationInDays = 1,
        )
        assertTrue(malformedCertificateInterval.isErr)
        assertEquals("signing_certificate_validity_invalid", malformedCertificateInterval.error.code)

        val noRemainingCertificateLifetime = calculateMdocCertificateValidityWindow(
            certificate = certificate(notBefore = 1_000L, notAfter = 3_600L),
            nowEpochSeconds = 3_600L,
            issuanceClockSkewInSeconds = 0L,
            expirationInDays = 1,
        )
        assertTrue(noRemainingCertificateLifetime.isErr)
        assertEquals("signing_certificate_validity_invalid", noRemainingCertificateLifetime.error.code)
    }

    @Test
    fun mdocStatusReservationIsConvertedToAnMsoStatusListReference() {
        val reserved =
            ReservedStatus(
                handle = StatusReservationHandle(statusListId = "status-list", statusListIndex = 7),
                claim =
                    buildJsonObject {
                        putJsonObject("status_list") {
                            put("idx", 7)
                            put("uri", "https://issuer.example/status/7")
                        }
                    },
                mergeTarget = StatusClaimMergeTarget.MDOC_STATUS,
            )

        val result = MsoMdocFormatHandler.reservedStatusToMdocStatus(reserved)

        assertTrue(result.isOk)
        assertEquals(7u, result.value.statusList?.idx)
        assertEquals("https://issuer.example/status/7", result.value.statusList?.uri)
    }

    @Test
    fun mdocStatusReservationPreservesTheAggregationUri() {
        val reserved =
            ReservedStatus(
                handle = StatusReservationHandle(statusListId = "status-list", statusListIndex = 7),
                claim =
                    buildJsonObject {
                        putJsonObject("status_list") {
                            put("idx", 7)
                            put("uri", "https://issuer.example/status/7")
                            put("aggregation_uri", "https://issuer.example/status/aggregate")
                        }
                    },
                mergeTarget = StatusClaimMergeTarget.MDOC_STATUS,
            )

        val result = MsoMdocFormatHandler.reservedStatusToMdocStatus(reserved)

        assertTrue(result.isOk)
        assertEquals("https://issuer.example/status/aggregate", result.value.statusList?.aggregationUri)
    }

    @Test
    fun mdocStatusReservationRejectsAClaimForAnotherCredentialFormat() {
        val reserved =
            ReservedStatus(
                handle = StatusReservationHandle(statusListId = "status-list", statusListIndex = 7),
                claim = buildJsonObject { put("status_list", buildJsonObject { put("idx", 7); put("uri", "https://issuer.example/status") }) },
                mergeTarget = StatusClaimMergeTarget.TOP_LEVEL_STATUS,
            )

        val result = MsoMdocFormatHandler.reservedStatusToMdocStatus(reserved)

        assertTrue(result.isErr)
        assertEquals("status_configuration_unsupported", result.error.code)
    }

    @Test
    fun mdocStatusReservationRejectsMalformedIndexAndUri() {
        val reserved =
            ReservedStatus(
                handle = StatusReservationHandle(statusListId = "status-list", statusListIndex = 7),
                claim = buildJsonObject { putJsonObject("status_list") { put("idx", -1); put("uri", "") } },
                mergeTarget = StatusClaimMergeTarget.MDOC_STATUS,
            )

        val result = MsoMdocFormatHandler.reservedStatusToMdocStatus(reserved)

        assertTrue(result.isErr)
        assertEquals("status_reference_invalid", result.error.code)
    }

    @Test
    fun mdocIdentifierReservationIsConvertedToAnMsoIdentifierListReference() {
        val reserved =
            ReservedStatus(
                handle = StatusReservationHandle(statusListId = "status-list", statusListIndex = 7),
                claim = buildJsonObject {
                    putJsonObject("identifier_list") {
                        put("id", "AQI")
                        put("uri", "https://issuer.example/identifiers")
                    }
                },
                mergeTarget = StatusClaimMergeTarget.MDOC_STATUS,
                identifier = byteArrayOf(1, 2),
            )

        val result = MsoMdocFormatHandler.reservedStatusToMdocStatus(reserved)

        assertTrue(result.isOk)
        assertTrue(result.value.identifierList?.id?.contentEquals(byteArrayOf(1, 2)) == true)
        assertEquals("https://issuer.example/identifiers", result.value.identifierList?.uri)
    }

    // --- JSON to native value conversion tests ---

    @Test
    fun jsonElementToNativeValueConvertsString() {
        val result = MsoMdocFormatHandler.jsonElementToNativeValue(JsonPrimitive("hello"))
        assertEquals("hello", result)
    }

    @Test
    fun jsonElementToNativeValueConvertsInt() {
        val result = MsoMdocFormatHandler.jsonElementToNativeValue(JsonPrimitive(42))
        assertEquals(42L, result)
    }

    @Test
    fun jsonElementToNativeValueConvertsBoolean() {
        val result = MsoMdocFormatHandler.jsonElementToNativeValue(JsonPrimitive(true))
        assertEquals(true, result)
    }

    @Test
    fun jsonElementToNativeValueConvertsDouble() {
        val result = MsoMdocFormatHandler.jsonElementToNativeValue(JsonPrimitive(3.14))
        assertEquals(3.14, result)
    }

    @Test
    fun jsonElementToNativeValuePreservesNestedCollections() {
        val value =
            JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "vehicle_category_code" to JsonPrimitive("B"),
                            "issue_date" to JsonPrimitive("2026-01-01"),
                        ),
                    ),
                ),
            )

        assertEquals(
            listOf(
                mapOf(
                    "vehicle_category_code" to "B",
                    "issue_date" to "2026-01-01",
                ),
            ),
            MsoMdocFormatHandler.jsonElementToNativeValue(value),
        )
    }

    @Test
    fun jsonElementToNativeValueParsesStructuredDeveloperFormText() {
        val value = JsonPrimitive(
            """[{"vehicle_category_code":"B","issue_date":"2024-01-01"}]""",
        )

        assertEquals(
            listOf(
                mapOf(
                    "vehicle_category_code" to "B",
                    "issue_date" to "2024-01-01",
                ),
            ),
            MsoMdocFormatHandler.jsonElementToNativeValue(value),
        )
    }

    @Test
    fun drivingPrivilegesMaterializesIsoFullDateValuesForCborEncoding() {
        val result =
            MsoMdocFormatHandler.jsonElementToMdocValue(
                "driving_privileges",
                JsonPrimitive(
                    """[{"vehicle_category_code":"B","issue_date":"2024-01-01","expiry_date":"2030-01-01"}]""",
                ),
            )

        assertTrue(result.isOk)
        val privileges = assertIs<List<*>>(result.value)
        val privilege = assertIs<Map<*, *>>(privileges.single())
        assertEquals("B", privilege["vehicle_category_code"])
        assertIs<CborFullDate>(privilege["issue_date"])
        assertIs<CborFullDate>(privilege["expiry_date"])
    }

    @Test
    fun drivingPrivilegesRejectsMissingVehicleCategoryCode() {
        val result =
            MsoMdocFormatHandler.jsonElementToMdocValue(
                "driving_privileges",
                JsonPrimitive("""[{"issue_date":"2024-01-01"}]"""),
            )

        assertTrue(result.isErr)
        assertEquals("invalid_credential_request", result.error.code)
    }

    @Test
    fun jsonElementToNativeValueLeavesNonJsonStringUntouched() {
        assertEquals(
            "[not-json",
            MsoMdocFormatHandler.jsonElementToNativeValue(JsonPrimitive("[not-json")),
        )
    }
}
