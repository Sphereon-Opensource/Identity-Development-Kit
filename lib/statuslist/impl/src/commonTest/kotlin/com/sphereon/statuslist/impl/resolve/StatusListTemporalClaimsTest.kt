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

package com.sphereon.statuslist.impl.resolve

import com.sphereon.core.api.Err
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.statuslist.StatusListSpec
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusListTemporalClaimsTest {
    @Test
    fun tokenStatusListAcceptsAbsentAndFutureExpiry() {
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject {},
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isOk,
        )
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("exp", NOW + 1) },
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isOk,
        )
    }

    @Test
    fun tokenStatusListAcceptsFiniteFractionalFutureExpiryWithoutTruncation() {
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("exp", NOW + 0.5) },
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isOk,
        )
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("exp", NOW - 0.5) },
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isErr,
        )
    }

    @Test
    fun tokenStatusListRejectsExpiryAtOrBeforeNow() {
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("exp", NOW) },
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isErr,
        )
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("exp", NOW - 1) },
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isErr,
        )
    }

    @Test
    fun tokenStatusListRejectsNonNumericStringAndNonFiniteExpiry() {
        listOf(
            JsonPrimitive((NOW + 1).toString()),
            JsonPrimitive(true),
            JsonPrimitive(Double.NaN),
            JsonPrimitive(Double.POSITIVE_INFINITY),
            JsonPrimitive(Double.NEGATIVE_INFINITY),
        ).forEach { expiry ->
            assertTrue(
                validateJwtStatusListTemporalClaims(
                    payload = buildJsonObject { put("exp", expiry) },
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    nowEpochSeconds = NOW,
                ).isErr,
                "expiry $expiry must be rejected",
            )
        }
    }

    @Test
    fun mdocCwtRejectsExpiryAtExactCurrentTime() {
        assertTrue(
            validateMdocStatusListTemporalClaims(
                expiresAtEpochSeconds = NOW,
                issuedAtEpochSeconds = null,
                ttlSeconds = null,
                nowEpochSeconds = NOW,
            ).isErr,
        )
        assertTrue(
            validateMdocStatusListTemporalClaims(
                expiresAtEpochSeconds = NOW + 1,
                issuedAtEpochSeconds = null,
                ttlSeconds = null,
                nowEpochSeconds = NOW,
            ).isOk,
        )
    }

    @Test
    fun mdocCwtTtlRequiresIssuedAt() {
        val result =
            validateMdocStatusListTemporalClaims(
                expiresAtEpochSeconds = NOW + 3_600,
                issuedAtEpochSeconds = null,
                ttlSeconds = 300,
                nowEpochSeconds = NOW,
            )

        assertTrue(result.isErr)
        assertEquals("mdoc revocation CWT ttl requires iat", (result as Err).error)
    }

    @Test
    fun mdocCwtRejectsNonPositiveTtl() {
        listOf(0L, -1L).forEach { ttl ->
            val result =
                validateMdocStatusListTemporalClaims(
                    expiresAtEpochSeconds = NOW + 3_600,
                    issuedAtEpochSeconds = NOW - 1,
                    ttlSeconds = ttl,
                    nowEpochSeconds = NOW,
                )

            assertTrue(result.isErr, "ttl $ttl must be rejected")
            assertEquals("mdoc revocation CWT ttl must be greater than zero", (result as Err).error)
        }
    }

    @Test
    fun mdocCwtRejectsIatPlusTtlOverflow() {
        val result =
            validateMdocStatusListTemporalClaims(
                expiresAtEpochSeconds = Long.MAX_VALUE,
                issuedAtEpochSeconds = Long.MAX_VALUE - 5,
                ttlSeconds = 6,
                nowEpochSeconds = NOW,
            )

        assertTrue(result.isErr)
        assertEquals("mdoc revocation CWT iat plus ttl overflows", (result as Err).error)
    }

    @Test
    fun mdocCwtRejectsDerivedNextUpdateAtExactCurrentTime() {
        val result =
            validateMdocStatusListTemporalClaims(
                expiresAtEpochSeconds = NOW + 3_600,
                issuedAtEpochSeconds = NOW - 300,
                ttlSeconds = 300,
                nowEpochSeconds = NOW,
            )

        assertTrue(result.isErr)
        assertEquals("mdoc revocation CWT publication freshness is expired", (result as Err).error)
    }

    @Test
    fun mdocCwtAcceptsDerivedNextUpdateAfterCurrentTime() {
        assertTrue(
            validateMdocStatusListTemporalClaims(
                expiresAtEpochSeconds = NOW + 3_600,
                issuedAtEpochSeconds = NOW - 299,
                ttlSeconds = 300,
                nowEpochSeconds = NOW,
            ).isOk,
        )
    }

    @Test
    fun mdocCwtAllowsOnlyTheIsoProtectedSignatureAlgorithms() {
        val missing = validateMdocStatusListProtectedAlgorithm(null)

        assertTrue(missing.isErr)
        assertEquals("mdoc revocation CWT requires protected alg", (missing as Err).error)
        listOf(
            CoseAlgorithm.ES256,
            CoseAlgorithm.ES384,
            CoseAlgorithm.ES512,
            CoseAlgorithm.EdDSA,
        ).forEach { algorithm ->
            assertTrue(
                validateMdocStatusListProtectedAlgorithm(algorithm).isOk,
                "$algorithm is an allowed ISO mdoc status-list signature algorithm",
            )
        }
        listOf(
            CoseAlgorithm.ES256K,
            CoseAlgorithm.HS256,
            CoseAlgorithm.RS256,
            CoseAlgorithm.PS256,
            CoseAlgorithm.A128GCM,
        ).forEach { algorithm ->
            val result = validateMdocStatusListProtectedAlgorithm(algorithm)
            assertTrue(result.isErr, "$algorithm must not be accepted for ISO mdoc status lists")
            assertEquals("mdoc revocation CWT protected alg is unsupported", (result as Err).error)
        }
    }

    @Test
    fun genericCwtRejectsExpiryAtExactCurrentTime() {
        assertTrue(
            validateGenericCwtTemporalClaims(
                expiresAtEpochSeconds = NOW,
                nowEpochSeconds = NOW,
            ).isErr,
        )
        assertTrue(
            validateGenericCwtTemporalClaims(
                expiresAtEpochSeconds = NOW + 1,
                nowEpochSeconds = NOW,
            ).isOk,
        )
    }

    @Test
    fun bitstringStatusListAcceptsAbsentAndFutureValidUntil() {
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject {},
                spec = StatusListSpec.BITSTRING_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isOk,
        )
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject {
                    put("exp", NOW - 1)
                    put("validUntil", "2023-11-14T22:13:21Z")
                },
                spec = StatusListSpec.BITSTRING_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isOk,
            "Bitstring Status List VC-JWT uses validUntil rather than JWT exp",
        )
    }

    @Test
    fun bitstringStatusListRejectsValidUntilAtOrBeforeNow() {
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("validUntil", "2023-11-14T22:13:20Z") },
                spec = StatusListSpec.BITSTRING_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isErr,
        )
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("validUntil", "2023-11-14T22:13:19Z") },
                spec = StatusListSpec.BITSTRING_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isErr,
        )
    }

    @Test
    fun bitstringStatusListRejectsMalformedValidUntil() {
        assertTrue(
            validateJwtStatusListTemporalClaims(
                payload = buildJsonObject { put("validUntil", "not-an-instant") },
                spec = StatusListSpec.BITSTRING_STATUS_LIST,
                nowEpochSeconds = NOW,
            ).isErr,
        )
    }

    private companion object {
        private const val NOW = 1_700_000_000L
    }
}
