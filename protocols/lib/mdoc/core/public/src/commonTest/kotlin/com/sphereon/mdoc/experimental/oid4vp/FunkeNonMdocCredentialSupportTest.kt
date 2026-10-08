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

package com.sphereon.mdoc.experimental.oid4vp

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborString
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.testutil.encodeCoseKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for FunkeNonMdocCredentialSupport classes.
 */
class FunkeNonMdocCredentialSupportTest {
    private val protocolCodec = Oid4vpRequestProtocolCodecImpl()

    private fun createTestDeviceEngagementSecurity(): com.sphereon.mdoc.engagement.DeviceEngagementSecurity {
        val key =
            CoseKeyJson
                .Builder()
                .withKty(CoseKeyTypeEnum.EC2)
                .withCrv(CoseCurve.P_256)
                .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                .build()
                .toCbor()
        val encodedKey = CborEncodedItem<CoseKeyType>(encodeCoseKey(key), key)
        return com.sphereon.mdoc.engagement
            .DeviceEngagementSecurity(cipherSuite = 1u, eDeviceKeyBytes = encodedKey)
    }

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
        assertTrue(cbor is CredentialFormat)
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

    // CredentialFormat tests

    @Test
    fun testCredentialFormatCreation() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormat(alg = algs)
        assertEquals(1, format.alg.value.size)
        assertEquals("ES256", format.alg.value[0].value)
    }

    @Test
    fun testCredentialFormatAlgLabel() {
        assertEquals("alg", CredentialFormat.ALG.value)
    }

    // Oid4vpRequestProtocol tests

    @Test
    fun testOid4vpRequestProtocolCreation() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormat(alg = algs)
        val formatMap = mutableMapOf(CborString("mso_mdoc") to format)
        val protocol = Oid4vpRequestProtocol(format = formatMap)
        assertNotNull(protocol.format)
        assertEquals(1, protocol.format.size)
    }

    @Test
    fun testOid4vpRequestProtocolCredentialFormatLabel() {
        assertEquals("credentialFormat", Oid4vpRequestProtocol.CREDENTIAL_FORMAT.value)
    }

    @Test
    fun testOid4vpRequestProtocolSupportedAlgorithms() {
        val algs = CborArray(mutableListOf(CborString("ES256")))
        val format = CredentialFormat(alg = algs)
        val protocol = Oid4vpRequestProtocol(format = mutableMapOf(CborString("mso_mdoc") to format))

        val supportedAlgorithms = protocol.getSupportedAlgorithms(com.sphereon.mdoc.oid4vp.Oid4VPFormatIdentifier.MSO_MDOC)

        assertNotNull(supportedAlgorithms)
        assertEquals("ES256", supportedAlgorithms.alg.single())
    }

    @Test
    fun testOid4vpRequestProtocolCodecRoundTrip() {
        val protocol =
            Oid4vpRequestProtocol(
                format =
                    mutableMapOf(
                        CborString("mso_mdoc") to CredentialFormat(CborArray(mutableListOf(CborString("ES256")))),
                    ),
            )

        val protocolInfo = protocolCodec.encode(protocol).getOrThrow()
        val decoded = protocolCodec.decode(protocolInfo).getOrThrow()

        assertEquals(protocol, decoded)
    }

    @Test
    fun testDeviceEngagementGetOid4vpProtocolInfoUsesCodec() {
        val protocol =
            Oid4vpRequestProtocol(
                format =
                    mutableMapOf(
                        CborString("mso_mdoc") to CredentialFormat(CborArray(mutableListOf(CborString("ES256")))),
                    ),
            )
        val protocolInfo = protocolCodec.encode(protocol).getOrThrow()
        val engagement =
            DeviceEngagement.V1_0(
                security = createTestDeviceEngagementSecurity(),
                protocolInfo = protocolInfo,
                original = null,
            )

        val decoded = engagement.getOid4vpProtocolInfo(protocolCodec)

        assertEquals(protocol, decoded)
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
