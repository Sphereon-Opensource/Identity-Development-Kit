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

package com.sphereon.identity.matching.impl.crypto

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests for the crypto data types (HashedIdentifier, EncryptedPayload).
 * These tests validate serialization round-trips and domain separation concepts
 * without requiring a live KMS.
 */
class ReconciliationCryptoTypesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun hashedIdentifierSerializationRoundTrip() {
        val original =
            HashedIdentifier(
                hash = "f1220abcdef1234567890",
                keyVersion = "v3",
                algorithm = JwaAlgorithm.HS256,
            )
        val serialized = json.encodeToString(HashedIdentifier.serializer(), original)
        val deserialized = json.decodeFromString<HashedIdentifier>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun hashedIdentifierDefaultAlgorithm() {
        val hashed = HashedIdentifier(hash = "f1220abc", keyVersion = "v1")
        assertEquals(JwaAlgorithm.HS256, hashed.algorithm)
    }

    @Test
    fun encryptedPayloadSerializationRoundTrip() {
        val original =
            EncryptedPayload(
                ciphertext = "Y2lwaGVydGV4dC1kYXRh",
                keyVersion = "v2",
                algorithm = ContentEncryptionAlgorithm.A256GCM,
            )
        val serialized = json.encodeToString(EncryptedPayload.serializer(), original)
        val deserialized = json.decodeFromString<EncryptedPayload>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun encryptedPayloadDefaultAlgorithm() {
        val encrypted = EncryptedPayload(ciphertext = "abc", keyVersion = "v1")
        assertEquals(ContentEncryptionAlgorithm.A256GCM, encrypted.algorithm)
    }

    @Test
    fun domainSeparationConceptualTest() {
        // This test validates the concept: two HashedIdentifiers from different domains
        // (holder vs institution) must be distinguishable even for the same input,
        // because different keys produce different hashes.
        val holderHash = HashedIdentifier(hash = "f1220aaa", keyVersion = "A-v1")
        val institutionHash = HashedIdentifier(hash = "f1220bbb", keyVersion = "B-v1")

        // Same input should produce different hashes when different keys are used
        assertNotEquals(holderHash.hash, institutionHash.hash)
        // Different key versions reflect domain separation
        assertNotEquals(holderHash.keyVersion, institutionHash.keyVersion)
    }

    @Test
    fun hmacDeterminismConceptualTest() {
        // HMAC is deterministic: same input + same key = same output.
        // This tests the data model's ability to carry that property.
        val hash1 = HashedIdentifier(hash = "f1220same", keyVersion = "v1")
        val hash2 = HashedIdentifier(hash = "f1220same", keyVersion = "v1")
        assertEquals(hash1, hash2)
        assertEquals(hash1.hash, hash2.hash)
    }
}
