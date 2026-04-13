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

package com.sphereon.identity.reconciliation.model

import com.sphereon.identity.matching.model.IdentifierType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ReconciliationModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun reconciliationSessionSerializationRoundTrip() {
        val now = Clock.System.now()
        val session =
            ReconciliationSession(
                id = "sess-1",
                tenantId = "t-1",
                status = ReconciliationSessionStatus.CREATED,
                identifierHash = "hash-abc",
                identifierType = IdentifierType.KEY,
                providerId = "provider-1",
                authorizationUrl = "https://idp.example.com/authorize?state=abc",
                state = "state-abc",
                nonce = "nonce-xyz",
                codeVerifier = "verifier-123",
                redirectUri = "https://app.example.com/callback",
                createdAt = now,
                expiresAt = now + 10.minutes,
            )

        val serialized = json.encodeToString(ReconciliationSession.serializer(), session)
        val deserialized = json.decodeFromString(ReconciliationSession.serializer(), serialized)

        assertEquals(session.id, deserialized.id)
        assertEquals(session.status, deserialized.status)
        assertEquals(session.identifierHash, deserialized.identifierHash)
        assertEquals(session.identifierType, deserialized.identifierType)
        assertEquals(session.state, deserialized.state)
        assertNull(deserialized.encryptedIdentity)
    }

    @Test
    fun resolvedIdentitySerializationRoundTrip() {
        val identity =
            ResolvedIdentity(
                externalSubject = "ext-sub-123",
                externalIssuer = "https://idp.example.com",
                claims = mapOf("email" to JsonPrimitive("user@example.com"), "name" to JsonPrimitive("Test User")),
                internalIdentityId = "internal-456",
            )

        val serialized = json.encodeToString(ResolvedIdentity.serializer(), identity)
        val deserialized = json.decodeFromString(ResolvedIdentity.serializer(), serialized)

        assertEquals("ext-sub-123", deserialized.externalSubject)
        assertEquals("https://idp.example.com", deserialized.externalIssuer)
        assertEquals(JsonPrimitive("user@example.com"), deserialized.claims["email"])
        assertEquals("internal-456", deserialized.internalIdentityId)
    }

    @Test
    fun reconciliationProviderSerializationRoundTrip() {
        val provider =
            ReconciliationProvider(
                id = "provider-1",
                name = "Test IdP",
                oidcClientId = "surf-oidc",
                identifierAttributeName = "sub",
                enabled = true,
                attributeMappings =
                    listOf(
                        ReconciliationAttributeMapping(source = "sub", target = "eduid", identifierType = "SUBJECT_ID", required = true),
                    ),
            )

        val serialized = json.encodeToString(ReconciliationProvider.serializer(), provider)
        val deserialized = json.decodeFromString(ReconciliationProvider.serializer(), serialized)

        assertEquals(provider.id, deserialized.id)
        assertEquals(provider.oidcClientId, deserialized.oidcClientId)
        assertEquals(1, deserialized.attributeMappings.size)
        assertEquals("sub", deserialized.attributeMappings[0].source)
    }

    @Test
    fun reconciliationSessionWithEncryptedIdentity() {
        val now = Clock.System.now()
        val encrypted =
            com.sphereon.identity.matching.crypto.EncryptedPayload(
                ciphertext = "encrypted-data",
                keyVersion = "v1",
            )
        val session =
            ReconciliationSession(
                id = "sess-1",
                tenantId = "t-1",
                status = ReconciliationSessionStatus.COMPLETED,
                identifierHash = "hash-abc",
                identifierType = IdentifierType.DID,
                providerId = "provider-1",
                encryptedIdentity = encrypted,
                createdAt = now,
                expiresAt = now + 10.minutes,
            )

        val serialized = json.encodeToString(ReconciliationSession.serializer(), session)
        val deserialized = json.decodeFromString(ReconciliationSession.serializer(), serialized)

        assertNotNull(deserialized.encryptedIdentity)
        assertEquals("encrypted-data", deserialized.encryptedIdentity!!.ciphertext)
        assertEquals("v1", deserialized.encryptedIdentity!!.keyVersion)
    }

    @Test
    fun createReconciliationSessionArgsSerializationRoundTrip() {
        val args =
            CreateReconciliationSessionArgs(
                identifierHash = "hash-abc",
                identifierType = IdentifierType.DID,
                providerId = "provider-1",
                tenantId = "tenant-1",
                redirectUri = "https://app.example.com/callback",
            )

        val serialized = json.encodeToString(CreateReconciliationSessionArgs.serializer(), args)
        val deserialized = json.decodeFromString(CreateReconciliationSessionArgs.serializer(), serialized)

        assertEquals(args.identifierHash, deserialized.identifierHash)
        assertEquals(args.identifierType, deserialized.identifierType)
        assertEquals(args.redirectUri, deserialized.redirectUri)
    }
}
