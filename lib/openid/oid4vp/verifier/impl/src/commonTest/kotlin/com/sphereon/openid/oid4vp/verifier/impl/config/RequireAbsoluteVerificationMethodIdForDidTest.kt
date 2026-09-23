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

package com.sphereon.openid.oid4vp.verifier.impl.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * OID4VP §5.9.3 `decentralized_identifier`: JOSE `kid` identifies a verification method
 * in the DID Document of the DID taken from `client_id`. Absolute DID URLs and relative
 * `#fragment` kids are accepted; relative kids are qualified by concatenating the client DID.
 */
class RequireAbsoluteVerificationMethodIdForDidTest {
    @Test
    fun `accepts absolute DID URL rooted in the client_id DID`() {
        val did = "did:web:verifier.example"
        assertEquals(
            "$did#1",
            requireAbsoluteVerificationMethodIdForDid(did, "$did#1"),
        )
    }

    @Test
    fun `qualifies relative fragment against client_id DID`() {
        val did = "did:web:verifier.example"
        assertEquals(
            "$did#issuer-assertion",
            requireAbsoluteVerificationMethodIdForDid(did, "#issuer-assertion"),
        )
        assertFailsWith<IllegalArgumentException> {
            requireAbsoluteVerificationMethodIdForDid(did, "issuer-assertion")
        }
    }

    @Test
    fun `rejects kid rooted in a different DID`() {
        val did = "did:web:verifier.example"
        assertFailsWith<IllegalArgumentException> {
            requireAbsoluteVerificationMethodIdForDid(
                did,
                "did:web:other.example#issuer-assertion",
            )
        }
    }

    @Test
    fun `rejects bare DID without fragment`() {
        val did = "did:jwk:eyJhbGciOiJFUzI1NiJ9"
        assertFailsWith<IllegalArgumentException> {
            requireAbsoluteVerificationMethodIdForDid(did, did)
        }
    }
}
