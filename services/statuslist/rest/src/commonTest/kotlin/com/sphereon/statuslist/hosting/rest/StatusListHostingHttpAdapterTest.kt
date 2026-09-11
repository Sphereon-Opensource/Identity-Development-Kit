package com.sphereon.statuslist.hosting.rest

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListHostingMode
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListResult
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.command.GetStatusListCommand
import com.sphereon.statuslist.command.GetStatusListTokenCommand
import com.sphereon.statuslist.hosting.rest.command.GetStatusListTokenByCorrelationIdEndpointCommandImpl
import com.sphereon.statuslist.hosting.rest.test.createTestAppConfigService
import com.sphereon.statuslist.hosting.rest.test.createTestSessionExecution
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Real-chain tests for the public status-list hosting endpoints: a [CommandBackedHttpAdapter] mounted
 * at the hosting base path (`/public/statuslists`) dispatches to the REAL by-id / by-correlationId token endpoint impls, each
 * delegating to a recording stub of the IDK `GetStatusListTokenCommand`. Asserts that the response is
 * the RAW token string with the token's own `Content-Type` and a `Cache-Control` header (never a JSON
 * envelope), and that the correct [StatusListRef] reaches the service.
 */
class StatusListHostingHttpAdapterTest {
    private val signedToken = "eyJhbGciOiJFUzI1NiJ9.statuslist.signature"

    private inner class Fixture(
        ttlSeconds: Long?,
        hostingMode: StatusListHostingMode = StatusListHostingMode.HOSTED,
        hostingConfig: StatusListHostingConfig = StatusListHostingConfig(),
    ) {
        val execution: SessionExecution = createTestSessionExecution()
        val metadataStub = StubGetStatusList(execution, hostingMode)
        val tokenStub =
            StubGetToken(
                execution,
                StatusListToken(
                    token = signedToken,
                    contentType = StatusListContentTypes.STATUSLIST_JWT,
                    ttlSeconds = ttlSeconds,
                ),
            )
        val adapter =
            TestAdapter(
                execution = execution,
                endpoints =
                    listOf(
                        GetStatusListTokenByCorrelationIdEndpointCommandImpl(
                            execution,
                            metadataStub,
                            tokenStub,
                            hostingConfig,
                        ),
                    ),
            )
    }

    private fun base(suffix: String) = StatusListHostingApiConstants.BASE_PATH + suffix

    @Test
    fun getByCorrelationId_returnsRawToken_withContentType_andTtlCacheControl() =
        runTest {
            val f = Fixture(ttlSeconds = 3600)
            val response =
                f.adapter.dispatch(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/sl-7"), body = null),
                )
            assertEquals(200, response.statusCode)
            // Raw token, not a JSON envelope.
            assertEquals(signedToken, response.bodyBytes?.decodeToString())
            assertEquals(StatusListContentTypes.STATUSLIST_JWT, response.contentType)
            assertEquals("public, max-age=3600", response.headers["Cache-Control"])
            // The public `/{id}` route resolves the stable business key (the human-readable
            // correlationId that credentials embed) first, falling back to the technical id.
            assertEquals(StatusListRef(correlationId = "sl-7", statusListUri = "http://localhost/public/statuslists/sl-7"), f.tokenStub.captured)
        }

    @Test
    fun getByCorrelationId_resolvesByCorrelationRef() =
        runTest {
            val f = Fixture(ttlSeconds = null)
            val response =
                f.adapter.dispatch(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/corr-9"), body = null),
                )
            assertEquals(200, response.statusCode)
            assertEquals(signedToken, response.bodyBytes?.decodeToString())
            assertEquals(StatusListRef(correlationId = "corr-9", statusListUri = "http://localhost/public/statuslists/corr-9"), f.tokenStub.captured)
        }

    @Test
    fun exportMode_isNotServedFromPublicHostingEndpoint() =
        runTest {
            val f = Fixture(ttlSeconds = null, hostingMode = StatusListHostingMode.EXPORT)
            val response =
                f.adapter.dispatch(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/export-only"), body = null),
                )
            assertEquals(404, response.statusCode)
            assertEquals(
                StatusListRef(correlationId = "export-only", statusListUri = "http://localhost/public/statuslists/export-only"),
                f.metadataStub.captured,
            )
            assertEquals(null, f.tokenStub.captured)
        }

    @Test
    fun missingTtl_fallsBackToDefaultMaxAge() =
        runTest {
            val f = Fixture(ttlSeconds = null)
            val response =
                f.adapter.dispatch(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/sl-1"), body = null),
                )
            val cacheControl = assertNotNull(response.headers["Cache-Control"])
            assertEquals("public, max-age=${StatusListHostingApiConstants.DEFAULT_CACHE_MAX_AGE_SECONDS}", cacheControl)
        }

    @Test
    fun configuredZeroHostingMaxAgeOverridesPositiveSignedTokenTtl() =
        runTest {
            val appConfig =
                createTestAppConfigService(
                    mapOf(StatusListHostingConfig.CACHE_MAX_AGE_SECONDS_KEY to "0"),
                )
            val f =
                Fixture(
                    ttlSeconds = 3600,
                    hostingConfig = StatusListHostingConfig(Provider { appConfig }),
                )

            val response =
                f.adapter.dispatch(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/fresh-status"), body = null),
                )

            assertEquals(200, response.statusCode)
            assertEquals(signedToken, response.bodyBytes?.decodeToString())
            assertEquals(StatusListContentTypes.STATUSLIST_JWT, response.contentType)
            assertEquals("public, max-age=0", response.headers["Cache-Control"])
        }

    @Test
    fun missingHostingMaxAgeLeavesTokenTtlPolicyInControl() {
        assertNull(StatusListHostingConfig().cacheMaxAgeSeconds)
    }

    @Test
    fun malformedOrNegativeHostingMaxAgeFailsClosed() {
        listOf("not-a-number", "-1", "", "   ").forEach { configured ->
            val appConfig =
                createTestAppConfigService(
                    mapOf(StatusListHostingConfig.CACHE_MAX_AGE_SECONDS_KEY to configured),
                )
            val config = StatusListHostingConfig(Provider { appConfig })

            assertFailsWith<IllegalArgumentException>("configuration '$configured' must fail closed") {
                config.cacheMaxAgeSeconds
            }
        }
    }

    private inner class StubGetStatusList(
        execution: SessionExecution,
        private val hostingMode: StatusListHostingMode,
    ) : TypedServiceCommandAdapter<StatusListRef, StatusListResult, IdkError>(
            commandId = GetStatusListCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<StatusListRef>(),
            outputTypeToken = typeToken<StatusListResult>(),
        ),
        GetStatusListCommand {
        override val commandId: String get() = GetStatusListCommand.COMMAND_ID
        var captured: StatusListRef? = null

        override suspend fun doExecute(
            args: StatusListRef,
            applyDuring: (StatusListRef) -> StatusListRef,
        ): IdkResult<StatusListResult, IdkError> {
            val ref = applyDuring(args)
            captured = ref
            val correlationId = ref.correlationId ?: "sl-1"
            return Ok(
                StatusListResult(
                    id = "sl-1",
                    correlationId = correlationId,
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    purposes = listOf(StatusPurpose.REVOCATION),
                    proofFormat = StatusProofFormat.JWT,
                    hostingMode = hostingMode,
                    bitsPerStatus = 1,
                    length = 131_072,
                    issuer = "did:example:issuer",
                    statusListUri = ref.statusListUri ?: "http://localhost/public/statuslists/$correlationId",
                    signedToken = signedToken,
                    contentType = StatusListContentTypes.STATUSLIST_JWT,
                ),
            )
        }
    }

    private inner class StubGetToken(
        execution: SessionExecution,
        private val token: StatusListToken,
    ) : TypedServiceCommandAdapter<StatusListRef, StatusListToken, IdkError>(
            commandId = GetStatusListTokenCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<StatusListRef>(),
            outputTypeToken = typeToken<StatusListToken>(),
        ),
        GetStatusListTokenCommand {
        override val commandId: String get() = GetStatusListTokenCommand.COMMAND_ID
        var captured: StatusListRef? = null

        override suspend fun doExecute(
            args: StatusListRef,
            applyDuring: (StatusListRef) -> StatusListRef,
        ): IdkResult<StatusListToken, IdkError> {
            captured = applyDuring(args)
            return Ok(token)
        }
    }

    /** Minimal real [CommandBackedHttpAdapter] mounting exactly the endpoints under test. */
    private class TestAdapter(
        execution: SessionExecution,
        private val endpoints: List<HttpEndpointCommand>,
    ) : CommandBackedHttpAdapter(
            id = "test.statuslist.hosting",
            execution = execution,
            endpointCommandRegistry = TestEndpointCommandRegistry(endpoints),
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = StatusListHostingApiConstants.BASE_PATH,
                ),
        ) {
        suspend fun dispatch(request: GenericHttpRequest) =
            endpoints.single().let { endpoint ->
                handleResolvedRequest(
                    request,
                    HttpAdapterRouteMatch(
                        adapterId = id,
                        method = request.method,
                        originalPath = request.path,
                        normalizedPath = request.path,
                        matchedPathPattern =
                            StatusListHostingApiConstants.BASE_PATH.trimEnd('/') +
                                "/" +
                                endpoint.endpoint.pathPattern.trimStart('/'),
                        handlerCommandId = endpoint.id,
                        tenantIdFromPath = null,
                    ),
                )
            }
    }

    private class TestEndpointCommandRegistry(
        endpoints: List<HttpEndpointCommand>,
    ) : HttpEndpointCommandRegistry {
        private val endpointsById = endpoints.associateBy { it.id }

        override fun get(handlerCommandId: String): HttpEndpointCommand? = endpointsById[handlerCommandId]

        override fun listHandlerCommandIds(): Set<String> = endpointsById.keys
    }
}
