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

package com.sphereon.openid.oid4vci.issuer.command

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticOfferModelsTest {
    @Test
    fun offerUriLifecycleHasExactlyTwoEntries() {
        assertEquals(2, OfferUriLifecycle.entries.size)
        val names = OfferUriLifecycle.entries.map { it.name }
        assertTrue(names.contains("SINGLE_USE"))
        assertTrue(names.contains("REUSABLE_FRESH_PER_FETCH"))
    }

    @Test
    fun offerRateLimitRoundTripsJson() {
        val original = OfferRateLimit(maxPerWindow = 100, windowSeconds = 3600)
        val json = Json.encodeToString(OfferRateLimit.serializer(), original)
        val decoded = Json.decodeFromString(OfferRateLimit.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun offerUriLifecycleSingleUseRoundTripsJson() {
        val original = OfferUriLifecycle.SINGLE_USE
        val json = Json.encodeToString(OfferUriLifecycle.serializer(), original)
        val decoded = Json.decodeFromString(OfferUriLifecycle.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun offerUriLifecycleReusableFreshPerFetchRoundTripsJson() {
        val original = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH
        val json = Json.encodeToString(OfferUriLifecycle.serializer(), original)
        val decoded = Json.decodeFromString(OfferUriLifecycle.serializer(), json)
        assertEquals(original, decoded)
    }
}
