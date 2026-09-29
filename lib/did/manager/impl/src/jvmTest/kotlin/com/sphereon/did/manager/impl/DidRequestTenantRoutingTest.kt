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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AppGraph
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.command.AddAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.AddControllerServiceCommand
import com.sphereon.did.manager.command.CreateAlsoKnownAsInput
import com.sphereon.did.manager.command.CreateControllerInput
import com.sphereon.did.manager.command.ResolveDidInput
import com.sphereon.did.manager.command.ResolveDidServiceCommand
import com.sphereon.did.manager.command.StringValueBody
import com.sphereon.did.persistence.DidDetail
import com.sphereon.did.persistence.DidRecord
import com.sphereon.did.persistence.DidRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * DID persistence is routed by the tenant resolved for the executing request
 * ([com.sphereon.core.api.context.SessionExecution.tenantId]), not by the tenant of the session's
 * own user context. The same DID exists in tenant A and tenant B with different record ids, so
 * every lookup and write shows which tenant it reached.
 *
 * - Subdomain: an anonymous request on tenant A's host runs in a tenant A user context.
 * - Path: a request whose session belongs to tenant B (host tenant and principal) is peeled to
 *   tenant A by a `LeadingSlug`/`WellKnownSuffix` path policy; the HTTP dispatcher records that
 *   in the session's [MutableResolvedTenantIdProvider], exactly as done here.
 */
class DidRequestTenantRoutingTest {
    @ContributesTo(AppScope::class)
    interface RepositoryAccess {
        val didRepository: DidRepository
    }

    @ContributesTo(SessionScope::class)
    interface RequestAccess {
        val resolvedTenantIdProvider: MutableResolvedTenantIdProvider
        val resolveDidCommand: ResolveDidServiceCommand
        val addControllerCommand: AddControllerServiceCommand
        val addAlsoKnownAsCommand: AddAlsoKnownAsServiceCommand
    }

    private lateinit var app: AppGraph
    private lateinit var repository: DidRepository

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
            ),
        )
        app = createDidManagerTestAppGraph(this)
        app.userContextManager.destroyAll()
        repository = (app as RepositoryAccess).didRepository
        runBlocking {
            assertTrue(repository.save(seed(TENANT_A, RECORD_A)).isOk, "seed tenant A")
            assertTrue(repository.save(seed(TENANT_B, RECORD_B)).isOk, "seed tenant B")
        }
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) app.userContextManager.destroyAll()
    }

    @Test
    fun anonymousRequestOnTenantSubdomainResolvesThatTenantsDid(): Unit =
        runBlocking {
            val session = anonymousSubdomainRequest(TENANT_A, "req-subdomain-a")

            assertEquals(RECORD_A, didManager(session).get(DID).value.id)
            val resolved = request(session).resolveDidCommand.execute(ResolveDidInput(did = DID))
            assertTrue(resolved.isOk, "public resolution failed: $resolved")
            assertNotNull(resolved.value.didDocument, "tenant A document")
        }

    @Test
    fun pathPeeledRequestResolvesThePeeledTenantNotTheSessionTenant(): Unit =
        runBlocking {
            val session = tenantBRequest("req-path-to-a")
            assertEquals(RECORD_B, didManager(session).get(DID).value.id, "before the peel the host tenant applies")

            request(session).resolvedTenantIdProvider.setCurrentTenantId(TENANT_A)
            try {
                assertEquals(RECORD_A, didManager(session).get(DID).value.id)
                val resolved = request(session).resolveDidCommand.execute(ResolveDidInput(did = DID))
                assertTrue(resolved.isOk, "public resolution failed: $resolved")
                assertNotNull(resolved.value.didDocument, "tenant A document")
            } finally {
                request(session).resolvedTenantIdProvider.clearCurrentTenantId()
            }
        }

    @Test
    fun childRowWritesUseThePeeledTenant(): Unit =
        runBlocking {
            val session = tenantBRequest("req-write-to-a")
            request(session).resolvedTenantIdProvider.setCurrentTenantId(TENANT_A)
            try {
                val controller =
                    request(session).addControllerCommand.execute(
                        CreateControllerInput(did = DID, body = StringValueBody("did:example:controller-a")),
                    )
                assertTrue(controller.isOk, "add controller failed: $controller")
                val aka =
                    request(session).addAlsoKnownAsCommand.execute(
                        CreateAlsoKnownAsInput(did = DID, body = StringValueBody("urn:tenant-a:aka")),
                    )
                assertTrue(aka.isOk, "add alsoKnownAs failed: $aka")
            } finally {
                request(session).resolvedTenantIdProvider.clearCurrentTenantId()
            }

            val tenantA = stored(TENANT_A)
            assertEquals(listOf("did:example:controller-a"), tenantA.controller.map { it.controllerDid })
            assertEquals(listOf("urn:tenant-a:aka"), tenantA.alsoKnownAs.map { it.akaUri })
            val tenantB = stored(TENANT_B)
            assertTrue(tenantB.controller.isEmpty(), "the session's own tenant must not receive the write")
            assertTrue(tenantB.alsoKnownAs.isEmpty(), "the session's own tenant must not receive the write")
        }

    @Test
    fun concurrentRequestsForDifferentTenantsResolveTheirOwnDid(): Unit =
        runBlocking {
            val start = CompletableDeferred<Unit>()
            val requests =
                (0 until REQUESTS).map { index ->
                    val viaPath = index % 2 == 1
                    val expected = if (index % 4 < 2) RECORD_A else RECORD_B
                    val tenant = if (expected == RECORD_A) TENANT_A else TENANT_B
                    val session =
                        if (viaPath) {
                            tenantBRequest("req-concurrent-$index").also { session ->
                                request(session).resolvedTenantIdProvider.setCurrentTenantId(tenant)
                            }
                        } else {
                            anonymousSubdomainRequest(tenant, "req-concurrent-$index")
                        }
                    async(Dispatchers.Default) {
                        start.await()
                        (0 until LOOKUPS).map { expected to didManager(session).get(DID).value.id }
                    }
                }
            start.complete(Unit)
            val mismatches = requests.awaitAll().flatten().filter { (expected, seen) -> expected != seen }
            assertTrue(mismatches.isEmpty(), "requests resolved another tenant's DID: $mismatches")
        }

    @Test
    fun requestWithoutTenantSeesNoTenantsDid(): Unit =
        runBlocking {
            val session =
                app.userContextManager
                    .getAnonymous()
                    .sessionContextManager
                    .createOrGetFromId("req-no-tenant", principalType = PrincipalType.ANONYMOUS)

            val lookup = didManager(session).get(DID)
            assertTrue(lookup.isErr, "a request without a tenant must not fall back to a tenant's DID: $lookup")
            assertEquals("NOT_FOUND_ERROR", lookup.error.code)
        }

    private fun anonymousSubdomainRequest(
        tenant: String,
        sessionId: String,
    ): SessionInstance =
        app.userContextManager
            .createOrGetFromInputs(DefaultTenantInputString(tenant), DefaultPrincipalInputString("visitor-$tenant"))
            .sessionContextManager
            .createOrGetFromId(sessionId, principalType = PrincipalType.ANONYMOUS)

    private fun tenantBRequest(sessionId: String): SessionInstance =
        app.userContextManager
            .createOrGetFromInputs(DefaultTenantInputString(TENANT_B), DefaultPrincipalInputString("operator-b"))
            .sessionContextManager
            .createOrGetFromId(sessionId, principalType = PrincipalType.USER)

    private fun didManager(session: SessionInstance): DidManager = (session.graph as DidManagerServiceImpl.Graph).didManager

    private fun request(session: SessionInstance): RequestAccess = session.graph as RequestAccess

    private suspend fun stored(tenant: String): DidDetail {
        val result = repository.findByDid(tenant, DID)
        assertTrue(result.isOk, "findByDid $tenant: $result")
        return assertNotNull(result.value, "seeded DID missing for $tenant")
    }

    private fun seed(
        tenant: String,
        recordId: String,
    ): DidDetail {
        val now = Clock.System.now()
        return DidDetail(
            record =
                DidRecord(
                    id = recordId,
                    tenantId = tenant,
                    did = DID,
                    method = "example",
                    role = DidRole.MANAGED,
                    createdAt = now,
                    updatedAt = now,
                ),
        )
    }

    private companion object {
        const val TENANT_A = "tenant-a"
        const val TENANT_B = "tenant-b"
        const val RECORD_A = "record-tenant-a"
        const val RECORD_B = "record-tenant-b"
        const val DID = "did:example:public"
        const val REQUESTS = 16
        const val LOOKUPS = 10
    }
}
