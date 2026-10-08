/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.integration

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelector
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.AuthorizationResult
import com.sphereon.oauth2.client.client.DpopContext
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.client.OidcLoginApi
import com.sphereon.oauth2.client.client.OidcLoginInitiation
import com.sphereon.oauth2.client.command.AuthorizationResponseSource
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.client.command.OidcLoginResult
import com.sphereon.oauth2.client.impl.client.OAuth2ClientImpl
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.service.PkceService
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.IdTokenValidationOptions
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.common.model.ValidatedIdToken
import com.sphereon.oauth2.server.authorization.command.federation.AuthenticatedUserResult
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcome
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackOutcomeType
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserArgs
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserCommand
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackArgs
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleReconciliationOutcomeCommand
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.config.FederationMetadataResolver
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.config.ResolvedFederationProvider
import com.sphereon.oauth2.server.authorization.impl.command.federation.GetAuthenticatedUserCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.federation.GetUserInfoCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.federation.HandleFederationCallbackCommandImpl
import com.sphereon.oauth2.server.authorization.impl.config.DirectFederationMetadataResolver
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2FederationHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.provider.AbstractFederatedUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.impl.provider.DefaultEmptyFederationProviderRuntimeResolver
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRoute
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteBinding
import com.sphereon.oauth2.server.authorization.routing.AuthenticationRouteDecision
import com.sphereon.oauth2.server.authorization.storage.PendingAuthorizationSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Session-scoped graph view that exposes the [HttpAdapter] multibinding. The federation flow
 * integration test pulls the [OAuth2FederationHttpAdapter] off it instead of constructing one by
 * hand, so the assertion runs against the same wiring production uses.
 */
@ContributesTo(SessionScope::class)
interface FederationFlowAdaptersGraph {
    val httpAdapters: Map<String, Lazy<HttpAdapter>>
    val pendingAuthorizationSessionStore: PendingAuthorizationSessionStore
}

/**
 * Gates the federation chain against the real Metro graph. Drives `/reconciliation/authorize`
 * through the [OAuth2FederationHttpAdapter] resolved from the session graph (not a hand-constructed
 * adapter), with a test-only [OAuth2Client], exact-binding runtime resolver, and
 * [FederationMetadataResolver] contributed via `@ContributesBinding(replaces = ...)` so every
 * collaborator Metro needs to wire the federation provider has to be present in the graph.
 *
 * The probe: when the request carries `x-forwarded-proto: https` + `host: abc.ngrok.app` and
 * the configured server issuer is null, the base URL resolved by the adapter must follow those
 * headers, and the downstream upstream-IdP authorization URL must carry a matching
 * `redirect_uri`.
 */
class FederationFlowIntegrationTest {
    private val ctx = OAuth2IntegrationTestContext(this)
    private val adapter: OAuth2FederationHttpAdapter =
        (ctx.session.graph as FederationFlowAdaptersGraph)
            .httpAdapters[OAuth2FederationHttpAdapter.ID]
            ?.value as? OAuth2FederationHttpAdapter
            ?: error("OAuth2FederationHttpAdapter not present in the session graph")
    private val routeSelector = (ctx.app as HttpAdapterRouteSelector.Graph).httpAdapterRouteSelector
    private val dispatcher = (ctx.session.graph as HttpAdapterDispatcher.Graph).httpAdapterDispatcher
    private val capturingClient =
        (ctx.session.graph as CapturingOAuth2ClientGraph).capturingOAuth2Client
    private val registry = (ctx.session.graph as SessionScopedCommandRegistry.Graph).sessionScopedCommandRegistry
    private val userAuthProviders =
        (ctx.session.graph as UserAuthenticationProvidersGraph).userAuthenticationProviders
    private val federationFacade: AbstractFederatedUserAuthenticationProvider =
        userAuthProviders["federated"] as? AbstractFederatedUserAuthenticationProvider
            ?: error(
                "expected the 'federated' UserAuthenticationProvider multibinding entry to be " +
                    "${AbstractFederatedUserAuthenticationProvider::class.simpleName}, got " +
                    "${userAuthProviders["federated"]?.let { it::class.simpleName }}; " +
                    "registered keys=${userAuthProviders.keys}",
            )

    @Test
    fun capturingClientSharesTheSessionOAuth2ClientBinding() {
        val graph = ctx.session.graph as CapturingOAuth2ClientGraph
        assertSame<Any>(graph.oauth2Client, graph.capturingOAuth2Client)
    }

    @Test
    fun reconciliationAuthorizeHonoursForwardedProtoAndHost() =
        runTest {
            capturingClient.reset()
            storeDownstreamTransaction()

            // Clear `issuer` so the OAuth2 AS HTTP adapter's `resolveBaseUrl()` helper falls
            // back to the X-Forwarded-Proto + Host headers rather than short-circuiting to a
            // fixed configured issuer.
            val testProvider =
                (ctx.session.graph as OAuth2IntegrationSessionGraph).oauth2ServersConfigProvider
                    as TestOAuth2ServersConfigProvider
            testProvider.overrideServer(
                com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig(
                    mode = com.sphereon.oauth2.common.config.AuthorizationServerMode.HOSTED,
                    issuer = null,
                    oidc = com.sphereon.oauth2.common.config.FeaturePolicy.SUPPORTED,
                    idTokenSigningAlgValuesSupported = setOf("ES256"),
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/reconciliation/authorize",
                    queryParameters =
                        mapOf(
                            "oid4vp_session" to "oid4vp-xyz",
                            "session_id" to "oauth-downstream",
                            "provider" to FederationTestFixtures.PROVIDER_ID,
                        ),
                    headers =
                        mapOf(
                            "host" to "abc.ngrok.app",
                            "x-forwarded-proto" to "https",
                        ),
                )

            val selection = routeSelector.select(request.method, request.path)
            val route =
                (selection as? HttpAdapterRouteSelection.Selected)?.match
                    ?: error("Expected selected federation route for ${request.method} ${request.path}, got $selection")
            assertEquals(adapter.id, route.adapterId)
            val response = dispatcher.dispatch(request, route)

            assertEquals(
                302,
                response.statusCode,
                "reconciliation/authorize should 302 to the upstream IdP (graph wiring ok, provider found); " +
                    "body=${response.body}",
            )

            val capturedRedirect = capturingClient.capturedRedirectUri
            assertNotNull(capturedRedirect, "OAuth2Client.initiateAuthorization should have been called")
            assertEquals(
                "https://abc.ngrok.app${FederationTestFixtures.CALLBACK_PATH}",
                capturedRedirect,
                "redirectUri threaded into upstream authorization MUST honour X-Forwarded-Proto + Host",
            )
        }

    private suspend fun storeDownstreamTransaction(
        selectedBindingId: String? = FederationTestFixtures.PROVIDER_ID,
        expired: Boolean = false,
    ) {
        val now = Clock.System.now()
        val downstream =
            (ctx.session.graph as FederationFlowAdaptersGraph).pendingAuthorizationSessionStore.create(
                AuthorizationSession(
                    sessionId = "oauth-downstream",
                    clientId = "downstream-client",
                    state = "downstream-state",
                    nonce = "downstream-nonce",
                    responseType = "code",
                    redirectUri = "https://rp.test/callback",
                    createdAt = now,
                    expiresAt = if (expired) now - 1.minutes else now + 5.minutes,
                    authenticationRoute =
                        AuthenticationRouteDecision(
                            route = if (selectedBindingId == null) AuthenticationRoute.CHOOSER else AuthenticationRoute.UPSTREAM_REDIRECT,
                            hostedAuthorizationServerId = "22222222-2222-4222-8222-222222222222",
                            hostedAuthorizationServerRevision = 1,
                            localLoginAllowed = false,
                            selectedBindingId = selectedBindingId,
                            eligibleBindings =
                                listOf(
                                    AuthenticationRouteBinding(
                                        bindingId = FederationTestFixtures.PROVIDER_ID,
                                        upstreamResourceId = "33333333-3333-4333-8333-333333333333",
                                        displayName = "Fixture upstream",
                                        upstreamIssuer = FederationTestFixtures.ISSUER_URL,
                                        bindingRevision = 1,
                                        upstreamResourceRevision = 1,
                                        claimsMapping = emptyMap(),
                                    ),
                                ),
                        ),
                ),
            )
        assertTrue(downstream.isOk, "Downstream authorization transaction must be stored")
    }

    private fun reconciliationRequest(
        sessionId: String?,
        providerId: String = FederationTestFixtures.PROVIDER_ID,
    ) = GenericHttpRequest(
        method = "GET",
        path = "/reconciliation/authorize",
        queryParameters =
            buildMap {
                put("oid4vp_session", "oid4vp-xyz")
                put("provider", providerId)
                sessionId?.let { put("session_id", it) }
            },
    )

    @Test
    fun reconciliationRejectsMissingOAuthSession() =
        runTest {
            capturingClient.reset()
            val request = reconciliationRequest(null)
            val selected = routeSelector.select(request.method, request.path) as HttpAdapterRouteSelection.Selected
            val response = dispatcher.dispatch(request, selected.match)
            assertEquals(400, response.statusCode)
            assertNull(capturingClient.capturedRedirectUri)
        }

    @Test
    fun reconciliationRejectsUnknownOAuthSession() =
        runTest {
            capturingClient.reset()
            val request = reconciliationRequest("unknown-oauth-session")
            val selected = routeSelector.select(request.method, request.path) as HttpAdapterRouteSelection.Selected
            val response = dispatcher.dispatch(request, selected.match)
            assertEquals(400, response.statusCode)
            assertNull(capturingClient.capturedRedirectUri)
        }

    @Test
    fun reconciliationRejectsExpiredOAuthSession() =
        runTest {
            capturingClient.reset()
            storeDownstreamTransaction(expired = true)
            val request = reconciliationRequest("oauth-downstream")
            val selected = routeSelector.select(request.method, request.path) as HttpAdapterRouteSelection.Selected
            val response = dispatcher.dispatch(request, selected.match)
            assertEquals(409, response.statusCode)
            assertNull(capturingClient.capturedRedirectUri)
        }

    @Test
    fun reconciliationRejectsIneligibleProvider() =
        runTest {
            capturingClient.reset()
            storeDownstreamTransaction(selectedBindingId = null)
            val request = reconciliationRequest("oauth-downstream", "99999999-9999-4999-8999-999999999999")
            val selected = routeSelector.select(request.method, request.path) as HttpAdapterRouteSelection.Selected
            val response = dispatcher.dispatch(request, selected.match)
            assertEquals(400, response.statusCode)
            assertNull(capturingClient.capturedRedirectUri)
        }

    @Test
    fun federationServiceCommandsResolveFromRealSessionGraph() {
        val expectedIds =
            listOf(
                InitiateProviderAuthenticationCommand.COMMAND_ID,
                HandleFederationCallbackCommand.COMMAND_ID,
                ExchangeCodeAndExtractClaimsCommand.COMMAND_ID,
                HandleFederationOutcomeCommand.COMMAND_ID,
                HandleReconciliationOutcomeCommand.COMMAND_ID,
            )
        expectedIds.forEach { id ->
            val command = registry.get(id)
            assertNotNull(
                command,
                "command '$id' MUST be discoverable via SessionScopedCommandRegistry in the real graph",
            )
            assertEquals(
                id,
                command.commandId,
                "registry lookup must match command.commandId exactly (no hyphen/case drift)",
            )
        }
    }

    @Test
    fun facadeDelegatesHandleFederationCallbackToRegistryResolvedCommand() =
        runTest {
            val registered = registry.get(HandleFederationCallbackCommand.COMMAND_ID)
            assertNotNull(registered, "command MUST be registered in the real graph")
            assertTrue(
                registered is ReplacingHandleFederationCallbackCommand,
                "test-only replaces-binding must take over HandleFederationCallbackCommand; " +
                    "got ${registered::class.simpleName}",
            )
            val fake = registered as ReplacingHandleFederationCallbackCommand
            fake.reset()

            val result = federationFacade.handleFederationCallback(code = "CODE", state = "STATE")

            assertTrue(result.isOk, "facade delegation path must succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(1, fake.invocations, "facade must route through the fake exactly once")
            assertEquals("CODE", fake.lastCode, "facade must pass the HTTP `code` param verbatim")
            assertEquals("STATE", fake.lastState, "facade must pass the HTTP `state` param verbatim")
            assertEquals(FederationCallbackOutcomeType.FEDERATION_COMPLETE, result.value.outcomeType)
            assertEquals(CANNED_SESSION_ID, result.value.federation?.sessionId)
        }

    @Test
    fun getAuthenticatedUserCommandResolvesAndDelegatesFromFacade() =
        runTest {
            val registered = registry.get(GetAuthenticatedUserCommand.COMMAND_ID)
            assertNotNull(registered, "command MUST be registered in the real graph")
            assertTrue(
                registered is ReplacingGetAuthenticatedUserCommand,
                "test-only replaces-binding must take over GetAuthenticatedUserCommand; " +
                    "got ${registered::class.simpleName}",
            )
            val fake = registered as ReplacingGetAuthenticatedUserCommand
            fake.reset()

            val result = federationFacade.getAuthenticatedUser(sessionId = "session-abc")

            assertTrue(result.isOk, "facade delegation path must succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(1, fake.invocations, "facade must route through the fake exactly once")
            assertEquals("session-abc", fake.lastSessionId, "facade must pass sessionId verbatim")
            assertEquals(CANNED_USER_ID, result.value?.userId, "facade must surface the fake's user id unchanged")
            assertEquals(CANNED_ACR, result.value?.acr, "facade must surface the fake's acr unchanged")
        }

    @Test
    fun getUserInfoCommandResolvesAndDelegatesFromFacade() =
        runTest {
            val registered = registry.get(GetUserInfoCommand.COMMAND_ID)
            assertNotNull(registered, "command MUST be registered in the real graph")
            assertTrue(
                registered is ReplacingGetUserInfoCommand,
                "test-only replaces-binding must take over GetUserInfoCommand; " +
                    "got ${registered::class.simpleName}",
            )
            val fake = registered as ReplacingGetUserInfoCommand
            fake.reset()

            val result = federationFacade.getUserInfo(userId = "user-xyz")

            assertTrue(result.isOk, "facade delegation path must succeed: ${if (result.isErr) result.error else ""}")
            assertEquals(1, fake.invocations, "facade must route through the fake exactly once")
            assertEquals("user-xyz", fake.lastUserId, "facade must pass userId verbatim")
            assertEquals(CANNED_USER_ID, result.value.userId, "facade must surface the fake's UserInfo.userId unchanged")
            assertEquals(CANNED_USERNAME, result.value.username, "facade must surface the fake's username unchanged")
        }

    @Test
    fun facadeReturnsCommandsAuthenticationErrorUnchanged() =
        runTest {
            val fake =
                registry.get(GetUserInfoCommand.COMMAND_ID) as ReplacingGetUserInfoCommand
            fake.reset()
            fake.failNextWith = AuthenticationError.UserNotFound(description = "fake-not-found-marker")

            val result = federationFacade.getUserInfo(userId = "missing-user")

            assertTrue(result.isErr, "facade must propagate command failure")
            val err = result.error
            assertTrue(
                err is AuthenticationError.UserNotFound,
                "facade must surface the command's AuthenticationError variant directly; got ${err::class.simpleName}",
            )
            assertEquals(
                "fake-not-found-marker",
                err.description,
                "facade must pass the command's error description through unchanged",
            )
        }
}

@ContributesTo(com.sphereon.di.session.SessionScope::class)
interface UserAuthenticationProvidersGraph {
    val userAuthenticationProviders: Map<String, UserAuthenticationProvider>
}

private const val CANNED_SESSION_ID = "canned-session-from-fake-callback-command"
private const val CANNED_USER_ID = "canned-user-from-fake-command"
private const val CANNED_ACR = "canned-acr"
private const val CANNED_USERNAME = "canned-preferred-username"

/**
 * Test-only replacement for [HandleFederationCallbackCommandImpl] that records invocations and
 * returns a canned outcome. Proves the facade delegates: if `AbstractFederatedUserAuthenticationProvider`
 * still inlined the business logic, this replacement would never fire and the test would fail.
 */
@Inject
@SingleIn(com.sphereon.di.session.SessionScope::class)
@ContributesBinding(
    com.sphereon.di.session.SessionScope::class,
    binding = binding<HandleFederationCallbackCommand>(),
    replaces = [HandleFederationCallbackCommandImpl::class],
)
class ReplacingHandleFederationCallbackCommand(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<HandleFederationCallbackArgs, FederationCallbackOutcome, AuthenticationError>(
        commandId = HandleFederationCallbackCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleFederationCallbackArgs>(),
        outputTypeToken = typeToken<FederationCallbackOutcome>(),
    ),
    HandleFederationCallbackCommand {
    override val commandId: String get() = HandleFederationCallbackCommand.COMMAND_ID

    @Volatile var invocations: Int = 0
        private set

    @Volatile var lastCode: String? = null
        private set

    @Volatile var lastState: String? = null
        private set

    fun reset() {
        invocations = 0
        lastCode = null
        lastState = null
    }

    override suspend fun supports(args: Any): Boolean = args is HandleFederationCallbackArgs

    override suspend fun doExecute(
        args: HandleFederationCallbackArgs,
        applyDuring: (HandleFederationCallbackArgs) -> HandleFederationCallbackArgs,
    ): com.sphereon.core.api.IdkResult<FederationCallbackOutcome, AuthenticationError> {
        val applied = applyDuring(args)
        invocations += 1
        lastCode = applied.code
        lastState = applied.state
        return Ok(FederationCallbackOutcome.federationComplete(sessionId = CANNED_SESSION_ID))
    }
}

/**
 * Test-only replacement for [GetAuthenticatedUserCommandImpl] that records invocations and
 * returns a canned [AuthenticatedUser]. Proves the facade's `getAuthenticatedUser` delegates:
 * if the abstract base inlined the session-store read, this fake would never fire and the
 * delegation test would fail.
 */
@Inject
@SingleIn(com.sphereon.di.session.SessionScope::class)
@ContributesBinding(
    com.sphereon.di.session.SessionScope::class,
    binding = binding<GetAuthenticatedUserCommand>(),
    replaces = [GetAuthenticatedUserCommandImpl::class],
)
class ReplacingGetAuthenticatedUserCommand(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<GetAuthenticatedUserArgs, AuthenticatedUserResult, AuthenticationError>(
        commandId = GetAuthenticatedUserCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetAuthenticatedUserArgs>(),
        outputTypeToken = typeToken<AuthenticatedUserResult>(),
    ),
    GetAuthenticatedUserCommand {
    override val commandId: String get() = GetAuthenticatedUserCommand.COMMAND_ID

    @Volatile var invocations: Int = 0
        private set

    @Volatile var lastSessionId: String? = null
        private set

    fun reset() {
        invocations = 0
        lastSessionId = null
    }

    override suspend fun supports(args: Any): Boolean = args is GetAuthenticatedUserArgs

    override suspend fun doExecute(
        args: GetAuthenticatedUserArgs,
        applyDuring: (GetAuthenticatedUserArgs) -> GetAuthenticatedUserArgs,
    ): IdkResult<AuthenticatedUserResult, AuthenticationError> {
        val applied = applyDuring(args)
        invocations += 1
        lastSessionId = applied.sessionId
        return Ok(
            AuthenticatedUserResult(
                user =
                    AuthenticatedUser(
                        userId = CANNED_USER_ID,
                        authenticatedAt =
                            kotlin.time.Clock.System
                                .now(),
                        authenticationMethod = AuthenticationMethod.OAUTH,
                        acr = CANNED_ACR,
                        amr = listOf("fed"),
                    ),
            ),
        )
    }
}

/**
 * Test-only replacement for [GetUserInfoCommandImpl] (federation flavour) that records
 * invocations and either returns a canned [UserInfo] or fails with an injected
 * [AuthenticationError]. The failure path lets the integration test verify the facade
 * surfaces the command's error variant directly without rewrapping.
 */
@Inject
@SingleIn(com.sphereon.di.session.SessionScope::class)
@ContributesBinding(
    com.sphereon.di.session.SessionScope::class,
    binding = binding<GetUserInfoCommand>(),
    replaces = [GetUserInfoCommandImpl::class],
)
class ReplacingGetUserInfoCommand(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<GetUserInfoArgs, UserInfo, AuthenticationError>(
        commandId = GetUserInfoCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetUserInfoArgs>(),
        outputTypeToken = typeToken<UserInfo>(),
    ),
    GetUserInfoCommand {
    override val commandId: String get() = GetUserInfoCommand.COMMAND_ID

    @Volatile var invocations: Int = 0
        private set

    @Volatile var lastUserId: String? = null
        private set

    @Volatile var failNextWith: AuthenticationError? = null

    fun reset() {
        invocations = 0
        lastUserId = null
        failNextWith = null
    }

    override suspend fun supports(args: Any): Boolean = args is GetUserInfoArgs

    override suspend fun doExecute(
        args: GetUserInfoArgs,
        applyDuring: (GetUserInfoArgs) -> GetUserInfoArgs,
    ): IdkResult<UserInfo, AuthenticationError> {
        val applied = applyDuring(args)
        invocations += 1
        lastUserId = applied.userId
        val failure = failNextWith
        if (failure != null) {
            failNextWith = null
            return Err(failure)
        }
        return Ok(
            UserInfo(
                userId = CANNED_USER_ID,
                username = CANNED_USERNAME,
            ),
        )
    }
}

private object FederationTestFixtures {
    const val PROVIDER_ID = "11111111-1111-4111-8111-111111111111"
    const val CALLBACK_PATH = "/federation/callback"
    const val ISSUER_URL = "https://upstream.idp.test"

    val providerConfig =
        FederationProviderConfig(
            id = PROVIDER_ID,
            name = "Keycloak",
            issuerUrl = ISSUER_URL,
            clientId = "vdx-sts",
            callbackPath = CALLBACK_PATH,
        )

    val metadata =
        AuthorizationServerMetadata(
            issuer = ISSUER_URL,
            tokenEndpoint = "$ISSUER_URL/token",
            authorizationEndpoint = "$ISSUER_URL/authorize",
        )
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<FederationProviderRuntimeResolver>(),
    replaces = [DefaultEmptyFederationProviderRuntimeResolver::class],
)
class TestFederationProviderRuntimeResolver : FederationProviderRuntimeResolver {
    override suspend fun resolve(bindingId: String): IdkResult<FederationProviderConfig, AuthenticationError> =
        FederationTestFixtures.providerConfig.takeIf { it.id == bindingId }?.let(::Ok)
            ?: Err(AuthenticationError.Generic(description = "not found"))

    override suspend fun listEnabled(): IdkResult<List<FederationProviderConfig>, AuthenticationError> = Ok(listOf(FederationTestFixtures.providerConfig))

    override suspend fun clientAuthentication(
        bindingId: String,
        audience: String,
    ): IdkResult<ClientAuthenticationConfig, AuthenticationError> = Ok(ClientAuthenticationConfig.None(FederationTestFixtures.providerConfig.clientId))
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<FederationMetadataResolver>(),
    replaces = [DirectFederationMetadataResolver::class],
)
class TestFederationMetadataResolver : FederationMetadataResolver {
    override suspend fun resolve(providerConfig: FederationProviderConfig): IdkResult<AuthorizationServerMetadata, IdkError> = Ok(FederationTestFixtures.metadata)

    override suspend fun invalidate(providerConfig: FederationProviderConfig) = Unit

    override suspend fun findByIssuer(issuer: String): ResolvedFederationProvider? = null
}

interface CapturedOAuth2Authorization {
    val capturedRedirectUri: String?

    fun reset()
}

/**
 * Captures the `redirectUri` arg passed to [OAuth2Client.initiateAuthorization]. Replaces the
 * default [OAuth2ClientImpl] at session scope so the federation flow routes through this
 * instance and the test can assert the exact value the adapter threaded downstream.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<OAuth2Client>(),
    replaces = [OAuth2ClientImpl::class],
)
@ContributesBinding(SessionScope::class, binding = binding<CapturedOAuth2Authorization>())
class CapturingOAuth2Client(
    private val pkceService: PkceService,
) : OAuth2Client,
    CapturedOAuth2Authorization {
    @Volatile
    private var redirectUri: String? = null

    override val capturedRedirectUri: String? get() = redirectUri

    override fun reset() {
        redirectUri = null
    }

    override val oidcLogin: OidcLoginApi
        get() = error("CapturingOAuth2Client.oidcLogin: not used in federation flow test")

    override suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError> = Ok(FederationTestFixtures.metadata)

    override fun isDpopSupported(authorizationServerMetadata: AuthorizationServerMetadata): Boolean = false

    override suspend fun initiateAuthorization(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scope: String?,
        state: String?,
        resource: List<String>?,
        clientAuthentication: ClientAuthenticationConfig?,
        dpopContext: DpopContext?,
        additionalParameters: Map<String, String>,
    ): IdkResult<AuthorizationResult, IdkError> {
        this.redirectUri = redirectUri
        val pkce = pkceService.createPkce(CreatePkceArgs())
        if (pkce.isErr) return Err(pkce.error)
        return Ok(
            AuthorizationResult(
                authorizationUrl = "${FederationTestFixtures.ISSUER_URL}/authorize?redirect_uri=$redirectUri&state=$state",
                pkceData = pkce.value,
                state = state,
            ),
        )
    }

    override suspend fun initiateOidcLogin(
        issuer: String,
        clientId: String,
        redirectUri: String,
        scopes: Set<String>,
        responseMode: OAuth2ResponseMode,
        prompt: String?,
        loginHint: String?,
        tenantId: String?,
        resource: String?,
        audience: String?,
        ownerHandleDigest: String?,
        grantBinding: String?,
        clientCorrelation: String?,
    ): IdkResult<OidcLoginInitiation, IdkError> = error("CapturingOAuth2Client.initiateOidcLogin: not used in federation flow test")

    override suspend fun initiateOidcLogin(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientId: String,
        redirectUri: String,
        scopes: Set<String>,
        responseMode: OAuth2ResponseMode,
        prompt: String?,
        loginHint: String?,
        tenantId: String?,
        resource: String?,
        audience: String?,
        ownerHandleDigest: String?,
        grantBinding: String?,
        clientCorrelation: String?,
    ): IdkResult<OidcLoginInitiation, IdkError> = error("CapturingOAuth2Client.initiateOidcLogin(metadata): not used in federation flow test")

    override suspend fun parseAuthorizationResponse(redirectUrl: String): IdkResult<AuthorizationResponse, IdkError> =
        error("CapturingOAuth2Client.parseAuthorizationResponse: not used in federation flow test")

    override suspend fun exchangeAuthorizationCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        authorizationCode: String,
        redirectUri: String,
        pkceData: PkceData?,
        resource: List<String>?,
        dpopContext: DpopContext?,
        audience: List<String>?,
    ): IdkResult<TokenResponse, IdkError> = error("CapturingOAuth2Client.exchangeAuthorizationCode: not used in federation flow test")

    override suspend fun exchangePreAuthorizedCode(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        preAuthorizedCode: String,
        txCode: String?,
        resource: List<String>?,
        dpopContext: DpopContext?,
    ): IdkResult<TokenResponse, IdkError> = error("CapturingOAuth2Client.exchangePreAuthorizedCode: not used in federation flow test")

    override suspend fun refreshAccessToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        refreshToken: String,
        scope: String?,
        resource: List<String>?,
        dpopContext: DpopContext?,
        audience: List<String>?,
    ): IdkResult<TokenResponse, IdkError> = error("CapturingOAuth2Client.refreshAccessToken: not used in federation flow test")

    override suspend fun introspectToken(
        authorizationServerMetadata: AuthorizationServerMetadata,
        clientAuthentication: ClientAuthenticationConfig,
        token: String,
        tokenTypeHint: String?,
    ): IdkResult<TokenIntrospectionResponse, IdkError> = error("CapturingOAuth2Client.introspectToken: not used in federation flow test")

    override suspend fun validateIdToken(
        idToken: String,
        options: IdTokenValidationOptions,
    ): IdkResult<ValidatedIdToken, IdkError> = error("CapturingOAuth2Client.validateIdToken: not used in federation flow test")

    override suspend fun fetchUserInfo(
        accessToken: String,
        metadata: AuthorizationServerMetadata,
    ): IdkResult<FetchUserInfoResult, IdkError> = error("CapturingOAuth2Client.fetchUserInfo: not used in federation flow test")
}

@ContributesTo(SessionScope::class)
interface CapturingOAuth2ClientGraph {
    val capturingOAuth2Client: CapturedOAuth2Authorization
    val oauth2Client: OAuth2Client
}
