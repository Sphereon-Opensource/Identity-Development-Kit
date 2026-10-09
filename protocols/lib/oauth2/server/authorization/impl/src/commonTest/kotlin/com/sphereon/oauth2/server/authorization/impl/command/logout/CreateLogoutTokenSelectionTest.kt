/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.logout

import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.RecordingJwtService
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.AsSigningRequirement
import com.sphereon.oauth2.server.authorization.signing.AsSigningSelection
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.CancellationException
import kotlin.test.assertFailsWith
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.TenantContextData
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider

class CreateLogoutTokenSelectionTest {
    private val ctx = OAuth2ServerTestContext("logout-captured-selection-test", this)
    private val issuerB = "https://b.example/oidc"
    private val provider = TestOAuth2ServersConfigProvider(
        OAuth2ServersConfig(
            defaultServer = "A",
            servers = mapOf(
                "A" to OAuth2ServerInstanceConfig(issuer = "https://ambient-a.example/oidc"),
                "B" to OAuth2ServerInstanceConfig(issuer = issuerB),
            ),
        ),
    )
    private val descriptor = ManagedOptsKeyInfo(
        identifier = KeyInfo<KeyType>(
            alias = "tenant-signing-alias",
            kid = "tenant-signing-kid",
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        ),
    )

    @Test
    fun routedBSignsWithExactCapturedConfigRatherThanAmbientA() = runTest {
        var capturedKey: String? = null
        val resolver = object : AsServerSigningIdentifierResolver {
            override suspend fun selectSigning(
                captured: CapturedAsServerConfig,
                requirement: AsSigningRequirement,
                requestedAlgorithm: String?,
            ): AsSigningSelection {
                capturedKey = captured.serverKey
                assertEquals(AsSigningRequirement.REQUIRED, requirement)
                return AsSigningSelection(descriptor, setOf("ES256"))
            }
        }
        val delegate = RecordingJwtService(mintedKid = "tenant-signing-kid")
        val recorder = object : JwtService by delegate {
            var signed: CreateJwsArgs? = null
            override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
                signed = args
                return delegate.createJwsCompact(args)
            }
        }
        val command = CreateLogoutTokenCommandImpl(
            execution = ctx.execution,
            jwtService = recorder,
            signingIdentifierResolver = resolver,
            configProvider = provider,
            asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
                override fun currentAsInstanceId(): String = "B"
            },
            secureRandom = defaultSecureRandom(),
        )
        val result = command.execute(CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user"))
        assertTrue(result.isOk)
        assertEquals("B", capturedKey)
        assertEquals("tenant-signing-kid", recorder.signed?.opts?.protectedHeader?.get("kid")?.toString()?.trim('"'))
        assertTrue(recorder.signed?.payload.toString().contains("\"iss\":\"$issuerB\""))
    }

    @Test
    fun missingTrustedRoutedBindingFailsBeforeSigning() = runTest {
        var resolverCalls = 0
        val resolver = object : AsServerSigningIdentifierResolver {
            override suspend fun selectSigning(
                captured: CapturedAsServerConfig,
                requirement: AsSigningRequirement,
                requestedAlgorithm: String?,
            ): AsSigningSelection {
                resolverCalls++
                return AsSigningSelection(descriptor, setOf("ES256"))
            }
        }
        val delegate = RecordingJwtService(mintedKid = "tenant-signing-kid")
        val recorder = object : JwtService by delegate {
            var signed: CreateJwsArgs? = null
            override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
                signed = args
                return delegate.createJwsCompact(args)
            }
        }
        val command = CreateLogoutTokenCommandImpl(
            execution = ctx.execution,
            jwtService = recorder,
            signingIdentifierResolver = resolver,
            configProvider = provider,
            asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
                override fun currentAsInstanceId(): String? = null
            },
            secureRandom = defaultSecureRandom(),
        )
        val result = command.execute(CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user"))
        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolverCalls)
        assertNull(recorder.signed)
    }

    private class CountingJwtService(
        private val delegate: RecordingJwtService = RecordingJwtService(mintedKid = "tenant-signing-kid"),
    ) : JwtService by delegate {
        var signingCalls = 0
        var signed: CreateJwsArgs? = null

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            signingCalls++
            signed = args
            return delegate.createJwsCompact(args)
        }
    }

    private class CountingSelection(
        private val selection: AsSigningSelection,
        private val cancellation: CancellationException? = null,
    ) : AsServerSigningIdentifierResolver {
        var calls = 0

        override suspend fun selectSigning(
            captured: CapturedAsServerConfig,
            requirement: AsSigningRequirement,
            requestedAlgorithm: String?,
        ): AsSigningSelection {
            calls++
            assertEquals("B", captured.serverKey)
            assertEquals(AsSigningRequirement.REQUIRED, requirement)
            cancellation?.let { throw it }
            return selection
        }
    }

    private fun commandFor(
        routedServer: OAuth2ServerInstanceConfig,
        resolver: AsServerSigningIdentifierResolver,
        jwtService: JwtService,
    ): CreateLogoutTokenCommandImpl = commandFor(
        routedServer = routedServer,
        resolver = resolver,
        jwtService = jwtService,
        execution = ctx.execution,
        configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                defaultServer = "A",
                servers = mapOf(
                    "A" to OAuth2ServerInstanceConfig(issuer = "https://ambient-a.example/oidc"),
                    "B" to routedServer,
                ),
            ),
        ),
    )

    private fun commandFor(
        routedServer: OAuth2ServerInstanceConfig,
        resolver: AsServerSigningIdentifierResolver,
        jwtService: JwtService,
        execution: SessionExecution,
        configProvider: OAuth2ServersConfigProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                defaultServer = "A",
                servers = mapOf(
                    "A" to OAuth2ServerInstanceConfig(issuer = "https://ambient-a.example/oidc"),
                    "B" to routedServer,
                ),
            ),
        ),
    ): CreateLogoutTokenCommandImpl = CreateLogoutTokenCommandImpl(
        execution = execution,
        jwtService = jwtService,
        signingIdentifierResolver = resolver,
        configProvider = configProvider,
        asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
            override fun currentAsInstanceId(): String = "B"
        },
        secureRandom = defaultSecureRandom(),
    )

    @Test
    fun routedServerWithoutIssuerOrTemplateRejectsCallerIssuerBeforeSelection() = runTest {
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(OAuth2ServerInstanceConfig(), resolver, jwt)

        val result = command.execute(CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user"))

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun blankConfiguredIssuerAndBlankCallerRejectBeforeSelection() = runTest {
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(OAuth2ServerInstanceConfig(issuer = ""), resolver, jwt)

        val result = command.execute(CreateLogoutTokenArgs(issuer = "", clientId = "rp", sub = "user"))

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun exactTrailingSlashIssuerIsPreservedWhenRoutedConfigMatches() = runTest {
        val issuer = "$issuerB/"
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(OAuth2ServerInstanceConfig(issuer = issuer), resolver, jwt)

        val result = command.execute(CreateLogoutTokenArgs(issuer = issuer, clientId = "rp", sub = "user"))

        assertTrue(result.isOk)
        assertEquals(1, resolver.calls)
        assertEquals(1, jwt.signingCalls)
        assertTrue(jwt.signed?.payload.toString().contains("\"iss\":\"$issuer\""))
    }

    @Test
    fun trailingSlashIssuerMismatchRejectsBeforeSelection() = runTest {
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(OAuth2ServerInstanceConfig(issuer = "$issuerB/"), resolver, jwt)

        val result = command.execute(CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user"))

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun resolverCancellationEscapesTheCommandBeforeJwsSigning() = runTest {
        val cancellation = CancellationException("cancel routed logout selection")
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")), cancellation)
        val jwt = CountingJwtService()
        val command = commandFor(OAuth2ServerInstanceConfig(issuer = issuerB), resolver, jwt)

        val escaped = assertFailsWith<CancellationException> {
            command.execute(CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user"))
        }

        val causeChain = mutableListOf<Throwable>()
        var current: Throwable? = escaped
        while (true) {
            val cause = current ?: break
            if (causeChain.any { it === cause }) break
            causeChain += cause
            current = cause.cause
        }
        assertTrue(causeChain.any { it === cancellation }, "injected cancellation must escape directly or via recovery")
        assertEquals(1, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun templateOnlyIssuerUsesTrustedSessionTenantFromCapturedRoutedServer() = runTest {
        val tenantId = "logout-template-tenant"
        val execution = executionForTenant(tenantId)
        assertEquals(tenantId, execution.tenantId)
        val expectedIssuer = "https://${execution.tenantId}.example/as/"
        val provider = CountingConfigProvider(
            OAuth2ServersConfig(
                defaultServer = "A",
                servers = mapOf(
                    "A" to OAuth2ServerInstanceConfig(issuer = "https://ambient-a.example/oidc"),
                    "B" to OAuth2ServerInstanceConfig(issuerTemplate = "https://{tenant-id}.example/as/"),
                ),
            ),
        )
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(
            routedServer = OAuth2ServerInstanceConfig(issuerTemplate = "https://{tenant-id}.example/as/"),
            resolver = resolver,
            jwtService = jwt,
            execution = execution,
            configProvider = provider,
        )

        val result = command.execute(CreateLogoutTokenArgs(issuer = expectedIssuer, clientId = "rp", sub = "user"))

        assertTrue(result.isOk)
        assertEquals(1, provider.configReads)
        assertEquals(0, provider.serverReads)
        assertEquals(0, provider.defaultServerReads)
        assertEquals(0, provider.issuerResolutionCalls)
        assertEquals(1, resolver.calls)
        assertEquals(1, jwt.signingCalls)
        assertTrue(jwt.signed?.payload.toString().contains("\"iss\":\"$expectedIssuer\""))
    }

    @Test
    fun templateIssuerForDifferentTenantRejectsBeforeSelectionAndSigning() = runTest {
        val execution = executionForTenant("logout-template-tenant")
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(
            routedServer = OAuth2ServerInstanceConfig(issuerTemplate = "https://{tenant-id}.example/as/"),
            resolver = resolver,
            jwtService = jwt,
            execution = execution,
        )

        val result = command.execute(
            CreateLogoutTokenArgs(issuer = "https://other-tenant.example/as/", clientId = "rp", sub = "user"),
        )

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun explicitIssuerTakesPrecedenceOverDifferentTemplate() = runTest {
        val tenantId = "logout-template-tenant"
        val execution = executionForTenant(tenantId)
        val explicitIssuer = "https://explicit.example/as/"
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(
            routedServer = OAuth2ServerInstanceConfig(
                issuer = explicitIssuer,
                issuerTemplate = "https://wrong-{tenant-id}.example/as/",
            ),
            resolver = resolver,
            jwtService = jwt,
            execution = execution,
        )

        val result = command.execute(CreateLogoutTokenArgs(issuer = explicitIssuer, clientId = "rp", sub = "user"))

        assertTrue(result.isOk)
        assertEquals(1, resolver.calls)
        assertEquals(1, jwt.signingCalls)
        assertTrue(jwt.signed?.payload.toString().contains("\"iss\":\"$explicitIssuer\""))
    }

    @Test
    fun blankExplicitIssuerDoesNotFallThroughToValidTemplate() = runTest {
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(
            routedServer = OAuth2ServerInstanceConfig(
                issuer = "",
                issuerTemplate = "https://{tenant-id}.example/as/",
            ),
            resolver = resolver,
            jwtService = jwt,
            execution = executionForTenant("logout-template-tenant"),
        )

        val result = command.execute(
            CreateLogoutTokenArgs(issuer = "https://logout-template-tenant.example/as/", clientId = "rp", sub = "user"),
        )

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun blankTemplateResultRejectsBeforeSelectionAndSigning() = runTest {
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(
            routedServer = OAuth2ServerInstanceConfig(issuerTemplate = ""),
            resolver = resolver,
            jwtService = jwt,
            execution = executionForTenant("logout-template-tenant"),
        )

        val result = command.execute(CreateLogoutTokenArgs(issuer = "https://unused.example/as/", clientId = "rp", sub = "user"))

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun anonymousExecutionCannotResolveIssuerTemplate() = runTest {
        assertEquals(IdentityConstants.ANONYMOUS_TENANT_ID, ctx.execution.tenantId)
        val resolver = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val jwt = CountingJwtService()
        val command = commandFor(
            routedServer = OAuth2ServerInstanceConfig(issuerTemplate = "https://{tenant-id}.example/as/"),
            resolver = resolver,
            jwtService = jwt,
        )

        val result = command.execute(
            CreateLogoutTokenArgs(issuer = "https://anonymous.example/as/", clientId = "rp", sub = "user"),
        )

        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertEquals(0, resolver.calls)
        assertEquals(0, jwt.signingCalls)
        assertNull(jwt.signed)
    }

    @Test
    fun cancellationInTrustedTenantLookupEscapesBeforeRoutedSelection() = runTest {
        val explicitServer = OAuth2ServerInstanceConfig(issuer = issuerB)
        val healthySelection = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
        val healthyJwt = CountingJwtService()
        val healthy = commandFor(explicitServer, healthySelection, healthyJwt)
            .execute(CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user"))
        assertTrue(healthy.isOk, healthy.toString())
        assertEquals(1, healthySelection.calls)
        assertEquals(1, healthyJwt.signingCalls)

        class TenantReadFailureExecution(
            private val base: SessionExecution,
            private val failure: Exception,
        ) : SessionExecution by base {
            var tenantReads = 0
            override val tenantId: String
                get() {
                    tenantReads++
                    // The pipeline context reads first; this must reach the command's capture.
                    if (tenantReads == 2) throw failure
                    return base.tenantId
                }
        }

        val ordinaryFailure = IllegalStateException("trusted tenant lookup unavailable")
        val cancellation = CancellationException("trusted tenant lookup cancelled")
        for (failure in listOf(ordinaryFailure, cancellation)) {
            val throwingExecution = TenantReadFailureExecution(ctx.execution, failure)
            val config = CountingConfigProvider(
                OAuth2ServersConfig(
                    defaultServer = "A",
                    servers = mapOf(
                        "A" to OAuth2ServerInstanceConfig(issuer = "https://ambient-a.example/oidc"),
                        "B" to explicitServer,
                    ),
                ),
            )
            val selection = CountingSelection(AsSigningSelection(descriptor, setOf("ES256")))
            val jwt = CountingJwtService()
            var routedReads = 0
            val command = CreateLogoutTokenCommandImpl(
                execution = throwingExecution,
                jwtService = jwt,
                signingIdentifierResolver = selection,
                configProvider = config,
                asInstanceIdProvider = object : OAuth2ServerInstanceIdProvider {
                    override fun currentAsInstanceId(): String {
                        routedReads++
                        return "B"
                    }
                },
                secureRandom = defaultSecureRandom(),
            )
            val args = CreateLogoutTokenArgs(issuer = issuerB, clientId = "rp", sub = "user")

            if (failure === ordinaryFailure) {
                val tolerated = command.execute(args)
                assertTrue(tolerated.isOk, "ordinary tenant lookup failure must reach explicit-issuer signing: $tolerated")
                assertEquals(2, throwingExecution.tenantReads)
                assertEquals(1, routedReads)
                assertEquals(1, config.configReads)
                assertEquals(1, selection.calls)
                assertEquals(1, jwt.signingCalls)
                assertTrue(jwt.signed?.payload.toString().contains("\"iss\":\"$issuerB\""))
            } else {
                val escaped = assertFailsWith<CancellationException> { command.execute(args) }
                val causeChain = mutableListOf<Throwable>()
                var current: Throwable? = escaped
                while (true) {
                    val cause = current ?: break
                    if (causeChain.any { it === cause }) break
                    causeChain += cause
                    current = cause.cause
                }
                assertTrue(causeChain.any { it === cancellation }, "injected cancellation must escape directly or via recovery")
                assertEquals(2, throwingExecution.tenantReads)
                assertEquals(0, routedReads)
                assertEquals(0, config.configReads)
                assertEquals(0, selection.calls)
                assertEquals(0, jwt.signingCalls)
                assertNull(jwt.signed)
            }
            assertEquals(0, config.serverReads)
            assertEquals(0, config.defaultServerReads)
            assertEquals(0, config.issuerResolutionCalls)
        }
    }

    private fun executionForTenant(tenantId: String): SessionExecution {
        val trustedTenantId = tenantId
        val context =
            ctx.app.userContextManager.createOrGetFromData(
                tenantData = object : TenantContextData {
                    override val tenantId: String = trustedTenantId
                },
                principalValue = "logout-template-principal",
                makeActive = false,
            )
        return context.createSession("logout-template-session-$tenantId", makeActive = false)
            .asCoreApiServiceGraph()
            .serviceExecution
    }

    private class CountingConfigProvider(
        private val config: OAuth2ServersConfig,
    ) : OAuth2ServersConfigProvider {
        var configReads = 0
        var serverReads = 0
        var defaultServerReads = 0
        var issuerResolutionCalls = 0

        override fun getConfig(): OAuth2ServersConfig {
            configReads++
            return config
        }

        override fun getServer(id: String): OAuth2ServerInstanceConfig? {
            serverReads++
            return config.getServer(id)
        }

        override fun getDefaultServer(): OAuth2ServerInstanceConfig {
            defaultServerReads++
            return config.getDefaultServer()
        }

        override fun resolveIssuer(serverId: String, tenantId: String): String {
            issuerResolutionCalls++
            return error("Logout command must resolve issuer from its captured server config")
        }
    }
}
