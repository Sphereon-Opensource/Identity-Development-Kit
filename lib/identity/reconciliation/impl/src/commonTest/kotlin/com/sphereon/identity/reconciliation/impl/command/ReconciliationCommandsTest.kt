package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.impl.testutil.ReconciliationTestContext
import com.sphereon.identity.reconciliation.model.CancelReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.CompleteReconciliationArgs
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.GetReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.ReconciliationProvider
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconciliationCommandsTest {

    private val ctx = ReconciliationTestContext("reconciliation-cmd-test", this)

    private val testProvider = ReconciliationProvider(
        id = "test-provider",
        name = "Test OIDC Provider",
        oidcClientId = "test-oidc-client",
        identifierAttributeName = "sub",
    )

    @Test
    fun getSessionReturnsCreatedSession() = runTest {
        ctx.providerStore.save(testProvider)

        val createResult = ctx.createSessionCommand.execute(
            CreateReconciliationSessionArgs(
                identifierHash = "sha256:get-test",
                identifierType = IdentifierType.EMAIL,
                providerId = "test-provider",
                tenantId = "tenant-1",
                redirectUri = "https://app.example.com/callback"
            )
        )
        assertTrue(createResult.isOk)
        val session = createResult.value.session

        val getResult = ctx.getSessionCommand.execute(
            GetReconciliationSessionArgs(sessionId = session.id, tenantId = "tenant-1")
        )
        assertTrue(getResult.isOk, "Should find session")
        assertEquals(session.id, getResult.value.id)
        assertEquals(ReconciliationSessionStatus.CREATED, getResult.value.status)
    }

    @Test
    fun getSessionNotFound() = runTest {
        val result = ctx.getSessionCommand.execute(
            GetReconciliationSessionArgs(sessionId = "nonexistent", tenantId = "tenant-1")
        )
        assertTrue(result.isErr, "Should fail for unknown session")
    }

    @Test
    fun cancelSessionSuccessfully() = runTest {
        ctx.providerStore.save(testProvider)

        val createResult = ctx.createSessionCommand.execute(
            CreateReconciliationSessionArgs(
                identifierHash = "sha256:cancel-test",
                identifierType = IdentifierType.EMAIL,
                providerId = "test-provider",
                tenantId = "tenant-1",
                redirectUri = "https://app.example.com/callback"
            )
        )
        assertTrue(createResult.isOk)
        val session = createResult.value.session

        val cancelResult = ctx.cancelSessionCommand.execute(
            CancelReconciliationSessionArgs(sessionId = session.id, tenantId = "tenant-1")
        )
        assertTrue(cancelResult.isOk, "Should cancel session")
        assertEquals(ReconciliationSessionStatus.CANCELLED, cancelResult.value.status)
    }

    @Test
    fun cancelSessionNotFound() = runTest {
        val result = ctx.cancelSessionCommand.execute(
            CancelReconciliationSessionArgs(sessionId = "nonexistent", tenantId = "tenant-1")
        )
        assertTrue(result.isErr, "Should fail for unknown session")
    }

    @Test
    fun completeReconciliationRejectsStateMismatch() = runTest {
        ctx.providerStore.save(testProvider)

        val createResult = ctx.createSessionCommand.execute(
            CreateReconciliationSessionArgs(
                identifierHash = "sha256:state-mismatch",
                identifierType = IdentifierType.EMAIL,
                providerId = "test-provider",
                tenantId = "tenant-1",
                redirectUri = "https://app.example.com/callback"
            )
        )
        assertTrue(createResult.isOk)
        val session = createResult.value.session

        val result = ctx.completeCommand.execute(
            CompleteReconciliationArgs(
                sessionId = session.id,
                tenantId = "tenant-1",
                authorizationCode = "auth-code-123",
                state = "wrong-state",
                internalIdentityId = "internal-user-1"
            )
        )

        assertTrue(result.isErr, "Should fail with state mismatch")
        assertTrue(result.error.code.contains("state_mismatch"), "Error code should indicate state mismatch")
    }

    @Test
    fun completeReconciliationRejectsExpiredSession() = runTest {
        ctx.providerStore.save(testProvider)

        val createResult = ctx.createSessionCommand.execute(
            CreateReconciliationSessionArgs(
                identifierHash = "sha256:expired-test",
                identifierType = IdentifierType.EMAIL,
                providerId = "test-provider",
                tenantId = "tenant-1",
                redirectUri = "https://app.example.com/callback"
            )
        )
        assertTrue(createResult.isOk)
        val session = createResult.value.session

        // Force-expire the session
        ctx.sessionStore.update(
            session.copy(expiresAt = kotlinx.datetime.Clock.System.now() - kotlin.time.Duration.parse("1h"))
        )

        val result = ctx.completeCommand.execute(
            CompleteReconciliationArgs(
                sessionId = session.id,
                tenantId = "tenant-1",
                authorizationCode = "auth-code-123",
                state = session.state!!,
                internalIdentityId = "internal-user-1"
            )
        )

        assertTrue(result.isErr, "Should fail with expired session")
        assertTrue(result.error.code.contains("expired"), "Error code should indicate expiration")
    }

    @Test
    fun completeReconciliationRejectsCompletedSession() = runTest {
        ctx.providerStore.save(testProvider)

        val createResult = ctx.createSessionCommand.execute(
            CreateReconciliationSessionArgs(
                identifierHash = "sha256:already-completed",
                identifierType = IdentifierType.EMAIL,
                providerId = "test-provider",
                tenantId = "tenant-1",
                redirectUri = "https://app.example.com/callback"
            )
        )
        assertTrue(createResult.isOk)
        val session = createResult.value.session

        // Mark as completed
        ctx.sessionStore.update(session.copy(status = ReconciliationSessionStatus.COMPLETED))

        val result = ctx.completeCommand.execute(
            CompleteReconciliationArgs(
                sessionId = session.id,
                tenantId = "tenant-1",
                authorizationCode = "auth-code-123",
                state = session.state!!,
                internalIdentityId = "internal-user-1"
            )
        )

        assertTrue(result.isErr, "Should fail with invalid state")
        assertTrue(result.error.code.contains("invalid_state"), "Error code should indicate invalid state")
    }
}
