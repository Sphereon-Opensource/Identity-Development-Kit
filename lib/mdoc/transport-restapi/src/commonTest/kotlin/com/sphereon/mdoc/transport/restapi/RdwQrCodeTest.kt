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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodecImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RdwQrCodeTest {
    @Test
    fun testRdwQrCodeDecoding() {
        // Actual RDW QR code from logs (2025-11-27 23:06:01.939)
        val qrCode =
            "mdoc://owBjMS4xAYIB2BhYS6QBAiABIVggzzR7n13ilUKBaRhmnAApagKPiqn1qOfEkWQ_TKVq5UYiWCBc6ZWwMkjVLAlVIRLvAkslI04EjJVvUow8EHBY8bSggAKBgwQBoQB4Umh0dHBzOi8vcmVhZGVyLnJkdy5tZG9jLm9ubGluZS9hcGkvMi4wL0FubmV4QS8zNzhjM2U4Yi05ZmZjLTQyMDYtYWI3Yy1jMzY5N2FiOGVjYzU"

        println("\n=== RDW QR Code Analysis ===")
        println("QR Code: $qrCode")

        // Step 1: Extract base64url part
        assertTrue(qrCode.startsWith("mdoc://"), "QR code should start with mdoc://")
        val base64Part = qrCode.substring(7)
        println("Base64Url part length: ${base64Part.length}")

        // Step 2: Decode base64url
        val rawBytes = base64Part.decodeFromBase64Url()
        println("Raw bytes length: ${rawBytes.size}")
        println("Raw bytes hex (first 20): ${rawBytes.take(20).toByteArray().encodeToHex()}")
        println("First byte: 0x${rawBytes[0].toUByte().toString(16)}")

        // Check if Tag 24 wrapped
        val isTag24Wrapped = rawBytes[0] == 0xD8.toByte() && rawBytes[1] == 0x18.toByte()
        println("Is Tag 24 wrapped (starts with 0xD818): $isTag24Wrapped")

        if (!isTag24Wrapped) {
            println("ERROR: ReaderEngagement in QR code is NOT Tag 24 wrapped!")
            println("This violates ISO 18013-7 which requires Tag 24 wrapping for REST API")
        }

        // Step 3: Decode ReaderEngagement
        val readerEngagement = ReaderEngagementCborCodecImpl().decodeUri(qrCode).getOrThrow().value
        println("ReaderEngagement decoded successfully")
        println("ReaderEngagement version: ${readerEngagement.version}")

        // Step 4: Check if original bytes are preserved
        val originalBytes = readerEngagement.original
        assertNotNull(originalBytes, "ReaderEngagement.original should not be null")
        println("Original bytes length: ${originalBytes.size}")
        println("Original bytes hex (first 20): ${originalBytes.take(20).toByteArray().encodeToHex()}")

        // Step 5: Verify original bytes match what we expect
        if (isTag24Wrapped) {
            // If input was Tag 24 wrapped, original should be the wrapped bytes
            assertTrue(
                originalBytes.contentEquals(rawBytes),
                "Original should preserve Tag 24 wrapped bytes",
            )
            assertEquals(183, originalBytes.size, "Tag 24 wrapped ReaderEngagement should be 183 bytes")
        } else {
            // If input was NOT Tag 24 wrapped, original will be the unwrapped bytes
            // This is the bug! For REST API, we NEED Tag 24 wrapped bytes
            println("WARNING: Original bytes are unwrapped (${originalBytes.size} bytes)")
            println("For REST API handover, we need to manually wrap with Tag 24")
        }

        println("\n=== Analysis Complete ===")
    }
}
