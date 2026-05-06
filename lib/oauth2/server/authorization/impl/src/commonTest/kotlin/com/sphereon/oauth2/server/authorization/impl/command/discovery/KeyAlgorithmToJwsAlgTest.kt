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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Locks the `SignatureAlgorithm` -> JWS `alg` mapping consumed by discovery metadata and
 * id_token signing. A drift here would silently re-introduce the OIDF Conformance bug where
 * `id_token_signing_alg_values_supported` advertised one alg but the JWS header carried another.
 */
class KeyAlgorithmToJwsAlgTest {
    @Test
    fun rsaSha256MapsToRs256() {
        assertEquals("RS256", keyAlgorithmToJwsAlg(SignatureAlgorithm.RSA_SHA256))
    }

    @Test
    fun rsaSha384MapsToRs384() {
        assertEquals("RS384", keyAlgorithmToJwsAlg(SignatureAlgorithm.RSA_SHA384))
    }

    @Test
    fun rsaSha512MapsToRs512() {
        assertEquals("RS512", keyAlgorithmToJwsAlg(SignatureAlgorithm.RSA_SHA512))
    }

    @Test
    fun ecdsaSha256MapsToEs256() {
        assertEquals("ES256", keyAlgorithmToJwsAlg(SignatureAlgorithm.ECDSA_SHA256))
    }

    @Test
    fun ecdsaSha384MapsToEs384() {
        assertEquals("ES384", keyAlgorithmToJwsAlg(SignatureAlgorithm.ECDSA_SHA384))
    }

    @Test
    fun ecdsaSha512MapsToEs512() {
        assertEquals("ES512", keyAlgorithmToJwsAlg(SignatureAlgorithm.ECDSA_SHA512))
    }

    @Test
    fun ed25519MapsToEdDsa() {
        assertEquals("EdDSA", keyAlgorithmToJwsAlg(SignatureAlgorithm.ED25519))
    }

    @Test
    fun pss256MapsToPs256() {
        assertEquals("PS256", keyAlgorithmToJwsAlg(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
    }

    @Test
    fun pss384MapsToPs384() {
        assertEquals("PS384", keyAlgorithmToJwsAlg(SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1))
    }

    @Test
    fun pss512MapsToPs512() {
        assertEquals("PS512", keyAlgorithmToJwsAlg(SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1))
    }

    @Test
    fun unsupportedAlgorithmThrows() {
        assertFailsWith<IllegalStateException> {
            keyAlgorithmToJwsAlg(SignatureAlgorithm.ES256K)
        }
    }
}
