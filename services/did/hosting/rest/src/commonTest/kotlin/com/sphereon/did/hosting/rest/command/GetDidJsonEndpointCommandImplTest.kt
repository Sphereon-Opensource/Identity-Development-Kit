package com.sphereon.did.hosting.rest.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import com.sphereon.did.hosting.DidHostingRegistry
import com.sphereon.did.hosting.HostedDid
import com.sphereon.did.hosting.rest.DidHostingApiConstants
import com.sphereon.did.hosting.rest.DidHostingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDidJsonEndpointCommandImplTest {
    @Test
    fun hostHeaderWithGatewayPortFallsBackToHostOnlyDidWebLocation() =
        runTest {
            val registry =
                RecordingRegistry(
                    hostedByLocation =
                        mapOf(
                            "ui174507.saas.localtest.me" to HostedDid("""{"id":"did:web:ui174507.saas.localtest.me"}""", "web"),
                        ),
                )
            val command = GetDidJsonEndpointCommandImpl(UnusedExecution, registry, DidHostingConfig())
            val adapter = TestAdapter(UnusedExecution, listOf(command))

            val response =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/.well-known/did.json",
                        headers = mapOf("Host" to "ui174507.saas.localtest.me:3443"),
                    ),
                )

            assertEquals(200, response.statusCode)
            assertEquals(DidHostingApiConstants.DID_JSON_MEDIA_TYPE, response.contentType)
            assertEquals("""{"id":"did:web:ui174507.saas.localtest.me"}""", response.bodyBytes?.decodeToString())
            assertEquals(
                listOf("ui174507.saas.localtest.me%3A3443", "ui174507.saas.localtest.me"),
                registry.requestedLocations,
            )
        }

    @Test
    fun portQualifiedDidWebLocationWinsBeforeHostOnlyFallback() =
        runTest {
            val registry =
                RecordingRegistry(
                    hostedByLocation =
                        mapOf(
                            "port.example.com%3A3443" to HostedDid("""{"id":"did:web:port.example.com%3A3443"}""", "web"),
                            "port.example.com" to HostedDid("""{"id":"did:web:port.example.com"}""", "web"),
                        ),
                )
            val command = GetDidJsonEndpointCommandImpl(UnusedExecution, registry, DidHostingConfig())
            val adapter = TestAdapter(UnusedExecution, listOf(command))

            val response =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/.well-known/did.json",
                        headers = mapOf("Host" to "port.example.com:3443"),
                    ),
                )

            assertEquals(200, response.statusCode)
            assertEquals("""{"id":"did:web:port.example.com%3A3443"}""", response.bodyBytes?.decodeToString())
            assertEquals(listOf("port.example.com%3A3443"), registry.requestedLocations)
        }

    private class RecordingRegistry(
        private val hostedByLocation: Map<String, HostedDid>,
    ) : DidHostingRegistry {
        val requestedLocations = mutableListOf<String>()

        override fun hostableMethods(): Set<String> = setOf("web")

        override suspend fun resolveDidJson(
            tenantId: String?,
            webLocation: String,
        ): IdkResult<HostedDid?, IdkError> {
            requestedLocations += webLocation
            return Ok(hostedByLocation[webLocation])
        }
    }

    private object UnusedExecution : SessionExecution {
        private val tenant =
            object : TenantContextData {
                override val tenantId: String = "tenant-1"
            }
        private val user =
            object : UserContext {
                override val id: String = "user-1"
                override val secureDetails: SecuredTenantContextDetails? = null
                override val tenant: TenantContextData = this@UnusedExecution.tenant
                override val principal: Any? = "user-1"
            }
        override val sessionContext: SessionContext =
            object : SessionContext {
                override val sessionId: String = "session-1"
                override val context: UserContext = user

                override fun isAnonymous(): Boolean = false
            }
        override val sessionContextManager: SessionContextManager =
            object : SessionContextManager {
                override val activeInstance: StateFlow<SessionInstance?> = MutableStateFlow(null)

                override fun getActive() = throw NotImplementedError()

                override fun hasActive() = false

                override fun getById(
                    sessionId: String,
                    makeActive: Boolean,
                ) = null

                override fun hasById(sessionId: String) = false

                override fun activateById(sessionId: String) = false

                override fun listIds() = emptySet<String>()

                override fun createOrGetFromCallbacks(sessionContextProvider: () -> SessionContext) = throw NotImplementedError()

                override fun createOrGetFromId(
                    sessionId: String,
                    correlationId: String,
                    makeActive: Boolean,
                ) = throw NotImplementedError()

                override fun destroyById(sessionId: String) {}

                override fun destroyAll() {}

                override fun getOrCreateBackgroundService(makeActive: Boolean) = throw NotImplementedError()

                override fun getAnonymous(makeActive: Boolean) = throw NotImplementedError()

                override fun getBackgroundServiceId() = "background"
            }
        override val log: SessionLogService =
            object : SessionLogService {
                override val sessionContext: SessionContext = this@UnusedExecution.sessionContext
                override val id: String = "session-1"
                override val isEnabled: Boolean = true
                override val scope: IdkScope = IdkScope.SESSION
                override val logManager: SessionLogManager =
                    object : SessionLogManager {
                        override suspend fun setGlobalConfig(config: LoggerConfig) = this

                        override suspend fun getGlobalConfig() = LoggerConfig.Default

                        override fun withTagAsync(
                            tag: String,
                            config: LoggerConfig?,
                        ) = throw NotImplementedError()

                        override fun withTag(
                            tag: String,
                            config: LoggerConfig?,
                        ) = throw NotImplementedError()
                    }

                override suspend fun setConfig(config: LoggerConfig): LogService = this

                override suspend fun getConfig() = LoggerConfig.Default

                override fun executeAsync(message: LogMessage) = Ok(Unit)

                override fun toAsync() = throw NotImplementedError()
            }
        override val conf: ContextConfig =
            object : ContextConfig {
                override val app: AppConfigService get() = throw NotImplementedError()
                override val tenant: TenantConfigService get() = throw NotImplementedError()
                override val principal: PrincipalConfigService get() = throw NotImplementedError()

                override fun conf(level: ConfigLevel) = throw NotImplementedError()
            }
    }

    private class TestAdapter(
        execution: SessionExecution,
        private val endpoints: List<HttpEndpointCommand>,
    ) : CommandBackedHttpAdapter(
            id = "did-hosting-test",
            execution = execution,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "",
                ),
        ),
        HttpAdapter {
        override val endpointCommands: List<HttpEndpointCommand> = endpoints
    }
}
