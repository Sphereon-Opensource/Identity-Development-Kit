/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.trust.etsi.resolution

import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.x509.X509VerificationRequestType
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListData
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ETSIIdentifierResolutionServiceSessionContextSafetyTest {
    @Test
    fun etsiTrustListSupportsWithContextDelegatesToContextFreeSupports() =
        runTest {
            val service = createTrustListService("etsi-trustlist-supports-execution")
            val forgedService = createTrustListService("etsi-trustlist-supports-forged")
            val opts = ExternalIdentifierETSITslOpts(identifier = "https://example.org/eu-tsl.xml")
            val unsupported = ExternalIdentifierDidOpts("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

            assertTrue(service.supports(opts))
            assertEquals(service.supports(opts), forgedService.supports(opts))

            assertFalse(service.supports("invalid"))
            assertFalse(forgedService.supports("invalid"))

            assertFalse(service.supports(unsupported))
            assertEquals(service.supports(unsupported), forgedService.supports(unsupported))
        }

    @Test
    fun x509ValidationSupportsWithContextDelegatesToContextFreeSupports() =
        runTest {
            val service = createX509ValidationService("etsi-x509-supports-execution")
            val forgedService = createX509ValidationService("etsi-x509-supports-forged")
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf("dummy-certificate")),
                )
            val unsupported = ExternalIdentifierDidOpts("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

            assertTrue(service.supports(opts))
            assertEquals(service.supports(opts), forgedService.supports(opts))

            assertFalse(service.supports("invalid"))
            assertFalse(forgedService.supports("invalid"))

            assertFalse(service.supports(unsupported))
            assertEquals(service.supports(unsupported), forgedService.supports(unsupported))
        }

    @Test
    fun etsiTrustListExecuteUnsupportedOptsIgnoresForgedSessionContext() =
        runTest {
            val service = createTrustListService("etsi-trustlist-execution")
            val forgedService = createTrustListService("etsi-trustlist-forged")
            val unsupported = ExternalIdentifierDidOpts("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

            val resultWithExecutionContext = service.execute(unsupported)
            val resultWithForgedContext = forgedService.execute(unsupported)

            assertTrue(resultWithExecutionContext.isErr)
            assertTrue(resultWithForgedContext.isErr)
            assertEquals(resultWithExecutionContext.error.code, resultWithForgedContext.error.code)
        }

    @Test
    fun x509ValidationExecuteUnsupportedOptsIgnoresForgedSessionContext() =
        runTest {
            val service = createX509ValidationService("etsi-x509-execution")
            val forgedService = createX509ValidationService("etsi-x509-forged")
            val unsupported = ExternalIdentifierDidOpts("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

            val resultWithExecutionContext = service.execute(unsupported)
            val resultWithForgedContext = forgedService.execute(unsupported)

            assertTrue(resultWithExecutionContext.isErr)
            assertTrue(resultWithForgedContext.isErr)
            assertEquals(resultWithExecutionContext.error.code, resultWithForgedContext.error.code)
        }

    private fun createTrustListService(sessionId: String): ETSITrustListIdentifierResolutionServiceImpl =
        ETSITrustListIdentifierResolutionServiceImpl(
            execution = TestSessionExecution(createAnonymousSessionContext(sessionId, "$sessionId-correlation")),
            trustListResolvers = setOf(MockTrustListResolver()),
            trustListParser = MockTrustListParser(),
        )

    private fun createX509ValidationService(sessionId: String): X509ETSIValidationIdentifierResolutionServiceImpl =
        X509ETSIValidationIdentifierResolutionServiceImpl(
            execution = TestSessionExecution(createAnonymousSessionContext(sessionId, "$sessionId-correlation")),
            trustListResolvers = setOf(MockTrustListResolver()),
            trustListParser = MockTrustListParser(),
            x509VerifyService = MockX509VerifyService(),
        )

    private class TestSessionExecution(
        override val sessionContext: SessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for this test")
        override val log: SessionLogService = MockSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService
            get() = throw NotImplementedError("Not needed for this test")
        override val tenant: TenantConfigService
            get() = throw NotImplementedError("Not needed for this test")
        override val principal: PrincipalConfigService
            get() = throw NotImplementedError("Not needed for this test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for this test")
    }

    private class MockSessionLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionLogService {
        private val manager = MockSessionLogManager(sessionContext)
        override val logManager: SessionLogManager = manager
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "mock-session-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): SessionLogService = this

        override fun executeAsync(message: LogMessage) = IdkOkResult(Unit)

        override fun toAsync(): AsyncLogService = MockAsyncLogService(sessionContext)
    }

    private class MockSessionLogManager(
        private val sessionContext: SessionContext,
    ) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): SessionLogManager = this

        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default

        override fun withTagAsync(
            tag: String,
            config: LoggerConfig?,
        ): AsyncLogService = MockAsyncLogService(sessionContext)

        override fun withTag(
            tag: String,
            config: LoggerConfig?,
        ): SessionLogService = MockSessionLogService(sessionContext)
    }

    private class MockAsyncLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : AsyncLogService {
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "mock-async-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this

        override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)

        override fun toSync(): SessionLogService = MockSessionLogService(sessionContext)
    }

    private class MockTrustListResolver : TrustListResolver {
        override fun getId(): String = "mock-trust-list-resolver"

        override suspend fun resolve(
            uri: String,
            options: ResolutionOptions,
        ): TrustListData = throw NotImplementedError("Not needed for this test")

        override fun supports(uri: String): Boolean = false
    }

    private class MockTrustListParser : ETSITrustListParser {
        override fun parseFromBytes(xmlData: ByteArray): ETSILoTE = throw NotImplementedError("Not needed for this test")

        override fun parseFromString(xmlString: String): ETSILoTE = throw NotImplementedError("Not needed for this test")

        override fun validate(xmlData: ByteArray): Boolean = throw NotImplementedError("Not needed for this test")

        override fun parseFromJson(jsonString: String): ETSILoTE = throw NotImplementedError("Not needed for this test")
    }

    private class MockX509VerifyService : X509VerifyService {
        override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
            X509VerificationResult(
                certificateChain = emptyArray(),
                critical = false,
                message = "Not needed for this test",
                error = true,
            )

        override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService = this

        override fun getTrustedCerts(): Array<String>? = null
    }
}
