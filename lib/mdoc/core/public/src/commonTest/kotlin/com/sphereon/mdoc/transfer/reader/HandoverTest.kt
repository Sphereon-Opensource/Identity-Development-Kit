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
    fun oid4vpHandover_keeps_inputs_for_b26_handover_info() {
        val handover =
            OID4VPHandover(
                clientId = "x509_hash:abc",
                nonce = "auth-nonce",
                jwkThumbprint = byteArrayOf(0x10, 0x11, 0x12),
                responseUri = "https://verifier.example.org/cb",
            )

        assertEquals("x509_hash:abc", handover.clientId)
        assertEquals("auth-nonce", handover.nonce)
        assertContentEquals(byteArrayOf(0x10, 0x11, 0x12), handover.jwkThumbprint)
        assertEquals("https://verifier.example.org/cb", handover.responseUri)
    }

    @Test
    fun oid4vpHandover_factory_passes_inputs_through() {
        val handover =
            OID4VPHandover.fromOid4vpInputs(
                clientId = "client-123",
                nonce = "auth-nonce",
                jwkThumbprint = byteArrayOf(0x20),
                responseUri = "https://wallet.example.org/callback",
            )

        assertEquals("client-123", handover.clientId)
        assertEquals("auth-nonce", handover.nonce)
        assertContentEquals(byteArrayOf(0x20), handover.jwkThumbprint)
        assertEquals("https://wallet.example.org/callback", handover.responseUri)
    }

    @Test
    fun oid4vpHandover_unencrypted_uses_null_thumbprint() {
        val handover =
            OID4VPHandover.fromOid4vpInputs(
                clientId = "client-123",
                nonce = "auth-nonce",
                jwkThumbprint = null,
                responseUri = "https://wallet.example.org/callback",
            )

        assertNull(handover.jwkThumbprint)
    }

    @Test
    fun oid4vpHandover_equality_and_hashcode_follow_payload() {
        val first = OID4VPHandover("c", "n", byteArrayOf(0x01), "u")
        val second = OID4VPHandover("c", "n", byteArrayOf(0x01), "u")
        val different = OID4VPHandover("c", "n", byteArrayOf(0x09), "u")
        val nullThumb = OID4VPHandover("c", "n", null, "u")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
        assertNotEquals(first, nullThumb)
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
