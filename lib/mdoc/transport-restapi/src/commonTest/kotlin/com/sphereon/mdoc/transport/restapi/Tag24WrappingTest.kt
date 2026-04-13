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

package com.sphereon.mdoc.transport.restapi

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodecImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Tag24WrappingTest {
    private val readerEngagementCborCodec = ReaderEngagementCborCodecImpl()

    @Test
    fun testCodecTag24WrappingMatchesExpected() {
        // RDW QR code with UNWRAPPED ReaderEngagement (starts with 0xa3, not 0xd818)
        val qrCode =
            "mdoc://owBjMS4xAYIB2BhYS6QBAiABIVggzzR7n13ilUKBaRhmnAApagKPiqn1qOfEkWQ_TKVq5UYiWCBc6ZWwMkjVLAlVIRLvAkslI04EjJVvUow8EHBY8bSggAKBgwQBoQB4Umh0dHBzOi8vcmVhZGVyLnJkdy5tZG9jLm9ubGluZS9hcGkvMi4wL0FubmV4QS8zNzhjM2U4Yi05ZmZjLTQyMDYtYWI3Yy1jMzY5N2FiOGVjYzU"

        val readerEngagement = readerEngagementCborCodec.decodeUri(qrCode).getOrThrow().value
        val unwrappedBytes = readerEngagement.original ?: error("original is null")

        println("\n=== Tag 24 Wrapping Test ===")
        println("Unwrapped bytes (${unwrappedBytes.size}): ${unwrappedBytes.encodeToHex()}")

        val wrappedBytes = readerEngagementCborCodec.encodeTag24(readerEngagement).getOrThrow()

        println("Wrapped bytes (${wrappedBytes.size}): ${wrappedBytes.encodeToHex()}")

        // Verify wrapping
        assertEquals(0xD8.toByte(), wrappedBytes[0], "First byte should be 0xD8 (Tag)")
        assertEquals(0x18.toByte(), wrappedBytes[1], "Second byte should be 0x18 (Tag 24)")

        // The wrapped size should be: 1 (tag) + 1 (tag number) + 1 (bstr header) + N (unwrapped size)
        // For 179 bytes, the bstr header is 0x58 0xB3 (2 bytes)
        val expectedSize = 1 + 1 + 2 + unwrappedBytes.size
        assertEquals(expectedSize, wrappedBytes.size, "Wrapped size should be unwrapped + 4 bytes of overhead")

        // Hash both versions
        val unwrappedHash = hash(unwrappedBytes, DigestAlg.SHA256)
        val wrappedHash = hash(wrappedBytes, DigestAlg.SHA256)

        println("Unwrapped hash: ${unwrappedHash.encodeToHex()}")
        println("Wrapped hash: ${wrappedHash.encodeToHex()}")

        // The hashes MUST be different
        assertFalse(
            unwrappedHash.contentEquals(wrappedHash),
            "Hashes of wrapped and unwrapped should be different",
        )

        println("=== Test Complete ===\n")
    }

    @Test
    fun testHashMatchesLoggedValue() {
        // From the logs, we know the unwrapped ReaderEngagement hash is:
        // abfa68bb400284976aff4de541d197ee7bbe7a44cf06913009b8a50bccda202c
        // But we should be hashing the WRAPPED bytes!

        val qrCode =
            "mdoc://owBjMS4xAYIB2BhYS6QBAiABIVggzzR7n13ilUKBaRhmnAApagKPiqn1qOfEkWQ_TKVq5UYiWCBc6ZWwMkjVLAlVIRLvAkslI04EjJVvUow8EHBY8bSggAKBgwQBoQB4Umh0dHBzOi8vcmVhZGVyLnJkdy5tZG9jLm9ubGluZS9hcGkvMi4wL0FubmV4QS8zNzhjM2U4Yi05ZmZjLTQyMDYtYWI3Yy1jMzY5N2FiOGVjYzU"

        val readerEngagement = readerEngagementCborCodec.decodeUri(qrCode).getOrThrow().value
        val unwrappedBytes = readerEngagement.original ?: error("original is null")

        // Hash unwrapped (this is what we were doing WRONG)
        val unwrappedHash = hash(unwrappedBytes, DigestAlg.SHA256)
        println("\n=== Hash Comparison ===")
        println("Unwrapped hash: ${unwrappedHash.encodeToHex()}")
        println("Expected from logs: abfa68bb400284976aff4de541d197ee7bbe7a44cf06913009b8a50bccda202c")

        assertEquals(
            "abfa68bb400284976aff4de541d197ee7bbe7a44cf06913009b8a50bccda202c",
            unwrappedHash.encodeToHex(),
            "Unwrapped hash should match what was logged",
        )

        // Now wrap and hash through the typed codec boundary (this is what we SHOULD do)
        val wrappedBytes = readerEngagementCborCodec.encodeTag24(readerEngagement).getOrThrow()
        val wrappedHash = hash(wrappedBytes, DigestAlg.SHA256)

        println("Wrapped hash: ${wrappedHash.encodeToHex()}")
        println("This is the CORRECT hash for REST API handover")
        println("=== Comparison Complete ===\n")
    }
}
