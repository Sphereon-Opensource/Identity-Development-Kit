/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.did.manager.impl.command

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.command.CreateDidInput
import com.sphereon.did.manager.command.CreateDidServiceCommand
import com.sphereon.did.manager.command.DidCreateKeyMaterial
import com.sphereon.did.manager.command.DidCreateKeyMaterialKind
import com.sphereon.did.manager.impl.publicJwk
import com.sphereon.did.manager.impl.testutil.createDidManagerTestAppGraph
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PublicJwkDidCreateCommandTest {
    @ContributesTo(SessionScope::class)
    interface CommandGraph {
        val createDid: CreateDidServiceCommand
    }

    private lateinit var app: AppGraph
    private lateinit var command: CreateDidServiceCommand

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "public-jwk-command-test",
            ),
        )
        app = createDidManagerTestAppGraph(this)
        app.userContextManager.destroyAll()
        command =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("public-jwk-create-command", principalType = com.sphereon.di.context.PrincipalType.USER)
                .graph
                .let { it as CommandGraph }
                .createDid
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) app.userContextManager.destroyAll()
    }

    @Test
    fun publicJwkCreateDoesNotRequireRegisteredKmsKey() =
        runTest {
            val created =
                command
                    .execute(
                        CreateDidInput(
                            method = "jwk",
                            keyInfo =
                                DidCreateKeyMaterial(
                                    kind = DidCreateKeyMaterialKind.PUBLIC_JWK,
                                    publicJwk = publicJwk(),
                                ),
                        ),
                    ).getOrThrow()

            assertTrue(created.did.startsWith("did:jwk:"))
            assertEquals(1, created.document?.verificationMethod?.size)
        }

    @Test
    fun createKeyMaterialRejectsBlankKidAndPrivateMembers() {
        assertFailsWith<IllegalArgumentException> {
            DidCreateKeyMaterial(
                kind = DidCreateKeyMaterialKind.PUBLIC_JWK,
                publicJwk = publicJwk().copy(kid = " "),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DidCreateKeyMaterial(
                kind = DidCreateKeyMaterialKind.PUBLIC_JWK,
                publicJwk = publicJwk().copy(d = "private-scalar"),
            )
        }
    }
}
