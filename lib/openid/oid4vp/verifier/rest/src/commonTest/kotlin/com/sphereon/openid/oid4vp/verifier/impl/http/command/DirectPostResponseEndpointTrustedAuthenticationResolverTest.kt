/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.verifier.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
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
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.verifier.DirectPostHandledResponse
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.config.ResponseEncryptionKeyConfig
import com.sphereon.openid.oid4vp.verifier.impl.http.command.DirectPostResponseEndpointCommandImpl
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCreateArgs
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionError
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.spi.VerifierTrustedAuthenticationRequest
import com.sphereon.openid.oid4vp.verifier.spi.VerifierTrustedAuthenticationResolver
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.common.store.StoreMetadata
import com.sphereon.openid.oid4vp.common.store.StoredEntry
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Contract tests for the direct_post trust-resolution seam.
 *
 * The endpoint must obtain trust only from the resolver's authoritative execution/session
 * context, pass the resolved entries unchanged to the service command, and stop before calling
 * that command when the resolver cannot produce a trusted configuration.
 */
class DirectPostResponseEndpointTrustedAuthenticationResolverTest {
    @Test
    fun `resolver receives authoritative tenant and persisted session context and list is forwarded`() =
        runTest {
            val fixture = fixture()
            var received: VerifierTrustedAuthenticationRequest? = null
            val trusted = trustedAuthentication()
            val resolver =
                object : VerifierTrustedAuthenticationResolver {
                    override suspend fun resolveTrustedAuthentications(
                        request: VerifierTrustedAuthenticationRequest,
                    ): IdkResult<List<TrustedAuthenticationResolution>, IdkError> {
                        received = request
                        return Ok(listOf(trusted))
                    }
                }

            val result = fixture.endpoint(resolver).execute(request())

            assertTrueOk(result)
            val receivedRequest = assertNotNull(received)
            assertEquals("tenant-from-session-execution", receivedRequest.tenantId)
            assertEquals("verifier-instance-persisted", receivedRequest.verifierInstanceId)
            assertEquals("verifier-business-id", receivedRequest.verifierId)
            assertEquals("dcql-query-id", receivedRequest.dcqlQueryId)
            assertEquals("template-id", receivedRequest.templateId)
            assertEquals(fixture.session.authorizationRequest, receivedRequest.originalRequest)
            assertEquals(fixture.session.dcqlQuery, receivedRequest.dcqlQuery)

            assertEquals(1, fixture.handle.invocations)
            assertEquals(listOf(trusted), fixture.handle.lastArgs!!.trustedAuthentications)
            // Request transport data cannot override the authenticated execution tenant.
            assertEquals("attacker-supplied-tenant", request().resolvedTenantId)
        }

    @Test
    fun `resolver error fails closed and handler is not called`() =
        runTest {
            val fixture = fixture()
            val resolverError = IdkError.fromString("trusted verifier configuration unavailable", code = "TRUST_CONFIG_UNAVAILABLE")
            val resolver =
                object : VerifierTrustedAuthenticationResolver {
                    override suspend fun resolveTrustedAuthentications(
                        request: VerifierTrustedAuthenticationRequest,
                    ): IdkResult<List<TrustedAuthenticationResolution>, IdkError> =
                        IdkResult.err(resolverError)
                }

            val result = fixture.endpoint(resolver).execute(request())

            assertFalse(result.isOk)
            assertEquals("TRUST_CONFIG_UNAVAILABLE", result.error.code)
            assertEquals(0, fixture.handle.invocations)
        }

    private fun fixture(): Fixture {
        val session = testSession()
        return Fixture(
            session = session,
            execution = TestSessionExecution(),
            handle = CapturingHandleDirectPostResponseCommand(),
            store = SingleSessionStore(session),
        )
    }

    private fun request(): GenericHttpRequest =
        GenericHttpRequest.withTextBody(
            method = "POST",
            path = "/auth/response",
            body = "state=session-correlation&vp_token=opaque-vp-token",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        ).copy(resolvedTenantId = "attacker-supplied-tenant")

    private fun trustedAuthentication(): TrustedAuthenticationResolution =
        TrustedAuthenticationResolution(
            controller = "https://holder.example",
            trustedJwks =
                Json.parseToJsonElement(
                    """{"keys":[{"kty":"EC","crv":"P-256","x":"WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA","y":"F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I","kid":"holder-key"}]}""",
                ) as JsonObject,
        )

    private fun testSession(): AuthorizationSession {
        val dcql =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "credential-query",
                            format = "jwt_vc_json",
                            meta = Json.parseToJsonElement("""{"type_values":[["VerifiableCredential"]]}""") as JsonObject,
                        ),
                    ),
            )
        return AuthorizationSession(
            instanceId = "verifier-instance-persisted",
            sessionId = "session-id",
            correlationId = "session-correlation",
            queryId = "dcql-query-id",
            dcqlQuery = dcql,
            dcqlQueryId = "dcql-query-id",
            verifierId = "verifier-business-id",
            templateId = "template-id",
            authorizationRequest =
                AuthorizationRequest(
                    clientId = "https://verifier.example",
                    redirectUri = "https://verifier.example/callback",
                    state = "session-correlation",
                    nonce = "request-nonce",
                ),
            status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
            createdAt = 1L,
            updatedAt = 1L,
            expiresAt = Long.MAX_VALUE,
        )
    }

    private class Fixture(
        val session: AuthorizationSession,
        val execution: SessionExecution,
        val handle: CapturingHandleDirectPostResponseCommand,
        val store: AuthorizationSessionStore,
    ) {
        fun endpoint(resolver: VerifierTrustedAuthenticationResolver): DirectPostResponseEndpointCommandImpl =
            DirectPostResponseEndpointCommandImpl(
                execution = execution,
                handleDirectPostCommand = handle,
                authorizationSessionStore = store,
                responseEncryptionKeyConfig = object : ResponseEncryptionKeyConfig {
                    override suspend fun resolveEncryptionKeyName(verifierInstanceId: String): String? = null
                },
                trustedAuthenticationResolver = Provider { resolver },
            )
    }

    private class CapturingHandleDirectPostResponseCommand : HandleDirectPostResponseCommand {
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<HandleDirectPostResponseArgs> = typeToken<HandleDirectPostResponseArgs>()
        override val outputTypeToken: TypeToken<DirectPostHandledResponse> = typeToken<DirectPostHandledResponse>()

        var invocations: Int = 0
        var lastArgs: HandleDirectPostResponseArgs? = null

        override suspend fun execute(args: HandleDirectPostResponseArgs): IdkResult<DirectPostHandledResponse, IdkError> {
            invocations++
            lastArgs = args
            return Ok(DirectPostHandledResponse("https://verifier.example/callback", "response-code", 2L))
        }
    }

    private class SingleSessionStore(
        private val session: AuthorizationSession,
    ) : AuthorizationSessionStore {
        override suspend fun getByCorrelationId(correlationId: String): IdkResult<AuthorizationSession?, IdkError> =
            Ok(session.takeIf { it.correlationId == correlationId })

        override suspend fun createSession(
            correlationId: String?,
            args: AuthorizationSessionCreateArgs,
            ttlSeconds: Long,
        ): IdkResult<AuthorizationSession, IdkError> = unused()

        override suspend fun updateStatus(
            correlationId: String,
            status: AuthorizationSessionStatus,
            error: AuthorizationSessionError?,
        ): IdkResult<AuthorizationSession, IdkError> = unused()

        override suspend fun storeResponse(
            correlationId: String,
            parsedResponse: ParsedAuthorizationResponse,
        ): IdkResult<AuthorizationSession, IdkError> = unused()

        override suspend fun storeValidationResult(
            correlationId: String,
            validationResult: ValidationResult,
        ): IdkResult<AuthorizationSession, IdkError> = unused()

        override suspend fun getForRequestUri(
            correlationId: String,
            markRetrieved: Boolean,
        ): IdkResult<AuthorizationSession?, IdkError> = unused()

        override suspend fun put(
            key: String,
            value: AuthorizationSession,
            ttlSeconds: Long,
        ): IdkResult<StoreMetadata, IdkError> = unused()

        override suspend fun get(key: String): IdkResult<AuthorizationSession?, IdkError> = unused()

        override suspend fun getEntry(key: String): IdkResult<StoredEntry<AuthorizationSession>?, IdkError> = unused()

        override suspend fun delete(key: String): IdkResult<Boolean, IdkError> = unused()

        override suspend fun exists(key: String): IdkResult<Boolean, IdkError> = unused()

        override suspend fun touch(key: String, ttlSeconds: Long): IdkResult<Boolean, IdkError> = unused()

        override suspend fun cleanupExpired(): IdkResult<Int, IdkError> = unused()

        private fun <T> unused(): T = error("not used by direct_post resolver tests")
    }

    private class TestSessionExecution : SessionExecution {
        override val tenantId: String = "tenant-from-session-execution"
        override val principalId: String = "verifier-principal"
        override val correlationId: String = "endpoint-correlation"
        override val sessionContextManager: SessionContextManager get() = error("not used")
        override val sessionContext: SessionContext = NoOpSessionContext
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig
        override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
    }

    private object NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = error("not used")
        override val tenant: TenantConfigService get() = error("not used")
        override val principal: PrincipalConfigService get() = error("not used")
        override fun conf(level: ConfigLevel): ConfigService = error("not used")
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "direct-post-resolver-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw IllegalStateException("not used")
        override suspend fun setConfig(config: LoggerConfig): LogService = this
        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
        override fun toAsync(): AsyncLogService = throw IllegalStateException("not used")
    }

    private fun <T> assertTrueOk(result: IdkResult<T, IdkError>) {
        assertEquals(true, result.isOk, "expected successful endpoint result: $result")
    }
}
