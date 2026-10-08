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

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.validation.jwsAlgToDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JWS-alg → digest mapping + server-metadata consistency validator.
 *
 * The digest family is determined by the `alg` numeric suffix per OIDC Core §3.1.3.6
 * (RS/ES/PS/HS-256 → SHA-256, …384 → SHA-384, …512 → SHA-512). The validator catches
 * configurations that advertise capabilities IDK cannot back — unsupported sig algs or the
 * pairwise subject type that has no implementation yet.
 */
class JwsAlgDigestTest {
    @Test
    fun sha256FamilyMapsToSha256() {
        listOf("RS256", "ES256", "PS256", "HS256").forEach { alg ->
            assertEquals(DigestAlg.SHA256, jwsAlgToDigest(alg), "alg=$alg")
            assertEquals(DigestAlg.SHA256, jwsAlgToDigest(alg.lowercase()), "alg=${alg.lowercase()} (case-insensitive)")
        }
    }

    @Test
    fun sha384FamilyMapsToSha384() {
        listOf("RS384", "ES384", "PS384", "HS384").forEach { alg ->
            assertEquals(DigestAlg.SHA384, jwsAlgToDigest(alg))
        }
    }

    @Test
    fun sha512FamilyMapsToSha512() {
        listOf("RS512", "ES512", "PS512", "HS512").forEach { alg ->
            assertEquals(DigestAlg.SHA512, jwsAlgToDigest(alg))
        }
    }

    @Test
    fun unknownAlgMapsToNull() {
        assertNull(jwsAlgToDigest("none"))
        assertNull(jwsAlgToDigest("EdDSA"))
        assertNull(jwsAlgToDigest("RS128"))
        assertNull(jwsAlgToDigest(""))
    }

    // ─── Server-metadata consistency validator ─────────────────────────────

    @Test
    fun validConfigPasses() {
        val config =
            OAuth2ServerInstanceConfig(
                oidc = FeaturePolicy.SUPPORTED,
                idTokenSigningAlgValuesSupported = setOf("ES256", "RS256"),
                subjectTypesSupported = listOf("public"),
            )
        val result = validateServerMetadataConsistency(config)
        assertTrue(result.isOk)
    }

    @Test
    fun unsupportedSigningAlgRejected() {
        val config =
            OAuth2ServerInstanceConfig(
                oidc = FeaturePolicy.SUPPORTED,
                idTokenSigningAlgValuesSupported = setOf("ES256", "EdDSA"),
            )
        val result = validateServerMetadataConsistency(config)
        assertTrue(result.isErr)
    }

    @Test
    fun pairwiseSubjectTypeRejected() {
        val config =
            OAuth2ServerInstanceConfig(
                oidc = FeaturePolicy.SUPPORTED,
                idTokenSigningAlgValuesSupported = setOf("ES256"),
                subjectTypesSupported = listOf("public", "pairwise"),
            )
        val result = validateServerMetadataConsistency(config)
        assertTrue(result.isErr, "pairwise must be rejected until sector-specific sub hashing is implemented")
    }

    @Test
    fun nullSigningAlgConfigIsAllowed() {
        // Metadata builder falls back to "ES256" when idTokenSigningAlgValuesSupported is null;
        // validator should not reject an unspecified config.
        val config =
            OAuth2ServerInstanceConfig(
                oidc = FeaturePolicy.SUPPORTED,
                idTokenSigningAlgValuesSupported = null,
            )
        val result = validateServerMetadataConsistency(config)
        assertTrue(result.isOk)
    }
}
