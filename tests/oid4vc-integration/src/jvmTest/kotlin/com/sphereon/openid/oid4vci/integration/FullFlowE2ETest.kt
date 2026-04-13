package com.sphereon.openid.oid4vci.integration

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.Oid4vciHolder
import com.sphereon.openid.oid4vci.issuer.service.Oid4vciIssuerService
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Graph interface to access OID4VCI services from the session graph in tests.
 *
 * These services are bound via @ContributesBinding(SessionScope::class) in the impl modules,
 * so Metro merges them into the session graph. This interface lets us extract them by casting.
 */
@ContributesTo(SessionScope::class)
interface Oid4vciTestSessionGraph {
    val oid4vciIssuerService: Oid4vciIssuerService
    val oid4vciHolder: Oid4vciHolder
}

/**
 * E2E integration tests for the OID4VCI DI graph.
 *
 * Verifies that the real Metro DI graph can be created with all OID4VCI + OAuth2 modules
 * on the classpath, and that services are correctly wired.
 */
class FullFlowE2ETest {
    private val ctx = Oid4vciTestContext(this)

    @Test
    fun diGraphCreatesSuccessfully() {
        assertNotNull(ctx.app, "App graph should be created")
        assertNotNull(ctx.session, "Session should be created")
    }

    @Test
    fun issuerServiceResolvesFromDi() =
        runTest {
            val graph = ctx.session.graph as Oid4vciTestSessionGraph
            val issuerService = graph.oid4vciIssuerService
            assertNotNull(issuerService, "Issuer service should be resolved from DI")
            assertNotNull(issuerService.commands, "Issuer commands should be available")
        }

    @Test
    fun holderResolvesFromDi() =
        runTest {
            val graph = ctx.session.graph as Oid4vciTestSessionGraph
            val holder = graph.oid4vciHolder
            assertNotNull(holder, "Oid4vciHolder should be resolved from DI")
            assertNotNull(holder.commands, "Holder commands should be available")
        }

    @Test
    fun issuerAndHolderCommandsFullyWired() =
        runTest {
            val graph = ctx.session.graph as Oid4vciTestSessionGraph
            val issuer = graph.oid4vciIssuerService
            val holder = graph.oid4vciHolder

            // Both services exist in the same session scope
            assertNotNull(issuer)
            assertNotNull(holder)

            // Verify issuer commands are wired
            assertNotNull(issuer.commands.createCredentialOffer)
            assertNotNull(issuer.commands.buildIssuerMetadata)
            assertNotNull(issuer.commands.issueNonce)
            assertNotNull(issuer.commands.handleCredentialRequest)
            assertNotNull(issuer.commands.handleDeferredCredentialRequest)
            assertNotNull(issuer.commands.handleNotification)
            assertNotNull(issuer.commands.buildSignedIssuerMetadata)

            // Verify holder commands are wired
            assertNotNull(holder.commands.parseCredentialOffer)
            assertNotNull(holder.commands.resolveCredentialOffer)
            assertNotNull(holder.commands.resolveIssuerMetadata)
            assertNotNull(holder.commands.selectAuthorizationServer)
            assertNotNull(holder.commands.requestNonce)
            assertNotNull(holder.commands.exchangePreAuthorizedCode)
            assertNotNull(holder.commands.createCredentialRequestProof)
            assertNotNull(holder.commands.requestCredential)
            assertNotNull(holder.commands.requestDeferredCredential)
            assertNotNull(holder.commands.sendNotification)
        }
}
