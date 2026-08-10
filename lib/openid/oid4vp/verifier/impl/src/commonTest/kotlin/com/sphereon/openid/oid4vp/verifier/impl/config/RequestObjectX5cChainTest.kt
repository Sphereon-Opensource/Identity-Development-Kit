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

import com.sphereon.crypto.core.x509.certificateFromBase64Der
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestObjectX5cChainTest {
    @Test
    fun omitsTerminalSelfIssuedTrustAnchor() {
        val storedChain = listOf("leaf", "intermediate", "root")

        val emittedChain = requestObjectX5cChain(storedChain) { certificate -> certificate == "root" }

        assertEquals(listOf("leaf", "intermediate"), emittedChain)
    }

    @Test
    fun preservesTerminalIntermediate() {
        val storedChain = listOf("leaf", "intermediate")

        val emittedChain = requestObjectX5cChain(storedChain) { false }

        assertEquals(storedChain, emittedChain)
    }

    @Test
    fun preservesSingleCertificateLeaf() {
        val storedChain = listOf("leaf")

        val emittedChain = requestObjectX5cChain(storedChain) { true }

        assertEquals(storedChain, emittedChain)
    }

    @Test
    fun rejectsEmptyChain() {
        assertFailsWith<IllegalArgumentException> {
            requestObjectX5cChain(emptyList()) { false }
        }
    }

    @Test
    fun omitsProvisionedOidfProtocolRoot() {
        val protocolRoot =
            "MIIBuzCCAWGgAwIBAgIUEA/mJ/KKrGwkUVIDWKaR/fFmlJkwCgYIKoZIzj0EAwIwKzEpMCcGA1UEAwwg" +
                "VkRYIE9JREYgQ29uZm9ybWFuY2UgUHJvdG9jb2wgQ0EwHhcNMjYwNzE4MTE1MDQ5WhcNMzYwNzE1MTE1" +
                "MDQ5WjArMSkwJwYDVQQDDCBWRFggT0lERiBDb25mb3JtYW5jZSBQcm90b2NvbCBDQTBZMBMGByqGSM49" +
                "AgEGCCqGSM49AwEHA0IABLbAYCl9npPJqERXehY9E8sUl0wYngC8kjaWj2LUGjvDOPyJ02C7qp+SRwQN" +
                "ZkFKT10d7cb5KjikJ6KGSOOXEfijYzBhMB8GA1UdIwQYMBaAFL6HmMsj3C0i7jsDa359mqwKg9rfMA8G" +
                "A1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBS+h5jLI9wtIu47A2t+fZqsCoPa" +
                "3zAKBggqhkjOPQQDAgNIADBFAiAotuaXJlv3f5deiq/edcT6oAWBt2ld0cxTpU9EjbHmzAIhAI52IZAr" +
                "EaPg0G+ST5JJK+nbZtQVsX/qxXJzfivrVLLD"
        val parsed = certificateFromBase64Der(protocolRoot)

        assertEquals(parsed.subjectDN, parsed.issuerDN)
        assertEquals(listOf("leaf"), requestObjectX5cChain(listOf("leaf", protocolRoot)))
    }

    @Test
    fun verifierDidWebAuthorityRetainsANonDefaultPort() {
        assertEquals(
            "tenant.example:25443",
            verifierHostOf("https://tenant.example:25443/oid4vp"),
        )
        assertEquals("tenant.example:25443", verifierHostOf("tenant.example:25443"))
        assertNull(verifierHostOf("https://user@tenant.example:25443/oid4vp"))
    }
}
