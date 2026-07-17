/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
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
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.holder.impl.SelectAuthorizationServerCommandImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SelectAuthorizationServerCommandImpl].
 *
 * These exercise the AS-selection rule (preferred > first authorization_servers entry > issuer
 * URL fallback, trimEnd('/')) and confirm metadata resolution is delegated entirely to the
 * injected [FetchAuthorizationServerMetadataCommand] (the one AS-selection command shared with
 * lib-oauth2-client) rather than a private raw-ktor fetch.
 */
class SelectAuthorizationServerTest {
    private fun issuerMetadata(
        credentialIssuer: String = "https://issuer.example.com",
        authorizationServers: List<String>? = null,
    ): CredentialIssuerMetadata =
        CredentialIssuerMetadata(
            credentialIssuer = credentialIssuer,
            authorizationServers = authorizationServers,
            credentialEndpoint = "$credentialIssuer/credential",
            credentialConfigurationsSupported = emptyMap(),
        )

    private fun metadataFor(
        issuer: String,
        tokenEndpoint: String = "$issuer/token",
        pushedAuthorizationRequestEndpoint: String? = null,
        authorizationEndpoint: String? = null,
    ): AuthorizationServerMetadata =
        AuthorizationServerMetadata(
            issuer = issuer,
            tokenEndpoint = tokenEndpoint,
            authorizationEndpoint = authorizationEndpoint,
            pushedAuthorizationRequestEndpoint = pushedAuthorizationRequestEndpoint,
        )

    // ------------------------------------------------------------------
    // Selection rule (preferred > authorization_servers.first() > credentialIssuer)
    // ------------------------------------------------------------------

    @Test
    fun preferredAuthorizationServerTakesPrecedenceOverIssuerList() =
        runTest {
            val fetch = FakeFetchCommand { args -> Ok(metadataFor(args.issuer)) }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata = issuerMetadata(authorizationServers = listOf("https://as-from-list.example.com")),
                        preferredAuthorizationServer = "https://preferred.example.com",
                    ),
                )

            assertTrue(result.isOk, "expected success, got ${if (result.isErr) result.error else ""}")
            assertEquals("https://preferred.example.com", result.value.authorizationServerUrl)
            assertEquals("https://preferred.example.com", fetch.lastArgs?.issuer)
        }

    @Test
    fun firstAuthorizationServerFromIssuerListIsUsedWhenNoPreferred() =
        runTest {
            val fetch = FakeFetchCommand { args -> Ok(metadataFor(args.issuer)) }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata =
                            issuerMetadata(
                                authorizationServers = listOf("https://as-one.example.com", "https://as-two.example.com"),
                            ),
                        preferredAuthorizationServer = null,
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("https://as-one.example.com", result.value.authorizationServerUrl)
            assertEquals("https://as-one.example.com", fetch.lastArgs?.issuer)
        }

    @Test
    fun credentialIssuerUrlIsUsedAsFallbackWhenNoAuthorizationServers() =
        runTest {
            val fetch = FakeFetchCommand { args -> Ok(metadataFor(args.issuer)) }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata = issuerMetadata(credentialIssuer = "https://issuer.example.com", authorizationServers = null),
                        preferredAuthorizationServer = null,
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("https://issuer.example.com", result.value.authorizationServerUrl)
            assertEquals("https://issuer.example.com", fetch.lastArgs?.issuer)
        }

    @Test
    fun trailingSlashIsTrimmedFromSelectedAuthorizationServerUrl() =
        runTest {
            val fetch = FakeFetchCommand { args -> Ok(metadataFor(args.issuer)) }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata = issuerMetadata(),
                        preferredAuthorizationServer = "https://preferred.example.com/",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("https://preferred.example.com", result.value.authorizationServerUrl)
            assertEquals("https://preferred.example.com", fetch.lastArgs?.issuer)
        }

    // ------------------------------------------------------------------
    // Typed metadata propagation (the point of this task: no more JsonObject hand-parsing)
    // ------------------------------------------------------------------

    @Test
    fun typedMetadataFromFetchCommandIsPropagatedIntoResolvedAuthorizationServer() =
        runTest {
            val fetch =
                FakeFetchCommand { args ->
                    Ok(
                        metadataFor(
                            issuer = args.issuer,
                            tokenEndpoint = "${args.issuer}/token",
                            authorizationEndpoint = "${args.issuer}/authorize",
                            pushedAuthorizationRequestEndpoint = "${args.issuer}/par",
                        ),
                    )
                }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata = issuerMetadata(credentialIssuer = "https://as.example.com"),
                        preferredAuthorizationServer = null,
                    ),
                )

            assertTrue(result.isOk)
            val resolved = result.value
            assertEquals("https://as.example.com", resolved.issuer)
            assertEquals("https://as.example.com/token", resolved.tokenEndpoint)
            assertEquals("https://as.example.com/authorize", resolved.authorizationEndpoint)
            assertEquals("https://as.example.com/par", resolved.pushedAuthorizationRequestEndpoint)
            // The typed AuthorizationServerMetadata object itself is carried through unchanged.
            assertEquals("https://as.example.com/token", resolved.metadata.tokenEndpoint)
        }

    @Test
    fun absentOptionalEndpointsRemainNullOnResolvedAuthorizationServer() =
        runTest {
            val fetch = FakeFetchCommand { args -> Ok(metadataFor(args.issuer)) }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata = issuerMetadata(credentialIssuer = "https://as.example.com"),
                        preferredAuthorizationServer = null,
                    ),
                )

            assertTrue(result.isOk)
            assertNull(result.value.pushedAuthorizationRequestEndpoint)
            assertNull(result.value.authorizationEndpoint)
            assertNull(result.value.interactiveAuthorizationEndpoint)
            assertNull(result.value.jwksUri)
        }

    // ------------------------------------------------------------------
    // Fetch failure maps to error (propagated as-is, no invented codes)
    // ------------------------------------------------------------------

    @Test
    fun fetchFailurePropagatesTheFetchCommandsErrorUnchanged() =
        runTest {
            val fetchError =
                IdkError.fromString(
                    message = "Could not fetch AS metadata from https://as.example.com",
                    code = "METADATA_NOT_FOUND",
                )
            val fetch = FakeFetchCommand { Err(fetchError) }
            val command = SelectAuthorizationServerCommandImpl(createTestSessionExecution(), fetch)

            val result =
                command.execute(
                    SelectAuthorizationServerArgs(
                        issuerMetadata = issuerMetadata(credentialIssuer = "https://as.example.com"),
                        preferredAuthorizationServer = null,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("METADATA_NOT_FOUND", result.error.code)
            assertEquals(fetchError, result.error)
        }
}

/**
 * Minimal fake of [FetchAuthorizationServerMetadataCommand] that records the last args it was
 * called with and returns a caller-supplied result. Avoids any HTTP or discovery-URL logic:
 * that behavior now lives exclusively behind the injected command (lib-oauth2-client), which
 * has its own discovery-order tests.
 */
private class FakeFetchCommand(
    private val handler: (FetchServerMetadataArgs) -> IdkResult<AuthorizationServerMetadata, IdkError>,
) : FetchAuthorizationServerMetadataCommand {
    var lastArgs: FetchServerMetadataArgs? = null
        private set

    override val isEnabled: Boolean = true
    override val inputTypeToken: TypeToken<FetchServerMetadataArgs> = typeToken<FetchServerMetadataArgs>()
    override val outputTypeToken: TypeToken<AuthorizationServerMetadata> = typeToken<AuthorizationServerMetadata>()

    override suspend fun execute(args: FetchServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError> {
        lastArgs = args
        return handler(args)
    }
}

/**
 * Test-friendly [SessionExecution] that can be constructed without DI, mirroring the pattern
 * used by lib/data/credential-definition/impl's commonTest TestSupport.
 */
private fun createTestSessionExecution(sessionId: String = "select-as-test-session"): SessionExecution {
    val tenantContextData =
        object : TenantContextData {
            override val tenantId: String = "00000000-0000-0000-0000-000000000001"
        }
    val userContext =
        object : UserContext {
            override val id: String = "test-user-context"
            override val secureDetails: SecuredTenantContextDetails? = null
            override val tenant: TenantContextData = tenantContextData
            override val principal: Any? = null
        }
    val sessionContext =
        object : SessionContext {
            override val sessionId: String = sessionId
            override val context: UserContext = userContext

            override fun isAnonymous(): Boolean = false
        }
    val sessionContextManager =
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
    val contextConfig =
        object : ContextConfig {
            override val app: AppConfigService get() = throw NotImplementedError()
            override val tenant: TenantConfigService get() = throw NotImplementedError()
            override val principal: PrincipalConfigService get() = throw NotImplementedError()

            override fun conf(level: ConfigLevel) = throw NotImplementedError()
        }
    val sessionLogManager =
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
    val sessionLogService =
        object : SessionLogService {
            override val sessionContext: SessionContext = sessionContext
            override val id: String = sessionId
            override val isEnabled: Boolean = true
            override val scope: IdkScope = IdkScope.SESSION
            override val logManager: SessionLogManager = sessionLogManager

            override suspend fun setConfig(config: LoggerConfig): LogService = this

            override suspend fun getConfig() = LoggerConfig.Default

            override fun executeAsync(message: LogMessage) = Ok(Unit)

            override fun toAsync() = throw NotImplementedError()
        }
    return object : SessionExecution {
        override val sessionContextManager: SessionContextManager = sessionContextManager
        override val sessionContext: SessionContext = sessionContext
        override val log: SessionLogService = sessionLogService
        override val conf: ContextConfig = contextConfig
    }
}
