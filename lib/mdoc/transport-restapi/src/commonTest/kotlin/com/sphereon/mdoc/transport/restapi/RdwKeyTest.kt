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
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test using actual RDW reader keys from logs to verify key derivation
 */
class RdwKeyTest {

    @Test
    @Ignore
    fun testRdwKeyDerivation() = runTest {
        println("\n=== RDW Key Derivation Test with Real Keys ===\n")

        // Holder keys from actual RDW logs (2025-11-27)
        val holderPrivateD = "ea2e5e5d8bd2e7979a6bd21c4a768fe337fcd3b00b1d4bd26a1fd5a1c7902eb4".decodeFromHex()
        val holderPublicX = "4690ca5b6db21133b30e4c821661f7f9ccd2cebd69a0f6096e560fdd86d7e064".decodeFromHex()
        val holderPublicY = "bf92d1a5b1a30afbc50daf52918b8235e8dea3ce558c5323b81e90130c3b86c5".decodeFromHex()

        // Reader keys from ReaderEngagement in QR code
        val readerPublicX = "cf347b9f5de29542816918669c00296a028f8aa9f5a8e7c491643f4ca56ae546".decodeFromHex()
        val readerPublicY = "5ce995b03248d52c09552112ef024b25234e048c956f528c3c107058f1b4a080".decodeFromHex()

        // Expected values from logs
        val expectedSalt = "e466cb96025e270e62f4c3cad1fc480e16fb162722923b639e3d53fd12a5cdb5"
        val expectedSharedSecret = "8a10dbaf5f0704b46a91bb09bfed329b22aab4572d736118cb6950adf28ecdc5"
        val expectedSKDevice = "78d4988542a618ff58083307f686b5ccbdfc44cbb84cee4ce5400abd21f8b2f4"
        val expectedSKReader = "d82601c04b78ab2972c2d318b4ec14183c4ba38d402ee8f4082dfaf32b1b9a99"

        println("Holder d: ${holderPrivateD.encodeToHex()}")
        println("Holder x: ${holderPublicX.encodeToHex()}")
        println("Holder y: ${holderPublicY.encodeToHex()}")
        println()
        println("Reader x: ${readerPublicX.encodeToHex()}")
        println("Reader y: ${readerPublicY.encodeToHex()}")
        println()

        // Create holder key - P-256 (secp256r1)
        val holderKey = CoseKey(
            generateKid = false,
            kty = KeyTypeMapping.EC.cose.toCbor(),
            crv = Curve.P_256.cose.toCbor(),  // COSE curve 1 = P-256
            d = holderPrivateD.toCborByteString(),
            x = holderPublicX.toCborByteString(),
            y = holderPublicY.toCborByteString()
        )

        println("Holder key created:")
        println("  kty: ${holderKey.kty}")  // Should be 2 (EC2)
        println("  crv: ${holderKey.crv}")  // Should be 1 (P-256)
        println()

        // Create reader key - P-256 (secp256r1)
        val readerKey = CoseKey(
            generateKid = false,
            kty = KeyTypeMapping.EC.cose.toCbor(),
            crv = Curve.P_256.cose.toCbor(),  // COSE curve 1 = P-256
            x = readerPublicX.toCborByteString(),
            y = readerPublicY.toCborByteString()
        )

        println("Reader key created:")
        println("  kty: ${readerKey.kty}")  // Should be 2 (EC2)
        println("  crv: ${readerKey.crv}")  // Should be 1 (P-256)
        println()

        // Verify keys are properly created
        assertNotNull(holderKey.d, "Holder key should have private component")
        assertNotNull(holderKey.x, "Holder key should have x coordinate")
        assertNotNull(holderKey.y, "Holder key should have y coordinate")
        assertEquals(1L, holderKey.crv?.value, "Holder key should be P-256 (curve 1)")

        assertNotNull(readerKey.x, "Reader key should have x coordinate")
        assertNotNull(readerKey.y, "Reader key should have y coordinate")
        assertEquals(1L, readerKey.crv?.value, "Reader key should be P-256 (curve 1)")

        // SessionTranscript from logs  
        val sessionTranscriptBytes =
            "83d81859010da40063312e31018201d8185886a7010202582b7870656e724431617934383163724d556c7a5674577533794345674a596268386970396f2d7a4d6531426b031bfffffffffffffff904810120012158204690ca5b6db21133b30e4c821661f7f9ccd2cebd69a0f6096e560fdd86d7e064225820bf92d1a5b1a30afbc50daf52918b8235e8dea3ce558c5323b81e90130c3b86c50281830401a100785268747470733a2f2f7265616465722e7264772e6d646f632e6f6e6c696e652f6170692f322e302f416e6e6578412f33373863336538622d396666632d343230362d616237632d6333363937616238656363350581a363636174016474797065016764657461696c73a166646f6d61696e60d818584ba401022001215820cf347b9f5de29542816918669c00296a028f8aa9f5a8e7c491643f4ca56ae5462258205ce995b03248d52c09552112ef024b25234e048c956f528c3c107058f1b4a0805820abfa68bb400284976aff4de541d197ee7bbe7a44cf06913009b8a50bccda202c".decodeFromHex()

        println("Creating SessionEncryption as MDOC (holder)...")
        println()

        // Create session encryption as holder
        // IMPORTANT: Per ISO 18013-5 Section 9.1.1.4, the HKDF salt is SHA-256(SessionTranscript)
        // where SessionTranscript is the raw CBOR array (starts with 0x83), NOT wrapped in Tag 24.
        // The sessionTranscriptBytes already contains raw bytes starting with 0x83, so use directly.
        val holderSessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

    }
}
