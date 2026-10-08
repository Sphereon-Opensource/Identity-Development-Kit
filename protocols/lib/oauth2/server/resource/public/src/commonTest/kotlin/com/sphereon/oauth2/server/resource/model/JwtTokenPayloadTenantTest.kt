/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.resource.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class JwtTokenPayloadTenantTest {

    private fun payload(claims: Map<String, JsonElement>) = TokenPayload.Jwt(
        sub = "svc",
        iss = "https://as.example",
        aud = null,
        exp = Instant.fromEpochSeconds(2_000_000_000),
        iat = Instant.fromEpochSeconds(1_000_000_000),
        scope = null,
        clientId = null,
        dpopJkt = null,
        jti = null,
        additionalClaims = claims,
    )

    @Test
    fun verifiedTenantComesFromTheTenantIdClaim() {
        assertEquals("tenant-a", payload(mapOf("tenant_id" to JsonPrimitive(" tenant-a "))).verifiedTenantId)
    }

    @Test
    fun missingBlankOrNonStringTenantClaimAttributesNoTenant() {
        assertNull(payload(emptyMap()).verifiedTenantId)
        assertNull(payload(mapOf("tenant_id" to JsonPrimitive(" "))).verifiedTenantId)
        assertNull(payload(mapOf("tenant_id" to JsonPrimitive(42))).verifiedTenantId)
        assertNull(payload(mapOf("tenant_id" to buildJsonArray { add(JsonPrimitive("tenant-a")) })).verifiedTenantId)
    }
}
