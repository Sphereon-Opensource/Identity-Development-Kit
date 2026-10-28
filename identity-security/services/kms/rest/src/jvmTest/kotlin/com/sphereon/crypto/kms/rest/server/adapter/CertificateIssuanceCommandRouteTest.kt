/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

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
import com.sphereon.core.api.http.dispatch.DefaultHttpAdapterDispatcher
import com.sphereon.core.api.http.dispatch.DefaultHttpAdapterRouteSelector
import com.sphereon.core.api.http.dispatch.HttpAdapterCatalog
import com.sphereon.core.api.http.dispatch.HttpAdapterCatalogDiagnostics
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.session.CommandInvocationBoundary
import com.sphereon.core.api.session.CommandInvocationContext
import com.sphereon.core.api.session.CommandLifecycleInterceptor
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.crypto.kms.rest.api.command.GetCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.command.IssueCertificateFromCsrServiceCommand
import com.sphereon.crypto.kms.rest.api.command.IssueCertificateServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListCertificateReferencesServiceCommand
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateResponse
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateFromCsrRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateRequest
import com.sphereon.crypto.kms.rest.server.command.IssueCertificateFromCsrServiceCommandImpl
import com.sphereon.crypto.kms.rest.server.command.IssueCertificateServiceCommandImpl
import com.sphereon.crypto.kms.rest.server.service.CertificatesRestService
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CertificateIssuanceCommandRouteTest {
    @Test
    fun universalSelectedIssueRoutesExecuteAsExternalRootAndNestedMintAsChild() =
        runTest {
            val seen = mutableListOf<CommandInvocationContext>()
            val service = recordingService(seen)
            val execution = testExecution()
            val adapter = adapter(execution, service)
            val selector = DefaultHttpAdapterRouteSelector(catalog(adapter))
            val dispatcher = DefaultHttpAdapterDispatcher(mapOf(adapter.id to lazyOf(adapter)), execution)

            listOf(
                Triple("POST", "/certificates/issue", ISSUE_BODY),
                Triple("POST", "/certificates/issue-from-csr", CSR_BODY),
            ).forEach { (method, path, body) ->
                val selected = selector.select(method, path, setOf(CertificatesHttpAdapter.ID))
                val match = assertIs<HttpAdapterRouteSelection.Selected>(selected).match
                assertEquals(CertificatesHttpAdapter.ID, match.adapterId)
                assertEquals(commandFor(path), match.handlerCommandId)

                val request = GenericHttpRequest.withTextBody(method = method, path = path, body = body)
                withContext(CommandInvocationContext.externalBoundary()) {
                    dispatcher.dispatch(request, match)
                }
            }

            assertEquals(4, seen.size)
            seen.forEachIndexed { index, context ->
                assertEquals(CommandInvocationBoundary.EXTERNAL, context.boundary)
                assertEquals(if (index % 2 == 0) 0 else 1, context.commandDepth)
            }
        }

    @Test
    fun lifecycleDenialStopsIssuanceServiceBeforeSigningWork() =
        runTest {
            var issuanceCalls = 0
            val execution = testExecution(
                chain =
                    object : CommandLifecycleInterceptorChain {
                        override val interceptors: List<CommandLifecycleInterceptor> =
                            listOf(
                                object : CommandLifecycleInterceptor {
                                    override val name: String = "test-deny-issuance"

                                    override suspend fun beforeExecute(
                                        context: com.sphereon.core.api.session.CommandExecutionContext,
                                        args: Any,
                                    ) = com.sphereon.core.api.session.InterceptorVerdict.Deny("test denial")
                                },
                            )
                    },
            )
            val service =
                object : CertificatesRestService by unused(CertificatesRestService::class.java) {
                    override suspend fun issueCertificate(request: IssueCertificateRequest): CertificateResponse {
                        issuanceCalls++
                        error("denied command reached certificate issuance")
                    }

                    override suspend fun issueCertificateFromCsr(request: IssueCertificateFromCsrRequest): CertificateResponse {
                        issuanceCalls++
                        error("denied command reached certificate issuance")
                    }
                }
            val adapter = adapter(execution, service)
            val dispatcher = DefaultHttpAdapterDispatcher(mapOf(adapter.id to lazyOf(adapter)), execution)
            val selector = DefaultHttpAdapterRouteSelector(catalog(adapter))

            for ((path, body) in listOf("/certificates/issue" to ISSUE_BODY, "/certificates/issue-from-csr" to CSR_BODY)) {
                val selected = selector.select("POST", path, setOf(CertificatesHttpAdapter.ID))
                val match = assertIs<HttpAdapterRouteSelection.Selected>(selected).match
                withContext(CommandInvocationContext.externalBoundary()) {
                    dispatcher.dispatch(GenericHttpRequest.withTextBody("POST", path, body), match)
                }
            }

            assertEquals(0, issuanceCalls)
        }

    @Test
    fun commandDescriptorsMatchTheSelectedIssueAndCsrRoutes() {
        assertEquals("kms.certificates.issue", IssueCertificateServiceCommand.COMMAND_ID)
        assertEquals("/certificates/issue", IssueCertificateServiceCommand.ENDPOINT.pathPattern)
        assertEquals("kms.certificates.issue-from-csr", IssueCertificateFromCsrServiceCommand.COMMAND_ID)
        assertEquals("/certificates/issue-from-csr", IssueCertificateFromCsrServiceCommand.ENDPOINT.pathPattern)
    }

    private fun adapter(execution: SessionExecution, service: CertificatesRestService) =
        CertificatesHttpAdapter(
            certificatesService = service,
            issueCertificateCommand = IssueCertificateServiceCommandImpl(execution, service),
            issueCertificateFromCsrCommand = IssueCertificateFromCsrServiceCommandImpl(execution, service),
            registerCommand = unused(RegisterCertificateReferenceServiceCommand::class.java),
            listCertificateReferencesCommand = unused(ListCertificateReferencesServiceCommand::class.java),
            getCertificateReferenceCommand = unused(GetCertificateReferenceServiceCommand::class.java),
        )

    private fun catalog(adapter: CertificatesHttpAdapter) =
        object : HttpAdapterCatalog {
            override val descriptions = listOf(adapter.describe())
            override val diagnostics = HttpAdapterCatalogDiagnostics(emptyList())
            override fun describeAll() = descriptions
            override fun descriptionById(id: String) = descriptions.singleOrNull { it.id == id }
            override fun requireNoCollisions() = Unit
        }

    private fun recordingService(seen: MutableList<CommandInvocationContext>): CertificatesRestService {
        val child =
            object : TypedServiceCommandAdapter<Unit, Unit, IdkError>(
                commandId = "kms.certificates.test-child",
                execution = testExecution(),
                inputTypeToken = typeToken<Unit>(),
                outputTypeToken = typeToken<Unit>(),
            ) {
                override suspend fun doExecute(args: Unit, applyDuring: (Unit) -> Unit): IdkResult<Unit, IdkError> {
                    val current = currentCoroutineContext()[CommandInvocationContext]
                    assertNotNull(current)
                    seen += current
                    return Ok(Unit)
                }
            }
        return object : CertificatesRestService by unused(CertificatesRestService::class.java) {
            override suspend fun issueCertificate(request: IssueCertificateRequest): CertificateResponse {
                currentCoroutineContext()[CommandInvocationContext]?.let(seen::add)
                child.execute(Unit)
                error("test issuance stopped after invocation-context capture")
            }

            override suspend fun issueCertificateFromCsr(request: IssueCertificateFromCsrRequest): CertificateResponse {
                currentCoroutineContext()[CommandInvocationContext]?.let(seen::add)
                child.execute(Unit)
                error("test issuance stopped after invocation-context capture")
            }
        }
    }

    private fun commandFor(path: String) =
        if (path.endsWith("issue-from-csr")) IssueCertificateFromCsrServiceCommand.COMMAND_ID else IssueCertificateServiceCommand.COMMAND_ID

    private fun testExecution(chain: CommandLifecycleInterceptorChain = EmptyInterceptorChain): SessionExecution {
        val context =
            object : SessionContext {
                override val sessionId = "certificate-issuance-test-session"
                override val context: UserContext =
                    object : UserContext {
                        override val id = "certificate-issuance-test-user"
                        override val tenant: TenantContextData = object : TenantContextData {
                            override val tenantId = "certificate-issuance-test-tenant"
                        }
                        override val principal: Any? = "certificate-issuance-test-principal"
                        override val secureDetails = null
                    }
            }
        return object : SessionExecution {
            override val tenantId = "certificate-issuance-test-tenant"
            override val principalId = "certificate-issuance-test-principal"
            override val correlationId = "certificate-issuance-test-correlation"
            override val sessionContextManager: SessionContextManager get() = error("unused")
            override val sessionContext = context
            override val log: SessionLogService = TestLog(context)
            override val interceptorChain = chain
            override val conf: ContextConfig = TestContextConfig
        }
    }

    private object TestContextConfig : ContextConfig {
        override val app: AppConfigService get() = error("unused")
        override val tenant: TenantConfigService get() = error("unused")
        override val principal: PrincipalConfigService get() = error("unused")
        override fun conf(level: ConfigLevel): ConfigService = error("unused")
    }

    private class TestLog(override val sessionContext: com.sphereon.di.session.SessionContext) : SessionLogService {
        override val id = "certificate-issuance-test-log"
        override val isEnabled = false
        override val scope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = kotlin.error("unused")
        override suspend fun setConfig(config: LoggerConfig): LogService = this
        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
        override fun toAsync(): AsyncLogService = kotlin.error("unused")
    }

    private fun <T> unused(type: Class<T>): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> error("${type.simpleName}.${method.name} must not be called") })

    private companion object {
        const val ISSUE_BODY =
            """{"issuerKeyInfo":{},"issuer":{},"subjectKeyInfo":{"key":{"kty":"EC","crv":"P-256","x":"AA","y":"AA"}},"subject":{},"serialNumber":1}"""
        const val CSR_BODY =
            """{"issuerKeyInfo":{},"issuer":{},"subjectKeyInfo":{"key":{"kty":"EC","crv":"P-256","x":"AA","y":"AA"}},"csr":{"commonName":"test","serialNumber":1,"der":"AA=="}}"""
    }
}
