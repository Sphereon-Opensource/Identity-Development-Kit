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

package com.sphereon.statuslist.impl.envelope

import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.impl.codec.StatusListCodec
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusListEnvelopeTest {
    private fun args(
        spec: StatusListSpec,
        proofFormat: StatusProofFormat,
        encodedList: String,
    ) = SignStatusListTokenArgs(
        spec = spec,
        proofFormat = proofFormat,
        issuer = "did:example:issuer",
        statusListUri = "https://issuer.example/statuslists/1",
        signingKeyAlias = "key-1",
        bitsPerStatus = 1,
        length = 256,
        purposes = listOf(StatusPurpose.REVOCATION),
        encodedList = encodedList,
        issuedAtEpochSeconds = 1_700_000_000L,
        ttlSeconds = 3600,
    )

    @Test
    fun tokenStatusListEnvelopeBuildParseDecode() =
        runTest {
            val spec = StatusListSpec.TOKEN_STATUS_LIST
            val bitset =
                com.sphereon.statuslist.impl.codec.StatusBitset
                    .create(256, 1, StatusListCodec.bitOrderFor(spec))
            bitset.set(10, StatusValues.INVALID)
            val encoded = StatusListCodec.encode(bitset, spec)

            val payload = TokenStatusListEnvelope.buildPayload(args(spec, StatusProofFormat.JWT, encoded))
            val content = TokenStatusListEnvelope.parse(payload)
            assertEquals(1, content.bitsPerStatus)
            assertEquals(3600L, content.ttlSeconds)

            val decoded = StatusListCodec.decode(content.encodedList, content.bitsPerStatus, spec)
            assertEquals(StatusValues.INVALID, decoded.get(10))
            assertEquals(StatusValues.VALID, decoded.get(0))
        }

    @Test
    fun bitstringCredentialBuildParseDecode() =
        runTest {
            val spec = StatusListSpec.BITSTRING_STATUS_LIST
            val bitset =
                com.sphereon.statuslist.impl.codec.StatusBitset
                    .create(256, 1, StatusListCodec.bitOrderFor(spec))
            bitset.set(200, StatusValues.INVALID)
            val encoded = StatusListCodec.encode(bitset, spec)

            val credential = BitstringStatusListEnvelope.buildCredential(args(spec, StatusProofFormat.VC_JWT, encoded))
            val content = BitstringStatusListEnvelope.parse(credential)
            assertEquals("revocation", content.statusPurpose)

            val decoded = StatusListCodec.decode(content.encodedList, content.statusSize, spec)
            assertEquals(StatusValues.INVALID, decoded.get(200))
            assertEquals(StatusValues.VALID, decoded.get(0))
        }
}
