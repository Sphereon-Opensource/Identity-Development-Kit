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

package com.sphereon.openid.oid4vp.auth.impl.http.command

import com.sphereon.core.api.http.HttpJson
import com.sphereon.identity.reconciliation.model.KnownHolderState
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import com.sphereon.openid.oid4vp.auth.model.IdvRequirementReason
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import com.sphereon.openid.oid4vp.auth.model.ReconciliationPlanType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for IDV_REQUIRED status serialization and session model extensions.
 *
 * Covers Phase 1 test requirements:
 * - IDV_REQUIRED serialization/deserialization
 * - New session fields (reconciliationSessionId, holderIdentifierHash, idvMessage)
 * - Status response with idvMessage
 */
class Oid4vpAuthSessionStatusSerializationTest {
    private val json = HttpJson.restApi

    // --- IDV_REQUIRED status serialization ---

    @Test
    fun `IDV_REQUIRED status serializes correctly`() {
        val response =
            Oid4vpAuthStatusResponse(
                sessionId = "session-123",
                status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                idvRequirementReason = IdvRequirementReason.CANDIDATE_MATCH_CONFIRMATION,
                idvMessage = "Identity verification required",
            )
        val serialized = json.encodeToString(Oid4vpAuthStatusResponse.serializer(), response)
        assertTrue(serialized.contains("IDV_REQUIRED"))
        assertTrue(serialized.contains("CANDIDATE_MATCH_CONFIRMATION"))
        assertTrue(serialized.contains("Identity verification required"))
    }

    @Test
    fun `IDV_REQUIRED status deserializes correctly`() {
        val input = """{"sessionId":"session-123","status":"IDV_REQUIRED","idvRequirementReason":"FIRST_TIME_LINK","idvMessage":"Please verify"}"""
        val response = json.decodeFromString(Oid4vpAuthStatusResponse.serializer(), input)
        assertEquals(Oid4vpAuthSessionStatus.IDV_REQUIRED, response.status)
        assertEquals(IdvRequirementReason.FIRST_TIME_LINK, response.idvRequirementReason)
        assertEquals("Please verify", response.idvMessage)
    }

    @Test
    fun `IDV_REQUIRED fromValue works`() {
        assertEquals(Oid4vpAuthSessionStatus.IDV_REQUIRED, Oid4vpAuthSessionStatus.fromValue("IDV_REQUIRED"))
    }

    @Test
    fun `IDV_REQUIRED fromValue case insensitive`() {
        assertEquals(Oid4vpAuthSessionStatus.IDV_REQUIRED, Oid4vpAuthSessionStatus.fromValue("idv_required"))
    }

    // --- Session model fields ---

    @Test
    fun `session with reconciliation fields serializes correctly`() {
        val now = Clock.System.now()
        val session =
            Oid4vpAuthSession(
                sessionId = "session-abc",
                correlationId = "corr-123",
                queryId = "test-query",
                status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                reconciliationSessionId = "recon-session-456",
                reconciliationPlanType = ReconciliationPlanType.STEP_UP,
                holderIdentifierHash = "sha256:abc123def",
                knownHolderState = KnownHolderState.MATCHED_CLAIM_TUPLE,
                idvRequirementReason = IdvRequirementReason.CANDIDATE_MATCH_CONFIRMATION,
                idvMessage = "First-time wallet login requires identity verification",
                requestedProjection = "sts",
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 5.minutes,
            )

        val serialized = json.encodeToString(Oid4vpAuthSession.serializer(), session)
        assertTrue(serialized.contains("reconciliation_session_id"))
        assertTrue(serialized.contains("recon-session-456"))
        assertTrue(serialized.contains("reconciliation_plan_type"))
        assertTrue(serialized.contains("STEP_UP"))
        assertTrue(serialized.contains("holder_identifier_hash"))
        assertTrue(serialized.contains("sha256:abc123def"))
        assertTrue(serialized.contains("known_holder_state"))
        assertTrue(serialized.contains("MATCHED_CLAIM_TUPLE"))
        assertTrue(serialized.contains("idv_requirement_reason"))
        assertTrue(serialized.contains("CANDIDATE_MATCH_CONFIRMATION"))
        assertTrue(serialized.contains("idv_message"))
        assertTrue(serialized.contains("requested_projection"))
        assertTrue(serialized.contains("sts"))
        assertTrue(serialized.contains("First-time wallet login"))
    }

    @Test
    fun `session reconciliation fields default to null`() {
        val now = Clock.System.now()
        val session =
            Oid4vpAuthSession(
                sessionId = "session-def",
                correlationId = "corr-456",
                queryId = "test-query",
                status = Oid4vpAuthSessionStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 5.minutes,
            )

        assertNull(session.reconciliationSessionId)
        assertNull(session.reconciliationPlanType)
        assertNull(session.holderIdentifierHash)
        assertNull(session.knownHolderState)
        assertNull(session.idvRequirementReason)
        assertNull(session.idvMessage)
        assertNull(session.requestedProjection)
    }

    @Test
    fun `session isIdvRequired returns true for IDV_REQUIRED status`() {
        val now = Clock.System.now()
        val session =
            Oid4vpAuthSession(
                sessionId = "s1",
                correlationId = "c1",
                queryId = "q1",
                status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 5.minutes,
            )
        assertTrue(session.isIdvRequired())
    }

    @Test
    fun `session isIdvRequired returns false for other statuses`() {
        val now = Clock.System.now()
        for (status in listOf(
            Oid4vpAuthSessionStatus.PENDING,
            Oid4vpAuthSessionStatus.VERIFIED,
            Oid4vpAuthSessionStatus.COMPLETED,
            Oid4vpAuthSessionStatus.EXPIRED,
            Oid4vpAuthSessionStatus.ERROR,
        )) {
            val session =
                Oid4vpAuthSession(
                    sessionId = "s1",
                    correlationId = "c1",
                    queryId = "q1",
                    status = status,
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = now + 5.minutes,
                )
            assertTrue(!session.isIdvRequired(), "isIdvRequired should be false for $status")
        }
    }

    // --- Status response with idvMessage ---

    @Test
    fun `status response from session includes idvMessage`() {
        val now = Clock.System.now()
        val session =
            Oid4vpAuthSession(
                sessionId = "session-xyz",
                correlationId = "corr-xyz",
                queryId = "test-query",
                status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                idvRequirementReason = IdvRequirementReason.FIRST_TIME_LINK,
                idvMessage = "Reconciliation needed",
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 5.minutes,
            )

        val response = Oid4vpAuthStatusResponse.from(session)
        assertEquals("session-xyz", response.sessionId)
        assertEquals(Oid4vpAuthSessionStatus.IDV_REQUIRED, response.status)
        assertEquals(IdvRequirementReason.FIRST_TIME_LINK, response.idvRequirementReason)
        assertEquals("Reconciliation needed", response.idvMessage)
    }

    @Test
    fun `status response from session without idvMessage has null`() {
        val now = Clock.System.now()
        val session =
            Oid4vpAuthSession(
                sessionId = "session-xyz",
                correlationId = "corr-xyz",
                queryId = "test-query",
                status = Oid4vpAuthSessionStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 5.minutes,
            )

        val response = Oid4vpAuthStatusResponse.from(session)
        assertNull(response.idvMessage)
    }

    @Test
    fun `status response with idvMessage serializes to JSON`() {
        val response =
            Oid4vpAuthStatusResponse(
                sessionId = "s1",
                status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                idvMessage = "Please complete identity verification",
            )

        val serialized = json.encodeToString(Oid4vpAuthStatusResponse.serializer(), response)
        assertTrue(serialized.contains("\"idvMessage\""))
        assertTrue(serialized.contains("Please complete identity verification"))
    }

    @Test
    fun `status response without idvMessage omits or nulls the field`() {
        val response =
            Oid4vpAuthStatusResponse(
                sessionId = "s1",
                status = Oid4vpAuthSessionStatus.PENDING,
            )

        val serialized = json.encodeToString(Oid4vpAuthStatusResponse.serializer(), response)
        // idvMessage should either be absent or null in JSON
        assertNull(response.idvMessage)
    }

    // --- IDV response model serialization ---

    @Test
    fun `IdvInitiateResponse serializes correctly`() {
        val response =
            com.sphereon.openid.oid4vp.auth.http.model.IdvInitiateResponse(
                sessionId = "sess-1",
                redirectUrl = "https://idp.example.com/authorize?client_id=test",
                planType = ReconciliationPlanType.STEP_UP,
                idvRequirementReason = IdvRequirementReason.CANDIDATE_MATCH_CONFIRMATION,
            )
        val serialized =
            json.encodeToString(
                com.sphereon.openid.oid4vp.auth.http.model.IdvInitiateResponse
                    .serializer(),
                response,
            )
        assertTrue(serialized.contains("sess-1"))
        assertTrue(serialized.contains("https://idp.example.com/authorize"))
        assertTrue(serialized.contains("STEP_UP"))
        assertTrue(serialized.contains("CANDIDATE_MATCH_CONFIRMATION"))
    }

    @Test
    fun `IdvStatusResponse serializes correctly`() {
        val response =
            com.sphereon.openid.oid4vp.auth.http.model.IdvStatusResponse(
                sessionId = "sess-1",
                status = "CREATED",
                message = "Reconciliation session created",
                planType = ReconciliationPlanType.STEP_UP,
                idvRequirementReason = IdvRequirementReason.CANDIDATE_MATCH_CONFIRMATION,
            )
        val serialized =
            json.encodeToString(
                com.sphereon.openid.oid4vp.auth.http.model.IdvStatusResponse
                    .serializer(),
                response,
            )
        assertTrue(serialized.contains("CREATED"))
        assertTrue(serialized.contains("Reconciliation session created"))
        assertTrue(serialized.contains("STEP_UP"))
        assertTrue(serialized.contains("CANDIDATE_MATCH_CONFIRMATION"))
    }
}
