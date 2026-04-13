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

package com.sphereon.mdoc.experimental.oid4vp

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for FunkeNonMdocCredentialSupport classes.
 */
class FunkeNonMdocCredentialSupportTest {

    // CredentialFormatJson tests

    @Test
    fun testCredentialFormatJsonCreation() {
        val algs = arrayOf("ES256", "ES384")
        val format = CredentialFormatJson(alg = algs)
        assertEquals(2, format.alg.size)
        assertEquals("ES256", format.alg[0])
        assertEquals("ES384", format.alg[1])
    }

    @Test
    fun testCredentialFormatJsonToCbor() {
        val format = CredentialFormatJson(alg = arrayOf("ES256"))
        val cbor = format.toCbor()
        assertNotNull(cbor)
        assertTrue(cbor is CredentialFormatCbor)
    }

    @Test
    fun testCredentialFormatJsonToJsonString() {
        val format = CredentialFormatJson(alg = arrayOf("ES256"))
        val json = format.toJsonString()
        assertNotNull(json)
        assertTrue(json.contains("ES256"))
    }

    @Test
    fun testCredentialFormatJsonEquality() {
        val format1 = CredentialFormatJson(alg = arrayOf("ES256", "ES384"))
        val format2 = CredentialFormatJson(alg = arrayOf("ES256", "ES384"))
        assertEquals(format1, format2)
    }

    @Test
    fun testCredentialFormatJsonEqualitySameInstance() {
        val format = CredentialFormatJson(alg = arrayOf("ES256"))
        assertEquals(format, format)
    }

    @Test
    fun testCredentialFormatJsonInequalityDifferentAlgs() {
        val format1 = CredentialFormatJson(alg = arrayOf("ES256"))
        val format2 = CredentialFormatJson(alg = arrayOf("ES384"))
        assertFalse(format1 == format2)
    }

    @Test
    fun testCredentialFormatJsonInequalityDifferentType() {
        val format = CredentialFormatJson(alg = arrayOf("ES256"))
        assertFalse(format.equals("not a CredentialFormatJson"))
    }

    @Test
    fun testCredentialFormatJsonHashCode() {
        val format1 = CredentialFormatJson(alg = arrayOf("ES256"))
        val format2 = CredentialFormatJson(alg = arrayOf("ES256"))
        assertEquals(format1.hashCode(), format2.hashCode())
    }

    // CredentialFormatCbor tests

    @Test
    fun testCredentialFormatCborCreation() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormatCbor(alg = algs)
        assertEquals(1, format.alg.value.size)
        assertEquals("ES256", format.alg.value[0].value)
    }

    @Test
    fun testCredentialFormatCborCborBuilder() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormatCbor(alg = algs)
        val builder = format.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testCredentialFormatCborAlgLabel() {
        assertEquals("alg", CredentialFormatCbor.ALG.value)
    }

    // Oid4vpRequestProtocolCbor tests

    @Test
    fun testOid4vpRequestProtocolCborCreation() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormatCbor(alg = algs)
        val formatMap = mutableMapOf(CborString("mso_mdoc") to format)
        val protocol = Oid4vpRequestProtocolCbor(format = formatMap)
        assertNotNull(protocol.format)
        assertEquals(1, protocol.format.size)
    }

    @Test
    fun testOid4vpRequestProtocolCborCborBuilder() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormatCbor(alg = algs)
        val formatMap = mutableMapOf(CborString("mso_mdoc") to format)
        val protocol = Oid4vpRequestProtocolCbor(format = formatMap)
        val builder = protocol.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testOid4vpRequestProtocolCborCredentialFormatLabel() {
        assertEquals("credentialFormat", Oid4vpRequestProtocolCbor.CREDENTIAL_FORMAT.value)
    }

    // Constants tests

    @Test
    fun testOid4vpProtocolInfoLiteral() {
        assertEquals("oid4vp", OID4VP_PROTOCOL_INFO_LITERAL)
    }

    @Test
    fun testOid4vpProtocolInfoLabel() {
        assertEquals("oid4vp", OID4VP_PROTOCOL_INFO_LABEL.value)
    }
}
