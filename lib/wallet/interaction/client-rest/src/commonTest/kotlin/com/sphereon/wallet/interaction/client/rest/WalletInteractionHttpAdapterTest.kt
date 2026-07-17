/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.client.rest

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.wallet.interaction.DispatchWalletInteractionActionBody
import com.sphereon.wallet.interaction.StartWalletInteractionBody
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionApiConstants
import com.sphereon.wallet.interaction.WalletInteractionClientFrame
import com.sphereon.wallet.interaction.WalletInteractionClientFrameType
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionServerFrame
import com.sphereon.wallet.interaction.WalletInteractionServerFrameType
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionSessionEnvelope
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStateEnvelope
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.actionsPath
import com.sphereon.wallet.interaction.eventsPath
import com.sphereon.wallet.interaction.framePath
import com.sphereon.wallet.interaction.impl.CancelWalletInteractionCommandImpl
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.FixedWalletInteractionSessionIdGenerator
import com.sphereon.wallet.interaction.impl.GetWalletInteractionEventsCommandImpl
import com.sphereon.wallet.interaction.impl.GetWalletInteractionStateCommandImpl
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.impl.StoreBackedWalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.impl.ResumeWalletInteractionCommandImpl
import com.sphereon.wallet.interaction.impl.StartWalletInteractionCommandImpl
import com.sphereon.wallet.interaction.impl.StaticWalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.impl.SubmitWalletInteractionActionCommandImpl
import com.sphereon.wallet.interaction.interactionPath
import com.sphereon.wallet.interaction.interactionsPath
import com.sphereon.wallet.interaction.statePath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletInteractionHttpAdapterTest {
    @Test
    fun descriptorProviderPublishesFullPathsAndMatchingAdapterId() {
        val description = WalletInteractionHttpAdapterDescriptorProvider().describe()

        assertEquals(WalletInteractionHttpAdapter.ADAPTER_ID, description.id)
        assertEquals(WalletInteractionApiConstants.BASE_PATH, description.mount.adapterBasePath)
        assertEquals(
            WalletInteractionApiConstants.Paths.INTERACTIONS,
            StartWalletInteractionHttpEndpointCommand.ENDPOINT.pathPattern,
        )
        assertTrue(
            description.endpoints.any {
                it.pathPattern == "${WalletInteractionApiConstants.BASE_PATH}${WalletInteractionApiConstants.Paths.EVENTS}"
            },
        )
        assertTrue(description.endpoints.any { it.commandId == WalletInteractionApiConstants.EndpointCommands.FRAME })
    }

    @Test
    fun commandBackedAdapterRoutesRestLifecycleToInteractionClient() =
        runTest {
            val engine = testEngine()
            val adapter = testAdapter(engine)
            val input = WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val startedResponse =
                adapter.handleRequest(
                    GenericHttpRequest.withTextBody(
                        method = "POST",
                        path = WalletInteractionApiConstants.interactionsPath("wallet"),
                        body =
                            defaultWalletInteractionJson.encodeToString(
                                StartWalletInteractionBody.serializer(),
                                StartWalletInteractionBody(input),
                            ),
                    ),
                )
            val started =
                defaultWalletInteractionJson
                    .decodeFromString(WalletInteractionSessionEnvelope.serializer(), requireBody(startedResponse.body))
                    .session

            assertEquals(201, startedResponse.statusCode)
            assertEquals(WalletInteractionStatus.CounterpartyNotice, started.state.status)

            val stateResponse =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = WalletInteractionApiConstants.statePath("wallet", started.sessionId),
                    ),
                )
            val state =
                defaultWalletInteractionJson
                    .decodeFromString(WalletInteractionStateEnvelope.serializer(), requireBody(stateResponse.body))
                    .state

            assertEquals(started.state, state)

            val actionResponse =
                adapter.handleRequest(
                    GenericHttpRequest.withTextBody(
                        method = "POST",
                        path = WalletInteractionApiConstants.actionsPath("wallet", started.sessionId),
                        body =
                            defaultWalletInteractionJson.encodeToString(
                                DispatchWalletInteractionActionBody.serializer(),
                                DispatchWalletInteractionActionBody(WalletInteractionAction.continueFlow()),
                            ),
                    ),
                )
            val afterAction =
                defaultWalletInteractionJson
                    .decodeFromString(WalletInteractionStateEnvelope.serializer(), requireBody(actionResponse.body))
                    .state

            assertEquals(WalletInteractionStatus.Completed, afterAction.status)

            val cancelResponse =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "DELETE",
                        path = WalletInteractionApiConstants.interactionPath("wallet", started.sessionId),
                    ),
                )

            assertEquals(200, cancelResponse.statusCode)
            assertEquals(WalletInteractionStatus.Cancelled, engine.observe(started.sessionId).value.status)
        }

    @Test
    fun sseEndpointReturnsBoundedSnapshotFrame() =
        runTest {
            val adapter = testAdapter(testEngine())
            val session = start(adapter)

            val response =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = WalletInteractionApiConstants.eventsPath("wallet", session.sessionId),
                    ),
                )

            assertEquals(200, response.statusCode)
            assertEquals("text/event-stream", response.headers["Content-Type"])
            assertTrue(requireBody(response.body).contains("event: ${WalletInteractionApiConstants.Sse.EVENT_STATE}"))
            assertTrue(requireBody(response.body).contains("\"revision\":${session.state.revision}"))

            val alreadySeen =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = WalletInteractionApiConstants.eventsPath("wallet", session.sessionId),
                        headers = mapOf(WalletInteractionApiConstants.Sse.LAST_EVENT_ID_HEADER to session.state.revision.toString()),
                    ),
                )

            assertEquals(200, alreadySeen.statusCode)
            assertEquals("", alreadySeen.body)
        }

    @Test
    fun frameEndpointRoutesClientFrame() =
        runTest {
            val adapter = testAdapter(testEngine())
            val session = start(adapter)

            val response =
                adapter.handleRequest(
                    GenericHttpRequest.withTextBody(
                        method = "POST",
                        path = WalletInteractionApiConstants.framePath("wallet", session.sessionId),
                        body =
                            defaultWalletInteractionJson.encodeToString(
                                WalletInteractionClientFrame.serializer(),
                                WalletInteractionClientFrame(
                                    type = WalletInteractionClientFrameType.DISPATCH_ACTION,
                                    sessionId = session.sessionId,
                                    action = WalletInteractionAction.continueFlow(),
                                    lastRevision = session.state.revision,
                                ),
                            ),
                    ),
                )
            val frame =
                defaultWalletInteractionJson.decodeFromString(
                    WalletInteractionServerFrame.serializer(),
                    requireBody(response.body),
                )

            assertEquals(200, response.statusCode)
            assertEquals(WalletInteractionServerFrameType.STATE, frame.type)
            assertEquals(WalletInteractionStatus.Completed, frame.state?.status)
        }

    @Test
    fun frameEndpointRejectsPathSessionMismatch() =
        runTest {
            val adapter = testAdapter(testEngine())
            val session = start(adapter)
            val otherSessionId = WalletInteractionSessionId("other")

            val response =
                adapter.handleRequest(
                    GenericHttpRequest.withTextBody(
                        method = "POST",
                        path = WalletInteractionApiConstants.framePath("wallet", session.sessionId),
                        body =
                            defaultWalletInteractionJson.encodeToString(
                                WalletInteractionClientFrame.serializer(),
                                WalletInteractionClientFrame(
                                    type = WalletInteractionClientFrameType.PING,
                                    sessionId = otherSessionId,
                                ),
                            ),
                    ),
                )

            assertEquals(400, response.statusCode)
            assertNotNull(response.body)
        }

    @Test
    fun sseParserAcceptsStateEvents() {
        val state =
            WalletInteractionState.resolving(
                WalletInteractionSessionId("s1"),
                WalletInteractionInput("wallet", WalletEntryPoint.rawQr("qr")),
            )
        val event = WalletInteractionStateEvent(state.sessionId, state.revision, state = state)
        val frame =
            "id: 0\n" +
                "event: ${WalletInteractionApiConstants.Sse.EVENT_STATE}\n" +
                "data: ${defaultWalletInteractionJson.encodeToString(WalletInteractionStateEvent.serializer(), event)}\n"

        val parsed = WalletInteractionSseParser().parse(frame)

        assertEquals("0", parsed.id)
        assertEquals(state.sessionId, parsed.data.sessionId)
    }

    @Test
    fun frameCodecUsesUiSafeFrames() {
        val sessionId = WalletInteractionSessionId("s1")
        val frame =
            WalletInteractionClientFrame(
                type = WalletInteractionClientFrameType.DISPATCH_ACTION,
                sessionId = sessionId,
                action = WalletInteractionAction.continueFlow(),
                lastRevision = 1,
            )
        val codec = WalletInteractionFrameCodec()

        val encoded = codec.encodeClient(frame)
        val decoded = codec.decodeClient(encoded)

        assertEquals(WalletInteractionApiConstants.framePath("wallet", sessionId), "/api/wallet/interaction/v1/wallets/wallet/interactions/s1/frames")
        assertEquals(frame, decoded)
    }

    @Test
    fun serverStateFrameRoundTrips() {
        val state =
            WalletInteractionState.resolving(
                WalletInteractionSessionId("s1"),
                WalletInteractionInput("wallet", WalletEntryPoint.rawQr("qr")),
            )
        val codec = WalletInteractionFrameCodec()

        val encoded =
            codec.encodeServer(
                WalletInteractionServerFrame(
                    type = WalletInteractionServerFrameType.STATE,
                    sessionId = state.sessionId,
                    revision = state.revision,
                    state = state,
                ),
            )
        val decoded = codec.decodeServer(encoded)

        assertEquals(WalletInteractionServerFrameType.STATE, decoded.type)
        assertEquals(state, decoded.state)
    }

    private suspend fun start(adapter: WalletInteractionHttpAdapter): WalletInteractionSession {
        val response =
            adapter.handleRequest(
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = WalletInteractionApiConstants.interactionsPath("wallet"),
                    body =
                        defaultWalletInteractionJson.encodeToString(
                            StartWalletInteractionBody.serializer(),
                            StartWalletInteractionBody(WalletInteractionInput("wallet", WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))),
                        ),
                ),
            )
        return defaultWalletInteractionJson
            .decodeFromString(
                WalletInteractionSessionEnvelope.serializer(),
                requireBody(response.body),
            ).session
    }

    private fun testEngine(): DefaultWalletInteractionEngine =
        DefaultWalletInteractionEngine(
            sensitiveInputAuthority =
                StoreBackedWalletInteractionSensitiveInputAuthority(
                    InMemoryWalletInteractionPrivateSessionStore(),
                ),
            adapters = listOf(StaticWalletInteractionProtocolAdapter.oid4vci()),
            sessionIdGenerator = FixedWalletInteractionSessionIdGenerator(),
        )

    private fun testAdapter(engine: DefaultWalletInteractionEngine): WalletInteractionHttpAdapter {
        val execution = TestSessionExecution
        val startCommand = StartWalletInteractionCommandImpl(execution, engine)
        val resumeCommand = ResumeWalletInteractionCommandImpl(execution, engine)
        val submitActionCommand = SubmitWalletInteractionActionCommandImpl(execution, engine)
        val cancelCommand = CancelWalletInteractionCommandImpl(execution, engine)
        val getStateCommand = GetWalletInteractionStateCommandImpl(execution, engine)
        val getEventsCommand = GetWalletInteractionEventsCommandImpl(execution, engine)
        return WalletInteractionHttpAdapter(
            execution = execution,
            start = StartWalletInteractionHttpEndpointCommandImpl(execution, startCommand),
            resume = ResumeWalletInteractionHttpEndpointCommandImpl(execution, resumeCommand),
            dispatch = DispatchWalletInteractionActionHttpEndpointCommandImpl(execution, submitActionCommand),
            cancel = CancelWalletInteractionHttpEndpointCommandImpl(execution, cancelCommand),
            getState = GetWalletInteractionStateHttpEndpointCommandImpl(execution, getStateCommand),
            getEvents = GetWalletInteractionEventsHttpEndpointCommandImpl(execution, getEventsCommand),
            frame =
                HandleWalletInteractionFrameHttpEndpointCommandImpl(
                    execution = execution,
                    resumeCommand = resumeCommand,
                    submitActionCommand = submitActionCommand,
                    cancelCommand = cancelCommand,
                ),
        )
    }
}

private fun requireBody(body: String?): String = requireNotNull(body)

private val TestSessionExecution: SessionExecution = TestSessionExecutionImpl()

private class TestSessionExecutionImpl : SessionExecution {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("test")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = NoOpContextConfig()
}

private class NoOpSessionLogService(
    override val sessionContext: SessionContext,
) : SessionLogService {
    override val id: String = "wallet-interaction-http-test-log"
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("test")

    override suspend fun setConfig(config: LoggerConfig): LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("test")
}

private class NoOpContextConfig : ContextConfig {
    override val app: AppConfigService
        get() = throw NotImplementedError("test")
    override val tenant: TenantConfigService
        get() = throw NotImplementedError("test")
    override val principal: PrincipalConfigService
        get() = throw NotImplementedError("test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("test")
}
