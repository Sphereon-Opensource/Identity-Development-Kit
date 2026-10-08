/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault.portability

import com.sphereon.data.store.vault.VaultExportRecipient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TdfEnvelopeContractTest {
    @Test
    fun `test envelope provider performs authenticated encryption and round trips`() =
        runTest {
            val provider = InMemoryAesGcmTestEnvelopeProvider()
            val plaintext = "deterministic BagIt archive bytes".encodeToByteArray()
            val request = protectRequest()
            val output = CollectingSink()

            val descriptor =
                provider.protect(
                    request = request,
                    plaintext = VaultByteProducer { sink -> sink.write(plaintext) },
                    output = output,
                )
            val protected = output.bytes()
            assertFalse(protected.contentEquals(plaintext))
            assertEquals("did:example:recipient", descriptor.recipientRef)

            val opened =
                provider.open(
                    TdfOpenRequest("did:example:recipient", "key:recipient"),
                    TestByteSource(protected),
                )
            assertContentEquals(plaintext, opened.plaintext.readAll())
        }

    @Test
    fun `TDF request is structurally singular recipient and test provider cannot register as standard`() {
        val request = protectRequest()
        val encoded = Json.encodeToString(request)

        assertTrue("\"recipient\"" in encoded)
        assertFalse("\"recipients\"" in encoded)
        assertEquals("did:example:recipient", request.recipient.recipientRef)
        assertFailsWith<VaultPortabilityError.TdfProviderRequired> {
            TdfEnvelopeProviderRegistry(listOf(InMemoryAesGcmTestEnvelopeProvider())).requireStandard("test-only-aes-gcm")
        }
    }

    private fun protectRequest() =
        TdfProtectRequest(
            recipient = VaultExportRecipient("did:example:recipient", "key:recipient"),
            policyBinding = "policy:portable-export",
            plaintextDigest = testDigest(),
        )
}
