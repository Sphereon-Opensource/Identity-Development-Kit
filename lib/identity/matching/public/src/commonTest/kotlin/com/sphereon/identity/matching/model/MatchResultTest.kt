/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.model

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MatchResultTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleMatch() = IdentityMatch(
        id = "match-1",
        identifierHash = "hash-abc",
        identifierType = IdentifierType.DID,
        internalIdentityId = "identity-1",
        tenantId = "tenant-1",
        createdAt = Clock.System.now()
    )

    @Test
    fun foundContainsMatch() {
        val match = sampleMatch()
        val result: MatchResult = MatchResult.Found(match)
        assertIs<MatchResult.Found>(result)
        assertEquals(match.id, result.match.id)
    }

    @Test
    fun notFoundIsDistinct() {
        val result: MatchResult = MatchResult.NotFound
        assertIs<MatchResult.NotFound>(result)
    }

    @Test
    fun foundSerializationRoundTrip() {
        val match = sampleMatch()
        val result: MatchResult = MatchResult.Found(match)
        val serialized = json.encodeToString(MatchResult.serializer(), result)
        val deserialized = json.decodeFromString(MatchResult.serializer(), serialized)
        assertIs<MatchResult.Found>(deserialized)
        assertEquals(match.id, deserialized.match.id)
        assertEquals(match.identifierHash, deserialized.match.identifierHash)
    }

    @Test
    fun notFoundSerializationRoundTrip() {
        val result: MatchResult = MatchResult.NotFound
        val serialized = json.encodeToString(MatchResult.serializer(), result)
        val deserialized = json.decodeFromString(MatchResult.serializer(), serialized)
        assertIs<MatchResult.NotFound>(deserialized)
    }
}
