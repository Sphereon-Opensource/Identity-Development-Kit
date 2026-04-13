/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transfer.reader

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandoverTest {
    @Test
    fun qrHandover_is_a_plain_domain_type() {
        assertTrue(QrHandover() is Handover<*, *>)
    }

    @Test
    fun nfcHandover_keeps_select_and_request_messages() {
        val handover =
            NfcHandover(
                handoverSelectMessage = byteArrayOf(0x01, 0x02, 0x03),
                handoverRequestMessage = byteArrayOf(0x04, 0x05),
            )

        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), handover.handoverSelectMessage)
        assertContentEquals(byteArrayOf(0x04, 0x05), handover.handoverRequestMessage)
    }

    @Test
    fun nfcHandover_allows_missing_request_message() {
        val handover =
            NfcHandover(
                handoverSelectMessage = byteArrayOf(0x01, 0x02, 0x03),
                handoverRequestMessage = null,
            )

        assertNull(handover.handoverRequestMessage)
    }

    @Test
    fun oid4vpHandover_keeps_hashes_and_nonce() {
        val handover =
            OID4VPHandover(
                clientIdHash = byteArrayOf(0x01, 0x02),
                responseUriHash = byteArrayOf(0x03, 0x04),
                nonce = "nonce-123",
            )

        assertContentEquals(byteArrayOf(0x01, 0x02), handover.clientIdHash)
        assertContentEquals(byteArrayOf(0x03, 0x04), handover.responseUriHash)
        assertEquals("nonce-123", handover.nonce)
    }

    @Test
    fun oid4vpHandover_factory_derives_hashes_and_keeps_authorization_nonce() {
        val handover =
            OID4VPHandover.fromClientIdAndResponseUri(
                clientId = "client-123",
                responseUri = "https://wallet.example.org/callback",
                mdocNonce = "mdoc-nonce",
                authorizationRequestNonce = "auth-nonce",
            )

        assertTrue(handover.clientIdHash.isNotEmpty())
        assertTrue(handover.responseUriHash.isNotEmpty())
        assertEquals("auth-nonce", handover.nonce)
    }

    @Test
    fun oid4vpHandover_equality_and_hashcode_follow_payload() {
        val first = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        val second = OID4VPHandover(byteArrayOf(0x01), byteArrayOf(0x02), "nonce")
        val different = OID4VPHandover(byteArrayOf(0x09), byteArrayOf(0x02), "nonce")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }

    @Test
    fun restApiHandover_keeps_hash_bytes() {
        val handover = RestApiHandover(byteArrayOf(0x01, 0x02, 0x03))

        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), handover.readerEngagementHash)
    }

    @Test
    fun restApiHandover_equality_and_hashcode_follow_payload() {
        val first = RestApiHandover(byteArrayOf(0x01, 0x02))
        val second = RestApiHandover(byteArrayOf(0x01, 0x02))
        val different = RestApiHandover(byteArrayOf(0x03, 0x04))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }
}
