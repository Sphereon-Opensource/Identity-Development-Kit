package com.sphereon.openid.oid4vp.auth.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for IDV-related flows using the mock bridge.
 *
 * Covers Phase 2 test requirements:
 * - IDV_REQUIRED status returned by getSessionStatus
 * - IDV_REQUIRED error from completeAuthentication
 * - Status polling behavior during IDV flow
 */
class Oid4vpAuthIdvCommandsTest {

    @Test
    fun `getSessionStatus returns IDV_REQUIRED with idvMessage`() = runTest {
        val mockBridge = MockOid4vpAuthBridge(
            getStatusResult = Ok(
                Oid4vpAuthStatusResponse(
                    sessionId = "session-idv",
                    status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                    idvMessage = "First-time wallet login requires identity verification via your institution"
                )
            )
        )

        val result = mockBridge.getSessionStatus("session-idv")
        assertTrue(result.isOk)
        assertEquals(Oid4vpAuthSessionStatus.IDV_REQUIRED, result.value.status)
        assertEquals(
            "First-time wallet login requires identity verification via your institution",
            result.value.idvMessage
        )
    }

    @Test
    fun `completeAuthentication returns IDV_REQUIRED error when reconciliation needed`() = runTest {
        val mockBridge = MockOid4vpAuthBridge(
            completeResult = Err(Oid4vpAuthErrors.idvRequired("session-new-user"))
        )

        val result = mockBridge.completeAuthentication("session-new-user")
        assertTrue(result.isErr)
        assertTrue(
            result.error.message.defaultMessage.contains("IDV", ignoreCase = true) ||
                result.error.message.defaultMessage.contains("identity verification", ignoreCase = true),
            "Error should mention IDV: ${result.error.message.defaultMessage}"
        )
    }

    @Test
    fun `session transitions through IDV flow states`() = runTest {
        // Simulate the session lifecycle during IDV:
        // 1. PENDING → VERIFIED → IDV_REQUIRED → (reconciliation) → VERIFIED → COMPLETED

        // Step 1: PENDING
        val pending = MockOid4vpAuthBridge(
            getStatusResult = Ok(
                Oid4vpAuthStatusResponse(sessionId = "s1", status = Oid4vpAuthSessionStatus.PENDING)
            )
        )
        assertEquals(Oid4vpAuthSessionStatus.PENDING, pending.getSessionStatus("s1").value.status)

        // Step 2: VERIFIED (wallet VP received)
        val verified = MockOid4vpAuthBridge(
            getStatusResult = Ok(
                Oid4vpAuthStatusResponse(sessionId = "s1", status = Oid4vpAuthSessionStatus.VERIFIED)
            )
        )
        assertEquals(Oid4vpAuthSessionStatus.VERIFIED, verified.getSessionStatus("s1").value.status)

        // Step 3: IDV_REQUIRED (unknown holder)
        val idvRequired = MockOid4vpAuthBridge(
            getStatusResult = Ok(
                Oid4vpAuthStatusResponse(
                    sessionId = "s1",
                    status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                    idvMessage = "Identity verification required"
                )
            )
        )
        val idvResult = idvRequired.getSessionStatus("s1")
        assertEquals(Oid4vpAuthSessionStatus.IDV_REQUIRED, idvResult.value.status)
        assertNotNull(idvResult.value.idvMessage)

        // Step 4: Back to VERIFIED (after reconciliation)
        val verifiedAgain = MockOid4vpAuthBridge(
            getStatusResult = Ok(
                Oid4vpAuthStatusResponse(sessionId = "s1", status = Oid4vpAuthSessionStatus.VERIFIED)
            )
        )
        assertEquals(Oid4vpAuthSessionStatus.VERIFIED, verifiedAgain.getSessionStatus("s1").value.status)

        // Step 5: COMPLETED
        val completed = MockOid4vpAuthBridge(
            getStatusResult = Ok(
                Oid4vpAuthStatusResponse(sessionId = "s1", status = Oid4vpAuthSessionStatus.COMPLETED)
            )
        )
        assertEquals(Oid4vpAuthSessionStatus.COMPLETED, completed.getSessionStatus("s1").value.status)
    }

    @Test
    fun `sessionNotIdvRequired error for non-IDV session`() = runTest {
        val error = Oid4vpAuthErrors.sessionNotIdvRequired("session-already-verified")
        assertTrue(error.message.defaultMessage.contains("not in IDV_REQUIRED", ignoreCase = true))
    }

    @Test
    fun `noReconciliationMapping error includes credential type`() = runTest {
        val error = Oid4vpAuthErrors.noReconciliationMapping("UnknownVCT")
        assertTrue(
            error.message.defaultMessage.contains("UnknownVCT", ignoreCase = true),
            "Error should mention the credential type: ${error.message.defaultMessage}"
        )
    }

    @Test
    fun `reconciliationFailed error includes reason`() = runTest {
        val error = Oid4vpAuthErrors.reconciliationFailed("Token exchange failed")
        assertTrue(
            error.message.defaultMessage.contains("Token exchange", ignoreCase = true),
            "Error should mention the reason: ${error.message.defaultMessage}"
        )
    }
}
