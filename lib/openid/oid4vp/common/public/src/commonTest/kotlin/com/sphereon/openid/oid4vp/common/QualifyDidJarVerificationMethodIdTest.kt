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

package com.sphereon.openid.oid4vp.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * JAR `kid` is qualified against the DID taken from `client_id` so relative `#fragment`
 * kids verify the same as absolute DID URLs.
 */
class QualifyDidJarVerificationMethodIdTest {
    private val did = "did:web:verifier.example"

    @Test
    fun `keeps absolute DID URL rooted in client_id DID`() {
        assertEquals("$did#1", qualifyDidJarVerificationMethodId(did, "$did#1"))
    }

    @Test
    fun `concatenates relative fragment with client_id DID`() {
        assertEquals("$did#whatever", qualifyDidJarVerificationMethodId(did, "#whatever"))
        assertEquals("$did#0", qualifyDidJarVerificationMethodId(did, "#0"))
    }

    @Test
    fun `rejects kid rooted in a different DID`() {
        assertNull(qualifyDidJarVerificationMethodId(did, "did:web:other.example#1"))
    }

    @Test
    fun `rejects bare DID without fragment and empty fragment`() {
        assertNull(qualifyDidJarVerificationMethodId(did, did))
        assertNull(qualifyDidJarVerificationMethodId(did, "#"))
        assertNull(qualifyDidJarVerificationMethodId(did, ""))
        assertNull(qualifyDidJarVerificationMethodId("not-a-did", "#0"))
    }
}
