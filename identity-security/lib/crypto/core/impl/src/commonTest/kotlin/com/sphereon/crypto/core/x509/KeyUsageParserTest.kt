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

package com.sphereon.crypto.core.x509

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyUsageParserTest {
    private fun expectedMap(vararg trueFlags: String): Map<String, Boolean> {
        val allFlags =
            listOf(
                "digitalSignature",
                "nonRepudiation",
                "keyEncipherment",
                "dataEncipherment",
                "keyAgreement",
                "keyCertSign",
                "cRLSign",
                "encipherOnly",
                "decipherOnly",
            )
        return allFlags.associateWith { flagName ->
            trueFlags.contains(flagName)
        }
    }

    @Test
    fun testParseKeyUsage_DigitalSignatureOnly() {
        // DER for BIT STRING (unused=7) '10000000 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x80.toByte(), 0x00)
        val expected = expectedMap("digitalSignature")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_NonRepudiationOnly() {
        // DER for BIT STRING (unused=7) '01000000 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x40.toByte(), 0x00)
        val expected = expectedMap("nonRepudiation")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_KeyEnciphermentOnly() {
        // DER for BIT STRING (unused=7) '00100000 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x20.toByte(), 0x00)
        val expected = expectedMap("keyEncipherment")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_DataEnciphermentOnly() {
        // DER for BIT STRING (unused=7) '00010000 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x10.toByte(), 0x00)
        val expected = expectedMap("dataEncipherment")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_KeyAgreementOnly() {
        // DER for BIT STRING (unused=7) '00001000 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x08.toByte(), 0x00)
        val expected = expectedMap("keyAgreement")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_KeyCertSignOnly() {
        // DER for BIT STRING (unused=7) '00000100 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x04.toByte(), 0x00)
        val expected = expectedMap("keyCertSign")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_CRLSignOnly() {
        // DER for BIT STRING (unused=7) '00000010 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x02.toByte(), 0x00)
        val expected = expectedMap("cRLSign")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_EncipherOnlyOnly() {
        // DER for BIT STRING (unused=7) '00000001 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x01.toByte(), 0x00)
        val expected = expectedMap("encipherOnly")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_DecipherOnlyOnly() {
        // DER for BIT STRING (unused=7) '00000000 1'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x00.toByte(), 0x80.toByte())
        val expected = expectedMap("decipherOnly")
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_MultipleFlags() {
        // DER for BIT STRING (unused=7) '10101100 1' (digitalSignature, keyEncipherment, keyAgreement, keyCertSign, decipherOnly)
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0xAC.toByte(), 0x80.toByte()) // AC = 1010 1100
        val expected =
            expectedMap(
                "digitalSignature",
                "keyEncipherment",
                "keyAgreement",
                "keyCertSign",
                "decipherOnly",
            )
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_FromOriginalCertificate() {
        val derBytes = byteArrayOf(0x03, 0x02, 0x05, 0xE0.toByte())
        val expected =
            expectedMap(
                "digitalSignature",
                "nonRepudiation",
                "keyEncipherment",
            )
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }

    @Test
    fun testParseKeyUsage_NoFlags() {
        // DER for BIT STRING (unused=7) '00000000 0'
        val derBytes = byteArrayOf(0x03, 0x03, 0x07, 0x00.toByte(), 0x00.toByte())
        val expected = expectedMap() // No flags are true
        val actual = parseKeyUsage(derBytes)
        assertEquals(expected, actual)
    }
}
