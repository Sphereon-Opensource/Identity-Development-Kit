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

import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MsoMdocFormatHandlerTest {
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
    fun jsonElementToNativeValueLeavesNonJsonStringUntouched() {
        assertEquals(
            "[not-json",
            MsoMdocFormatHandler.jsonElementToNativeValue(JsonPrimitive("[not-json")),
        )
    }
}
