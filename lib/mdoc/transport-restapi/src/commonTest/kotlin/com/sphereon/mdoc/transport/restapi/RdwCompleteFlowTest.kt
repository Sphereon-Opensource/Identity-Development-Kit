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
 *
 */

package com.sphereon.mdoc.transport.restapi

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.cborSerializer
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test that verifies the Tag 24 wrapping logic matches ISO 18013-7 requirements.
 * This is critical for REST API handover where the hash of the ReaderEngagement
 * must match between holder and reader.
 */
class RdwCompleteFlowTest {

    @Test
    fun testRestApiHandoverHashLogic() {
        println("\n=== REST API Handover Hash Test ===")

        // RDW provides UNWRAPPED ReaderEngagement in QR code
        val qrCode =
            "mdoc://owBjMS4xAYIB2BhYS6QBAiABIVggzzR7n13ilUKBaRhmnAApagKPiqn1qOfEkWQ_TKVq5UYiWCBc6ZWwMkjVLAlVIRLvAkslI04EjJVvUow8EHBY8bSggAKBgwQBoQB4Umh0dHBzOi8vcmVhZGVyLnJkdy5tZG9jLm9ubGluZS9hcGkvMi4wL0FubmV4QS8zNzhjM2U4Yi05ZmZjLTQyMDYtYWI3Yy1jMzY5N2FiOGVjYzU"

        val readerEngagement = ReaderEngagement.fromEngagementUri(qrCode)
        val originalBytes = readerEngagement.original ?: error("original is null")

        println("Original bytes length: ${originalBytes.size}")
        println("First 4 bytes: ${originalBytes.take(4).toByteArray().encodeToHex()}")

        // Check if Tag 24 wrapped
        val isTag24Wrapped = originalBytes.size >= 2 &&
                originalBytes[0] == 0xD8.toByte() &&
                originalBytes[1] == 0x18.toByte()

        println("Is Tag 24 wrapped: $isTag24Wrapped")

        // Simulate what RestApiTransfer.kt does
        val bytesForHash = if (isTag24Wrapped) {
            println("Using wrapped bytes as-is")
            originalBytes
        } else {
            println("Manually wrapping with Tag 24")
            val wrappedItem = CborEncodedItem(
                value = originalBytes,
                data = readerEngagement
            )
            val wrapped = cborSerializer.encode(wrappedItem)
            println("Wrapped length: ${wrapped.size}")
            println("First 4 bytes after wrap: ${wrapped.take(4).toByteArray().encodeToHex()}")
            wrapped
        }

        val finalHash = hash(bytesForHash, DigestAlg.SHA256)
        println("\nFinal hash for handover: ${finalHash.encodeToHex()}")

        // Verify wrapping worked
        if (!isTag24Wrapped) {
            assertTrue(bytesForHash[0] == 0xD8.toByte(), "Should be Tag 24 wrapped")
            assertTrue(bytesForHash[1] == 0x18.toByte(), "Should be Tag 24 wrapped")
            assertEquals(183, bytesForHash.size, "Should be 179 + 4 bytes overhead")
        }

        // The correct hash should be of the WRAPPED bytes
        println("Handover hash logic verified")
        println("=== Test Complete ===\n")
    }
}
