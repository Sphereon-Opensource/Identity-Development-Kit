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

package com.sphereon.openid.oid4vci.rest

import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit
import com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateCredentialOfferInputStaticOfferFieldsTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun uriLifecycleDefaultsToSingleUse() {
        val input =
            CreateCredentialOfferInput(
                credentialConfigurationIds = listOf("PID"),
            )
        assertEquals(OfferUriLifecycle.SINGLE_USE, input.uriLifecycle)
    }

    @Test
    fun initialConnectorFieldsDefaultsToEmptyMap() {
        val input =
            CreateCredentialOfferInput(
                credentialConfigurationIds = listOf("PID"),
            )
        assertTrue(input.initialConnectorFields.isEmpty())
    }

    @Test
    fun rateLimitDefaultsToNull() {
        val input =
            CreateCredentialOfferInput(
                credentialConfigurationIds = listOf("PID"),
            )
        assertNull(input.rateLimit)
    }

    @Test
    fun uriLifecycleRoundTripsJsonWithSnakeCaseWireKey() {
        val input =
            CreateCredentialOfferInput(
                credentialConfigurationIds = listOf("PID"),
                uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
            )
        val serialized = json.encodeToString(CreateCredentialOfferInput.serializer(), input)
        assertTrue(serialized.contains("\"uri_lifecycle\""), "expected uri_lifecycle key in JSON: $serialized")
        val decoded = json.decodeFromString(CreateCredentialOfferInput.serializer(), serialized)
        assertEquals(OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH, decoded.uriLifecycle)
    }

    @Test
    fun rateLimitRoundTripsJsonWithSnakeCaseWireKey() {
        val rateLimit = OfferRateLimit(maxPerWindow = 20, windowSeconds = 60)
        val input =
            CreateCredentialOfferInput(
                credentialConfigurationIds = listOf("PID"),
                uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                rateLimit = rateLimit,
            )
        val serialized = json.encodeToString(CreateCredentialOfferInput.serializer(), input)
        assertTrue(serialized.contains("\"rate_limit\""), "expected rate_limit key in JSON: $serialized")
        val decoded = json.decodeFromString(CreateCredentialOfferInput.serializer(), serialized)
        assertEquals(rateLimit, decoded.rateLimit)
    }

    @Test
    fun initialConnectorFieldsRoundTripsJsonWithSnakeCaseWireKey() {
        val inputJson =
            """{"credential_configuration_ids":["PID"],"initial_connector_fields":{"email":"alice@example.com"}}"""
        val input = json.decodeFromString(CreateCredentialOfferInput.serializer(), inputJson)
        assertEquals(JsonPrimitive("alice@example.com"), input.initialConnectorFields["email"])

        val encoded = json.encodeToString(CreateCredentialOfferInput.serializer(), input)
        assertTrue(encoded.contains("\"initial_connector_fields\""), "expected initial_connector_fields key in JSON: $encoded")
    }

    @Test
    fun minimalJsonDecodesWithAllDefaults() {
        val input =
            json.decodeFromString(
                CreateCredentialOfferInput.serializer(),
                """{"credential_configuration_ids":["PID"]}""",
            )
        assertEquals(OfferUriLifecycle.SINGLE_USE, input.uriLifecycle)
        assertTrue(input.initialConnectorFields.isEmpty())
        assertNull(input.rateLimit)
    }
}
