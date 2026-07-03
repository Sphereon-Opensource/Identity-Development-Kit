/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.client.rest

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.command.headerIgnoreCase
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.CancelWalletInteractionArgs
import com.sphereon.wallet.interaction.CancelWalletInteractionCommand
import com.sphereon.wallet.interaction.CancelWalletInteractionResult
import com.sphereon.wallet.interaction.DispatchWalletInteractionActionBody
import com.sphereon.wallet.interaction.GetWalletInteractionEventsArgs
import com.sphereon.wallet.interaction.GetWalletInteractionEventsCommand
import com.sphereon.wallet.interaction.GetWalletInteractionStateArgs
import com.sphereon.wallet.interaction.GetWalletInteractionStateCommand
import com.sphereon.wallet.interaction.ResumeWalletInteractionArgs
import com.sphereon.wallet.interaction.ResumeWalletInteractionCommand
import com.sphereon.wallet.interaction.StartWalletInteractionBody
import com.sphereon.wallet.interaction.StartWalletInteractionCommand
import com.sphereon.wallet.interaction.SubmitWalletInteractionActionArgs
import com.sphereon.wallet.interaction.SubmitWalletInteractionActionCommand
import com.sphereon.wallet.interaction.WalletInteractionApiConstants
import com.sphereon.wallet.interaction.WalletInteractionClientFrame
import com.sphereon.wallet.interaction.WalletInteractionClientFrameType
import com.sphereon.wallet.interaction.WalletInteractionServerFrame
import com.sphereon.wallet.interaction.WalletInteractionServerFrameType
import com.sphereon.wallet.interaction.WalletInteractionSessionEnvelope
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionStateEnvelope
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private const val PARAM_WALLET_INSTANCE_ID = "walletInstanceId"
private const val PARAM_SESSION_ID = "sessionId"
private const val WALLET_INTERACTION_TAG = "wallet-interaction"
private val JSON_MEDIA = setOf(MediaType.ApplicationJson)
private val SSE_MEDIA = setOf(MediaType.Custom("text/event-stream"))
private val JSON_HEADERS = mapOf("Content-Type" to "application/json")
private val SSE_HEADERS =
    mapOf(
        "Content-Type" to "text/event-stream",
        "Cache-Control" to "no-cache",
        "Connection" to "keep-alive",
    )

interface StartWalletInteractionHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.START
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.POST,
                pathPattern = WalletInteractionApiConstants.Paths.INTERACTIONS,
                consumes = JSON_MEDIA,
                produces = JSON_MEDIA,
                operationId = "startWalletInteraction",
                commandId = COMMAND_ID,
            )
    }
}

interface ResumeWalletInteractionHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.RESUME
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.POST,
                pathPattern = WalletInteractionApiConstants.Paths.RESUME,
                produces = JSON_MEDIA,
                operationId = "resumeWalletInteraction",
                commandId = COMMAND_ID,
            )
    }
}

interface DispatchWalletInteractionActionHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.DISPATCH_ACTION
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.POST,
                pathPattern = WalletInteractionApiConstants.Paths.ACTIONS,
                consumes = JSON_MEDIA,
                produces = JSON_MEDIA,
                operationId = "dispatchWalletInteractionAction",
                commandId = COMMAND_ID,
            )
    }
}

interface CancelWalletInteractionHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.CANCEL
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.DELETE,
                pathPattern = WalletInteractionApiConstants.Paths.INTERACTION,
                produces = JSON_MEDIA,
                operationId = "cancelWalletInteraction",
                commandId = COMMAND_ID,
            )
    }
}

interface GetWalletInteractionStateHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.GET_STATE
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.GET,
                pathPattern = WalletInteractionApiConstants.Paths.STATE,
                produces = JSON_MEDIA,
                operationId = "getWalletInteractionState",
                commandId = COMMAND_ID,
            )
    }
}

interface GetWalletInteractionEventsHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.GET_EVENTS
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.GET,
                pathPattern = WalletInteractionApiConstants.Paths.EVENTS,
                produces = SSE_MEDIA,
                operationId = "getWalletInteractionEvents",
                commandId = COMMAND_ID,
            )
    }
}

interface HandleWalletInteractionFrameHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = WalletInteractionApiConstants.EndpointCommands.FRAME
        val ENDPOINT: HttpEndpointDescriptor =
            walletInteractionEndpoint(
                method = HttpMethod.POST,
                pathPattern = WalletInteractionApiConstants.Paths.FRAMES,
                consumes = JSON_MEDIA,
                produces = JSON_MEDIA,
                operationId = "handleWalletInteractionFrame",
                commandId = COMMAND_ID,
            )
    }
}

/**
 * Public REST adapter for wallet interaction sessions.
 *
 * This adapter exposes request/response HTTP resources and bounded event replay.
 * It does not provide the internal routed command transport; live event streams
 * remain available through the neutral server-streaming command.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class WalletInteractionHttpAdapter(
    execution: SessionExecution,
    private val start: StartWalletInteractionHttpEndpointCommand,
    private val resume: ResumeWalletInteractionHttpEndpointCommand,
    private val dispatch: DispatchWalletInteractionActionHttpEndpointCommand,
    private val cancel: CancelWalletInteractionHttpEndpointCommand,
    private val getState: GetWalletInteractionStateHttpEndpointCommand,
    private val getEvents: GetWalletInteractionEventsHttpEndpointCommand,
    private val frame: HandleWalletInteractionFrameHttpEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ADAPTER_ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = BASE_PATH),
        tenantPathPolicy = TenantPathPolicy.None,
    ) {
    override val endpointCommands: List<HttpEndpointCommand> by lazy {
        listOf(start, resume, dispatch, cancel, getState, getEvents, frame)
    }

    override val openApiHints: OpenApiHints =
        OpenApiHints(
            tags = setOf(WALLET_INTERACTION_TAG),
            operationIdPrefix = "walletInteraction",
        )

    companion object {
        const val ADAPTER_ID: String = "wallet-interaction.http"
        const val BASE_PATH: String = WalletInteractionApiConstants.BASE_PATH
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class WalletInteractionHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = WalletInteractionHttpAdapter.ADAPTER_ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = WalletInteractionHttpAdapter.BASE_PATH,
                ),
            endpoints =
                WalletInteractionHttpEndpoints.relative.map { endpoint ->
                    endpoint.copy(
                        pathPatterns = endpoint.pathPatterns.map { pattern -> WalletInteractionHttpAdapter.BASE_PATH + pattern },
                    )
                },
            openApiHints =
                OpenApiHints(
                    tags = setOf(WALLET_INTERACTION_TAG),
                    operationIdPrefix = "walletInteraction",
                ),
        )
}

object WalletInteractionHttpEndpoints {
    val relative: List<HttpEndpointDescriptor> =
        listOf(
            StartWalletInteractionHttpEndpointCommand.ENDPOINT,
            ResumeWalletInteractionHttpEndpointCommand.ENDPOINT,
            DispatchWalletInteractionActionHttpEndpointCommand.ENDPOINT,
            CancelWalletInteractionHttpEndpointCommand.ENDPOINT,
            GetWalletInteractionStateHttpEndpointCommand.ENDPOINT,
            GetWalletInteractionEventsHttpEndpointCommand.ENDPOINT,
            HandleWalletInteractionFrameHttpEndpointCommand.ENDPOINT,
        )
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StartWalletInteractionHttpEndpointCommand>())
class StartWalletInteractionHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val command: StartWalletInteractionCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = StartWalletInteractionHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = StartWalletInteractionHttpEndpointCommand.ENDPOINT,
    ),
    StartWalletInteractionHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            val walletInstanceId = request.walletInstanceId().orAbort()
            val body = request.requireJsonBody<StartWalletInteractionBody>(json).orAbort()
            if (body.input.walletInstanceId != walletInstanceId) {
                abort(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "wallet_interaction_start_wallet_mismatch"))
            }
            WalletInteractionSessionEnvelope(
                command.execute(body.input).orAbort(),
            ).json(WalletInteractionSessionEnvelope.serializer(), statusCode = 201)
        }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResumeWalletInteractionHttpEndpointCommand>())
class ResumeWalletInteractionHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val command: ResumeWalletInteractionCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = ResumeWalletInteractionHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ResumeWalletInteractionHttpEndpointCommand.ENDPOINT,
    ),
    ResumeWalletInteractionHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            WalletInteractionSessionEnvelope(
                command
                    .execute(
                        ResumeWalletInteractionArgs(
                            walletInstanceId = request.walletInstanceId().orAbort(),
                            sessionId = request.sessionId().orAbort(),
                        ),
                    ).orAbort(),
            ).json(WalletInteractionSessionEnvelope.serializer())
        }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DispatchWalletInteractionActionHttpEndpointCommand>())
class DispatchWalletInteractionActionHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val command: SubmitWalletInteractionActionCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = DispatchWalletInteractionActionHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DispatchWalletInteractionActionHttpEndpointCommand.ENDPOINT,
    ),
    DispatchWalletInteractionActionHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            val body = request.requireJsonBody<DispatchWalletInteractionActionBody>(json).orAbort()
            WalletInteractionStateEnvelope(
                command
                    .execute(
                        SubmitWalletInteractionActionArgs(
                            walletInstanceId = request.walletInstanceId().orAbort(),
                            sessionId = request.sessionId().orAbort(),
                            action = body.action,
                        ),
                    ).orAbort(),
            ).json(WalletInteractionStateEnvelope.serializer())
        }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CancelWalletInteractionHttpEndpointCommand>())
class CancelWalletInteractionHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val command: CancelWalletInteractionCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = CancelWalletInteractionHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = CancelWalletInteractionHttpEndpointCommand.ENDPOINT,
    ),
    CancelWalletInteractionHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            command
                .execute(
                    CancelWalletInteractionArgs(
                        walletInstanceId = request.walletInstanceId().orAbort(),
                        sessionId = request.sessionId().orAbort(),
                    ),
                ).orAbort()
                .json(CancelWalletInteractionResult.serializer())
        }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetWalletInteractionStateHttpEndpointCommand>())
class GetWalletInteractionStateHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val command: GetWalletInteractionStateCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = GetWalletInteractionStateHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetWalletInteractionStateHttpEndpointCommand.ENDPOINT,
    ),
    GetWalletInteractionStateHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            WalletInteractionStateEnvelope(
                command
                    .execute(
                        GetWalletInteractionStateArgs(
                            walletInstanceId = request.walletInstanceId().orAbort(),
                            sessionId = request.sessionId().orAbort(),
                        ),
                    ).orAbort(),
            ).json(WalletInteractionStateEnvelope.serializer())
        }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetWalletInteractionEventsHttpEndpointCommand>())
class GetWalletInteractionEventsHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val command: GetWalletInteractionEventsCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = GetWalletInteractionEventsHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetWalletInteractionEventsHttpEndpointCommand.ENDPOINT,
    ),
    GetWalletInteractionEventsHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            val afterRevision = request.afterRevision().orAbort()
            val events =
                command
                    .execute(
                        GetWalletInteractionEventsArgs(
                            walletInstanceId = request.walletInstanceId().orAbort(),
                            sessionId = request.sessionId().orAbort(),
                            afterRevision = afterRevision,
                        ),
                    ).orAbort()
                    .events
            val body = events.joinToString(separator = "") { event -> encodeSseEvent(event) }
            GenericHttpResponse(statusCode = 200, headers = SSE_HEADERS, body = body)
        }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleWalletInteractionFrameHttpEndpointCommand>())
class HandleWalletInteractionFrameHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val resumeCommand: ResumeWalletInteractionCommand,
    private val submitActionCommand: SubmitWalletInteractionActionCommand,
    private val cancelCommand: CancelWalletInteractionCommand,
) : WalletInteractionHttpEndpointCommandAdapter(
        id = HandleWalletInteractionFrameHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = HandleWalletInteractionFrameHttpEndpointCommand.ENDPOINT,
    ),
    HandleWalletInteractionFrameHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        runWalletInteractionEndpoint {
            val request = applyDuring(args)
            val pathSessionId = request.sessionId().orAbort()
            val frame = request.requireJsonBody<WalletInteractionClientFrame>(json).orAbort()
            if (frame.sessionId != pathSessionId) {
                abort(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "wallet_interaction_frame_session_mismatch"))
            }
            val walletInstanceId = request.walletInstanceId().orAbort()
            val state =
                when (frame.type) {
                    WalletInteractionClientFrameType.RESUME -> {
                        resumeCommand
                            .execute(ResumeWalletInteractionArgs(walletInstanceId, frame.sessionId))
                            .orAbort()
                            .state
                    }

                    WalletInteractionClientFrameType.DISPATCH_ACTION -> {
                        submitActionCommand
                            .execute(
                                SubmitWalletInteractionActionArgs(
                                    walletInstanceId = walletInstanceId,
                                    sessionId = frame.sessionId,
                                    action = requireNotNull(frame.action) { "wallet_interaction_frame_action_missing" },
                                ),
                            ).orAbort()
                    }

                    WalletInteractionClientFrameType.CANCEL -> {
                        cancelCommand
                            .execute(CancelWalletInteractionArgs(walletInstanceId, frame.sessionId))
                            .orAbort()
                            .state
                    }

                    WalletInteractionClientFrameType.PING -> {
                        null
                    }
                }
            val response =
                if (state == null) {
                    WalletInteractionServerFrame(
                        type = WalletInteractionServerFrameType.PONG,
                        sessionId = frame.sessionId,
                    )
                } else {
                    WalletInteractionServerFrame(
                        type = WalletInteractionServerFrameType.STATE,
                        sessionId = frame.sessionId,
                        revision = state.revision,
                        state = state,
                    )
                }
            response.json(WalletInteractionServerFrame.serializer())
        }
}

abstract class WalletInteractionHttpEndpointCommandAdapter(
    id: String,
    execution: SessionExecution,
    endpoint: HttpEndpointDescriptor,
) : HttpEndpointCommandAdapter(id = id, execution = execution, endpoint = endpoint) {
    protected val json: Json = defaultWalletInteractionJson

    protected suspend fun runWalletInteractionEndpoint(block: suspend () -> GenericHttpResponse,): IdkResult<GenericHttpResponse, IdkError> =
        try {
            Ok(block())
        } catch (abort: WalletInteractionHttpAbort) {
            Err(abort.error)
        } catch (expected: IllegalArgumentException) {
            Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = expected.message ?: "wallet_interaction_request_invalid",
                    throwable = expected,
                ),
            )
        }

    protected fun <T> T.json(
        serializer: KSerializer<T>,
        statusCode: Int = 200,
    ): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = statusCode,
            headers = JSON_HEADERS,
            body = json.encodeToString(serializer, this),
        )

    protected fun <T> IdkResult<T, IdkError>.orAbort(): T = getOrElse { abort(it) }

    protected fun abort(error: IdkError): Nothing = throw WalletInteractionHttpAbort(error)

    protected fun encodeSseEvent(event: WalletInteractionStateEvent): String =
        "id: ${event.revision}\n" +
            "event: ${WalletInteractionApiConstants.Sse.EVENT_STATE}\n" +
            "data: ${json.encodeToString(WalletInteractionStateEvent.serializer(), event)}\n\n"
}

private class WalletInteractionHttpAbort(
    val error: IdkError,
) : RuntimeException(error.toString())

private fun GenericHttpRequest.walletInstanceId(): IdkResult<String, IdkError> = requirePathParam(PARAM_WALLET_INSTANCE_ID)

private fun GenericHttpRequest.sessionId(): IdkResult<WalletInteractionSessionId, IdkError> = requirePathParam(PARAM_SESSION_ID).map { raw -> WalletInteractionSessionId(raw) }

private fun GenericHttpRequest.afterRevision(): IdkResult<Long?, IdkError> {
    val raw = headerIgnoreCase(WalletInteractionApiConstants.Sse.LAST_EVENT_ID_HEADER) ?: return Ok(null)
    val revision =
        raw.toLongOrNull()
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "wallet_interaction_last_event_id_invalid"))
    return Ok(revision)
}

private fun walletInteractionEndpoint(
    method: HttpMethod,
    pathPattern: String,
    consumes: Set<MediaType> = emptySet(),
    produces: Set<MediaType>,
    operationId: String,
    commandId: String,
): HttpEndpointDescriptor =
    HttpEndpointDescriptor(
        method = method,
        pathPattern = pathPattern,
        consumes = consumes,
        produces = produces,
        operationId = operationId,
        commandId = commandId,
        tags = setOf(WALLET_INTERACTION_TAG),
    )
