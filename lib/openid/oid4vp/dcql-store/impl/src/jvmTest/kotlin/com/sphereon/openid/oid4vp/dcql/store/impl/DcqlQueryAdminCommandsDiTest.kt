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

package com.sphereon.openid.oid4vp.dcql.store.impl

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.ClaimsPathPointer
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.dcql.store.command.CreateDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.CreateDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.DeleteDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.DeleteDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.GetDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.GetDcqlQueryServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.ListDcqlQueriesArgs
import com.sphereon.openid.oid4vp.dcql.store.command.ListDcqlQueriesServiceCommand
import com.sphereon.openid.oid4vp.dcql.store.command.UpdateDcqlQueryArgs
import com.sphereon.openid.oid4vp.dcql.store.command.UpdateDcqlQueryServiceCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DI-graph test for the DCQL query configuration store and its admin ServiceCommands.
 *
 * This composes the real Metro graph (validating every `@ContributesBinding` for the
 * store and the five command impls resolves at the correct scope), then exercises CRUD,
 * conflict/not-found handling, and per-tenant isolation through the resolved commands.
 */
class DcqlQueryAdminCommandsDiTest {
    private lateinit var app: DcqlStoreJvmTestAppGraph

    @BeforeTest
    fun setup() {
        app = createDcqlStoreJvmTestAppGraph(this)
    }

    @AfterTest
    fun tearDown() {
        app.destroy()
    }

    private fun sessionForTenant(
        tenantId: String,
        sessionId: String,
    ): DcqlStoreTestSessionGraph {
        val tenant =
            object : TenantContextData {
                override val tenantId: String = tenantId
            }
        val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-$tenantId")
        val session: SessionInstance = user.createSession(sessionId = sessionId, makeActive = true)
        return session.graph as DcqlStoreTestSessionGraph
    }

    private fun sampleQuery(credentialId: String = "identity_credential"): DcqlQuery =
        DcqlQuery(
            credentials =
                listOf(
                    DcqlCredentialQuery(
                        id = credentialId,
                        format = "dc+sd-jwt",
                        meta = sdJwtVcMeta("urn:test:$credentialId"),
                        claims = listOf(DcqlClaimQuery(path = ClaimsPathPointer(listOf(JsonPrimitive("first_name"))))),
                    ),
                ),
        )

    @Test
    fun `create then get round-trips the configuration`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            val createResult =
                graph.createCommand.execute(
                    CreateDcqlQueryArgs(
                        queryId = "einvoice",
                        name = "eInvoice verification",
                        description = "Verify the eInvoice credential",
                        dcqlQuery = sampleQuery(),
                    ),
                )
            assertTrue(createResult.isOk, "create should succeed")
            assertEquals("einvoice", createResult.value.queryId)
            assertTrue(createResult.value.createdAt > 0, "store stamps createdAt")

            val getResult = graph.getCommand.execute(GetDcqlQueryArgs("einvoice"))
            assertTrue(getResult.isOk, "get should succeed")
            assertEquals("eInvoice verification", getResult.value.name)
        }

    @Test
    fun `create rejects a duplicate query id`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            val args =
                CreateDcqlQueryArgs(
                    queryId = "duplicate",
                    name = "First",
                    dcqlQuery = sampleQuery(),
                )
            assertTrue(graph.createCommand.execute(args).isOk)

            val second = graph.createCommand.execute(args.copy(name = "Second"))
            assertTrue(second.isErr, "second create with same query id must fail")
        }

    @Test
    fun `get and update fail for an unknown query id`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            assertTrue(graph.getCommand.execute(GetDcqlQueryArgs("missing")).isErr)
            assertTrue(
                graph.updateCommand.execute(UpdateDcqlQueryArgs(queryId = "missing", name = "x")).isErr,
            )
        }

    @Test
    fun `update merges only the supplied fields`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            graph.createCommand
                .execute(
                    CreateDcqlQueryArgs(
                        queryId = "merge-me",
                        name = "Original name",
                        description = "Original description",
                        dcqlQuery = sampleQuery(),
                        enabled = true,
                    ),
                ).also { assertTrue(it.isOk) }

            val updated =
                graph.updateCommand.execute(
                    UpdateDcqlQueryArgs(queryId = "merge-me", enabled = false),
                )
            assertTrue(updated.isOk)
            assertEquals("Original name", updated.value.name, "name unchanged when null")
            assertEquals("Original description", updated.value.description, "description unchanged when null")
            assertFalse(updated.value.enabled, "enabled flipped to false")
        }

    @Test
    fun `list returns all configurations for the tenant and delete removes one`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            graph.createCommand.execute(CreateDcqlQueryArgs(queryId = "q1", name = "Q1", dcqlQuery = sampleQuery()))
            graph.createCommand.execute(CreateDcqlQueryArgs(queryId = "q2", name = "Q2", dcqlQuery = sampleQuery()))

            val listed = graph.listCommand.execute(ListDcqlQueriesArgs())
            assertTrue(listed.isOk)
            assertEquals(listOf("q1", "q2"), listed.value.map { it.queryId })

            val deleted = graph.deleteCommand.execute(DeleteDcqlQueryArgs("q1"))
            assertTrue(deleted.isOk)
            assertTrue(deleted.value, "delete reports true for an existing entry")

            val afterDelete = graph.listCommand.execute(ListDcqlQueriesArgs())
            assertEquals(listOf("q2"), afterDelete.value.map { it.queryId })
        }

    @Test
    fun `delete of an unknown query id reports false`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            val deleted = graph.deleteCommand.execute(DeleteDcqlQueryArgs("never-existed"))
            assertTrue(deleted.isOk)
            assertFalse(deleted.value)
        }

    @Test
    fun `configurations are isolated per tenant`() =
        runTest {
            val tenantA = sessionForTenant("tenant-a", "session-a")
            val tenantB = sessionForTenant("tenant-b", "session-b")

            tenantA.createCommand
                .execute(CreateDcqlQueryArgs(queryId = "shared-id", name = "A's query", dcqlQuery = sampleQuery()))
                .also { assertTrue(it.isOk) }

            // Tenant B does not see tenant A's entry.
            assertTrue(tenantB.getCommand.execute(GetDcqlQueryArgs("shared-id")).isErr)
            assertTrue(
                tenantB.listCommand
                    .execute(ListDcqlQueriesArgs())
                    .value
                    .isEmpty()
            )

            // Tenant B can create the same query id independently.
            assertTrue(
                tenantB.createCommand
                    .execute(CreateDcqlQueryArgs(queryId = "shared-id", name = "B's query", dcqlQuery = sampleQuery()))
                    .isOk,
            )
            assertEquals(
                "A's query",
                tenantA.getCommand
                    .execute(GetDcqlQueryArgs("shared-id"))
                    .value.name
            )
            assertEquals(
                "B's query",
                tenantB.getCommand
                    .execute(GetDcqlQueryArgs("shared-id"))
                    .value.name
            )
        }

    @Test
    fun `store binding is reachable directly from the session graph`() =
        runTest {
            val graph = sessionForTenant("tenant-a", "session-a")
            // The same store the commands delegate to is resolvable as a SessionScope binding.
            assertNull(graph.dcqlQueryConfigurationStore.getByQueryId("nothing").value)
        }
}

/**
 * Session-scope graph extension exposing the DCQL store bindings under test.
 *
 * Metro merges this into the real session graph, so casting `SessionInstance.graph`
 * to this interface yields the actual `@ContributesBinding` instances.
 */
@ContributesTo(SessionScope::class)
interface DcqlStoreTestSessionGraph {
    val dcqlQueryConfigurationStore: DcqlQueryConfigurationStore
    val createCommand: CreateDcqlQueryServiceCommand
    val getCommand: GetDcqlQueryServiceCommand
    val listCommand: ListDcqlQueriesServiceCommand
    val updateCommand: UpdateDcqlQueryServiceCommand
    val deleteCommand: DeleteDcqlQueryServiceCommand
}

@DependencyGraph(AppScope::class)
abstract class DcqlStoreJvmTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): DcqlStoreJvmTestAppGraph
    }
}

fun createDcqlStoreJvmTestAppGraph(
    application: Any,
    appId: String = "com.sphereon.openid.oid4vp.dcql.store.test",
    profile: String = "test",
    version: String = "0.0.1-TEST",
): DcqlStoreJvmTestAppGraph {
    val graph =
        createGraphFactory<DcqlStoreJvmTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
