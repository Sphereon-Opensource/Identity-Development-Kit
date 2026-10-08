package com.sphereon.openid.oid4vp.universal.impl

import com.sphereon.core.events.EventStore
import com.sphereon.core.events.SessionEventService
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.http.callback.CallbackSigning
import com.sphereon.di.context.PrincipalType
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.universal.impl.event.SessionStatusEventEmitterImpl
import com.sphereon.openid.oid4vp.verifier.impl.KtorAuthorizationSessionCallbackDispatcher
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCallbackConfig
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression boundary for configured callback signing when no secret resolver is configured.
 *
 * This intentionally uses the real session graph's event service and the real callback
 * dispatcher. Only remote I/O is replaced by MockEngine. A configured HMAC callback must
 * fail closed before remote I/O when its secret reference cannot be resolved. The companion
 * success test supplies an IDK resolver and verifies the receiver-side HMAC independently.
 */
class CallbackSigningSessionStatusEventEmitterTest {
    @Test
    fun configuredSigningWithoutSecretResolverFailsClosedBeforeDelivery() = runTest {
        val captured = mutableListOf<CapturedRequest>()
        val transport = TransportFactory(MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes()
            captured += CapturedRequest(request.headers, body)
            respond("", HttpStatusCode.OK)
        })

        try {
            val app = createJvmUniversalOid4vpTestAppGraph(this)
            val sessionInstance =
                app.userContextManager
                    .getAnonymous()
                    .sessionContextManager
                    .createOrGetFromId("callback-signing-regression", principalType = PrincipalType.USER)
            val sessionGraph = sessionInstance.graph
            val sessionEventService = (sessionGraph as SessionEventService.Graph).sessionEventService
            val eventStore = (app as EventStore.Graph).eventStore
            val correlationId = "callback-signing-correlation"
            val created = session(correlationId)
            val updated =
                created.copy(
                    status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
                    updatedAt = created.updatedAt + 1,
                )

            SessionStatusEventEmitterImpl(
                sessionEventService = sessionEventService,
                callbackDispatcher = KtorAuthorizationSessionCallbackDispatcher(transport),
            ).onStatusChange(updated, created.status)

            val events = eventStore.getRecent(100).getOrElse { error("event store read failed: $it") }
                .filter { it.context.correlationId == correlationId }
            val dispatched = events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_DISPATCHED }
            val failed = events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_FAILED }

            assertTrue(captured.isEmpty(), "an unresolved signing secret must fail before remote I/O")
            assertTrue(failed, "a pre-request signing failure must emit CALLBACK_FAILED")
            assertFalse(dispatched, "a failed callback must never emit CALLBACK_DISPATCHED")
            assertTrue(
                events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_ATTEMPTED },
                "the real SessionEventService must persist CALLBACK_ATTEMPTED",
            )
        } finally {
            transport.close()
        }
    }

    @Test
    fun configuredHmacCallbackIsVerifiedByIndependentReceiver() = runTest {
        val secret = "callback-hmac-test-secret"
        val captured = mutableListOf<CapturedRequest>()
        val transport = TransportFactory(MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes()
            captured += CapturedRequest(request.headers, body)
            respond("", HttpStatusCode.OK)
        })

        try {
            val app = createJvmUniversalOid4vpTestAppGraph(this)
            val sessionInstance =
                app.userContextManager
                    .getAnonymous()
                    .sessionContextManager
                    .createOrGetFromId("callback-signing-success", principalType = PrincipalType.USER)
            val sessionEventService = (sessionInstance.graph as SessionEventService.Graph).sessionEventService
            val eventStore = (app as EventStore.Graph).eventStore
            val correlationId = "callback-signing-success-correlation"
            val created = session(correlationId, FIXED_SECRET_REF, null)
            val updated = created.copy(
                status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
                updatedAt = created.updatedAt + 1,
            )

            SessionStatusEventEmitterImpl(
                sessionEventService = sessionEventService,
                callbackDispatcher = KtorAuthorizationSessionCallbackDispatcher(
                    transport,
                    OpaqueSecretResolver { Ok(secret) },
                ),
            ).onStatusChange(updated, created.status)

            val request = captured.single()
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA256"))
            val expected =
                mac.doFinal(request.body).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            assertEquals("${CallbackSigning.HMAC_SHA256_PREFIX}$expected", request.headers[CallbackSigning.SIGNATURE_HEADER])

            val events = eventStore.getRecent(100).getOrElse { error("event store read failed: $it") }
                .filter { it.context.correlationId == correlationId }
            assertTrue(events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_DISPATCHED })
            assertFalse(events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_FAILED })
        } finally {
            transport.close()
        }
    }

    @Test
    fun throwingSecretResolverFailsAsSafeCallbackEventBeforeHttp() = runTest {
        val captured = mutableListOf<CapturedRequest>()
        val transport = TransportFactory(MockEngine { request ->
            captured += CapturedRequest(
                request.headers,
                (request.body as OutgoingContent.ByteArrayContent).bytes(),
            )
            respond("", HttpStatusCode.OK)
        })

        try {
            val app = createJvmUniversalOid4vpTestAppGraph(this)
            val sessionInstance = app.userContextManager.getAnonymous().sessionContextManager
                .createOrGetFromId("callback-signing-throwing-resolver", principalType = PrincipalType.USER)
            val eventStore = (app as EventStore.Graph).eventStore
            val correlationId = "callback-signing-throwing-resolver-correlation"
            val created = session(correlationId)
            val updated = created.copy(
                status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
                updatedAt = created.updatedAt + 1,
            )
            SessionStatusEventEmitterImpl(
                sessionEventService = (sessionInstance.graph as SessionEventService.Graph).sessionEventService,
                callbackDispatcher = KtorAuthorizationSessionCallbackDispatcher(
                    transport,
                    OpaqueSecretResolver { throw IllegalStateException("secret resolver detail") },
                ),
            ).onStatusChange(updated, created.status)

            val events = eventStore.getRecent(100).getOrElse { error("event store read failed: $it") }
                .filter { it.context.correlationId == correlationId }
            assertTrue(captured.isEmpty())
            assertTrue(events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_FAILED })
        } finally {
            transport.close()
        }
    }

    @Test
    fun throwingClientFactoryFailsAsSafeCallbackEventBeforeHttp() = runTest {
        val transport = TransportFactory(
            MockEngine { error("request must not be reached") },
            creationFailure = IllegalStateException("factory detail"),
        )

        try {
            val app = createJvmUniversalOid4vpTestAppGraph(this)
            val sessionInstance = app.userContextManager.getAnonymous().sessionContextManager
                .createOrGetFromId("callback-signing-throwing-factory", principalType = PrincipalType.USER)
            val eventStore = (app as EventStore.Graph).eventStore
            val correlationId = "callback-signing-throwing-factory-correlation"
            val created = session(correlationId)
            val updated = created.copy(
                status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
                updatedAt = created.updatedAt + 1,
            )
            SessionStatusEventEmitterImpl(
                sessionEventService = (sessionInstance.graph as SessionEventService.Graph).sessionEventService,
                callbackDispatcher = KtorAuthorizationSessionCallbackDispatcher(
                    transport,
                    OpaqueSecretResolver { Ok("callback-hmac-test-secret") },
                ),
            ).onStatusChange(updated, created.status)

            val events = eventStore.getRecent(100).getOrElse { error("event store read failed: $it") }
                .filter { it.context.correlationId == correlationId }
            assertTrue(events.any { it.type == UniversalOid4vpEventTypes.CALLBACK_FAILED })
        } finally {
            transport.close()
        }
    }

    private fun session(
        correlationId: String,
        secretRef: String? = FIXED_SECRET_REF,
        signing: com.sphereon.core.api.http.callback.CallbackSigningAlgorithm? = com.sphereon.core.api.http.callback.CallbackSigningAlgorithm.HMAC_SHA256,
    ): AuthorizationSession {
        val now = 1_800_000_000_000L
        return AuthorizationSession(
            instanceId = "callback-signing-instance",
            sessionId = "callback-signing-session",
            correlationId = correlationId,
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    DcqlCredentialQuery(
                        id = "callback-credential",
                        format = "dc+sd-jwt",
                        meta = sdJwtVcMeta("urn:test:callback"),
                    ),
                ),
            ),
            authorizationRequest = AuthorizationRequest(
                clientId = "https://verifier.example.test",
                redirectUri = "https://verifier.example.test/callback",
                state = correlationId,
            ),
            status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
            callback = AuthorizationSessionCallbackConfig(
                url = "https://callback.example.test/status",
                statuses = listOf(AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED),
                secretRef = secretRef,
                signing = signing,
            ),
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 600_000,
        )
    }

    private data class CapturedRequest(
        val headers: io.ktor.http.Headers,
        val body: ByteArray,
    )

    private class TransportFactory(
        private val engine: MockEngine,
        private val creationFailure: Throwable? = null,
    ) : HttpClientFactory {
        private val clients = mutableListOf<HttpClient>()

        override fun createClient(options: HttpClientOptions): HttpClient {
            creationFailure?.let { throw it }
            return HttpClient(engine) {
                expectSuccess = false
                followRedirects = options.followRedirects
                if (options.enableContentNegotiation) {
                    install(ContentNegotiation) { options.contentNegotiationConfig?.invoke(this) }
                }
                options.additionalConfig?.invoke(this)
            }.also(clients::add)
        }

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()
        override fun getEngineTypeDefault(): HttpClientEngineType = error("metadata is not used")

        fun close() {
            clients.forEach(HttpClient::close)
            engine.close()
        }
    }

    private companion object {
        const val FIXED_SECRET_REF = "sec_test_callback_signing"
    }
}
