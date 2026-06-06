package com.sphereon.statuslist.hosting.rest

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.command.GetStatusListTokenCommand
import com.sphereon.statuslist.hosting.rest.command.GetStatusListTokenByCorrelationIdEndpointCommandImpl
import com.sphereon.statuslist.hosting.rest.command.GetStatusListTokenByIdEndpointCommandImpl
import com.sphereon.statuslist.hosting.rest.test.createTestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Real-chain tests for the public status-list hosting endpoints: a [CommandBackedHttpAdapter] mounted
 * at `/statuslists` dispatches to the REAL by-id / by-correlationId token endpoint impls, each
 * delegating to a recording stub of the IDK `GetStatusListTokenCommand`. Asserts that the response is
 * the RAW token string with the token's own `Content-Type` and a `Cache-Control` header (never a JSON
 * envelope), and that the correct [StatusListRef] reaches the service.
 */
class StatusListHostingHttpAdapterTest {
    private val signedToken = "eyJhbGciOiJFUzI1NiJ9.statuslist.signature"

    private inner class Fixture(
        ttlSeconds: Long?
    ) {
        val execution: SessionExecution = createTestSessionExecution()
        val stub =
            StubGetToken(
                execution,
                StatusListToken(
                    token = signedToken,
                    contentType = StatusListContentTypes.STATUSLIST_JWT,
                    ttlSeconds = ttlSeconds,
                ),
            )
        val adapter: HttpAdapter =
            TestAdapter(
                execution = execution,
                endpoints =
                    listOf(
                        GetStatusListTokenByIdEndpointCommandImpl(execution, stub),
                        GetStatusListTokenByCorrelationIdEndpointCommandImpl(execution, stub),
                    ),
            )
    }

    private fun base(suffix: String) = StatusListHostingApiConstants.BASE_PATH + suffix

    @Test
    fun getById_returnsRawToken_withContentType_andTtlCacheControl() =
        runTest {
            val f = Fixture(ttlSeconds = 3600)
            val response =
                f.adapter.handleRequest(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/sl-7"), body = null),
                )
            assertEquals(200, response.statusCode)
            // Raw token, not a JSON envelope.
            assertEquals(signedToken, response.bodyBytes?.decodeToString())
            assertEquals(StatusListContentTypes.STATUSLIST_JWT, response.contentType)
            assertEquals("public, max-age=3600", response.headers["Cache-Control"])
            assertEquals(StatusListRef(id = "sl-7"), f.stub.captured)
        }

    @Test
    fun getByCorrelationId_resolvesByCorrelationRef() =
        runTest {
            val f = Fixture(ttlSeconds = null)
            val response =
                f.adapter.handleRequest(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/by/corr-9"), body = null),
                )
            assertEquals(200, response.statusCode)
            assertEquals(signedToken, response.bodyBytes?.decodeToString())
            assertEquals(StatusListRef(correlationId = "corr-9"), f.stub.captured)
        }

    @Test
    fun missingTtl_fallsBackToDefaultMaxAge() =
        runTest {
            val f = Fixture(ttlSeconds = null)
            val response =
                f.adapter.handleRequest(
                    GenericHttpRequest.withTextBody(method = "GET", path = base("/sl-1"), body = null),
                )
            val cacheControl = assertNotNull(response.headers["Cache-Control"])
            assertEquals("public, max-age=${StatusListHostingApiConstants.DEFAULT_CACHE_MAX_AGE_SECONDS}", cacheControl)
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
            id = "statuslist-hosting-test",
            execution = execution,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = StatusListHostingApiConstants.BASE_PATH,
                ),
        ) {
        override val endpointCommands: List<HttpEndpointCommand> = endpoints
    }
}
