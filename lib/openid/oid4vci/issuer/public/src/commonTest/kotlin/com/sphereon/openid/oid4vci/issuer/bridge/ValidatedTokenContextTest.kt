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

@file:OptIn(ExperimentalTime::class)

package com.sphereon.openid.oid4vci.issuer.bridge

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ValidatedTokenContextTest {
    @Test
    fun newFieldsDefaultToNull() {
        val ctx =
            ValidatedTokenContext(
                subject = "sub",
                clientId = "client",
                scope = "openid",
                credentialConfigurationIds = listOf("PID"),
            )
        assertNull(ctx.userinfoClaims)
        assertNull(ctx.acr)
        assertNull(ctx.authTime)
        assertNull(ctx.upstreamSubject)
        assertNull(ctx.upstreamIssuer)
    }

    @Test
    fun existingFieldsAreUnaffected() {
        val ctx =
            ValidatedTokenContext(
                subject = "sub-1",
                clientId = "client-1",
                scope = "openid profile",
                credentialConfigurationIds = listOf("IdentityCredential"),
                credentialIdentifiers = listOf("id-001"),
                cnfJkt = "thumbprint-abc",
            )
        assertEquals("sub-1", ctx.subject)
        assertEquals("client-1", ctx.clientId)
        assertEquals("openid profile", ctx.scope)
        assertEquals(listOf("IdentityCredential"), ctx.credentialConfigurationIds)
        assertEquals(listOf("id-001"), ctx.credentialIdentifiers)
        assertEquals("thumbprint-abc", ctx.cnfJkt)
    }

    @Test
    fun fullyPopulatedContextCarriesAllFields() {
        val instant = Instant.fromEpochSeconds(1_700_000_000L)
        val claims = mapOf("email" to JsonPrimitive("user@example.com"))

        val ctx =
            ValidatedTokenContext(
                subject = "sub-2",
                clientId = "client-2",
                scope = "openid",
                credentialConfigurationIds = listOf("PID"),
                cnfJkt = "jkt-value",
                userinfoClaims = claims,
                acr = "urn:mace:incommon:iap:silver",
                authTime = instant,
                upstreamSubject = "ext-sub-42",
                upstreamIssuer = "https://idp.example.com",
            )

        assertEquals(claims, ctx.userinfoClaims)
        assertEquals("urn:mace:incommon:iap:silver", ctx.acr)
        assertEquals(instant, ctx.authTime)
        assertEquals("ext-sub-42", ctx.upstreamSubject)
        assertEquals("https://idp.example.com", ctx.upstreamIssuer)
    }
}
