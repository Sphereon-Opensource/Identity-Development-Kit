/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.did.manager.impl

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.impl.testutil.createDidManagerTestAppGraph
import com.sphereon.did.persistence.DidRepository
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicJwkDidManagerCreateTest {
    @ContributesTo(SessionScope::class)
    interface ManagerGraph {
        val didManager: DidManager
        val didRepository: DidRepository
    }

    private lateinit var app: AppGraph
    private lateinit var manager: DidManager
    private lateinit var repository: DidRepository

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "public-jwk-manager-test",
            ),
        )
        app = createDidManagerTestAppGraph(this)
        app.userContextManager.destroyAll()
        val graph =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("public-jwk-create-manager", principalType = com.sphereon.di.context.PrincipalType.USER)
                .graph as ManagerGraph
        manager = graph.didManager
        repository = graph.didRepository
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) app.userContextManager.destroyAll()
    }

    @Test
    fun onePublicVmPersistsWithoutKmsBindingOrKeyMapping() =
        runTest {
            val created =
                manager
                    .create(
                        DidCreateOptions(
                            method = "jwk",
                            publicKeyJwk = publicJwk(),
                            verificationMethods = emptyList(),
                        ),
                    ).getOrThrow()
            val stored = repository.findByDid(tenantId = null, did = created.did).getOrThrow()
            requireNotNull(stored)

            assertEquals(1, stored.verificationMethod.size)
            assertTrue(stored.keyMapping.isEmpty())
            stored.verificationMethod.single().let { vm ->
                assertNull(vm.kmsProviderId)
                assertNull(vm.kmsKeyAlias)
                assertNull(vm.kmsKid)
                assertNull(vm.keyReferenceId)
            }
        }
}

