/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

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
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeRevocationCheckerTest {
    private val unavailableOcsp =
        object : OCSPChecker {
            override suspend fun checkOCSP(
                certificate: ByteArray,
                issuerCertificate: ByteArray?,
                options: RevocationCheckOptions,
            ) = RevocationCheckResult(
                status = RevocationStatus.UNAVAILABLE,
                method = RevocationCheckMethod.OCSP,
                checkedAt = 0L,
                errorMessage = "Not available",
            )
        }

    private val unavailableCrl =
        object : CRLChecker {
            override suspend fun checkCRL(
                certificate: ByteArray,
                options: RevocationCheckOptions,
            ) = RevocationCheckResult(
                status = RevocationStatus.UNAVAILABLE,
                method = RevocationCheckMethod.CRL,
                checkedAt = 0L,
                errorMessage = "Not available",
            )
        }

    private fun createTestExecution(): SessionExecution =
        TestSessionExecution(
            createAnonymousSessionContext("revocation-test", "revocation-test-correlation"),
        )

    @Test
    fun returnsGoodWhenCheckersUnavailableAndNotFailOnUnknown() =
        runTest {
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = unavailableOcsp,
                    crlChecker = unavailableCrl,
                    execution = createTestExecution(),
                )
            val result =
                checker.checkRevocation(
                    certificate = "test-cert".encodeToByteArray(),
                    options = RevocationCheckOptions(failOnUnknown = false),
                )
            assertEquals(RevocationStatus.GOOD, result.status)
            assertEquals(RevocationCheckMethod.NONE, result.method)
        }

    @Test
    fun returnsUnavailableWhenCheckersUnavailableAndFailOnUnknown() =
        runTest {
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = unavailableOcsp,
                    crlChecker = unavailableCrl,
                    execution = createTestExecution(),
                )
            val result =
                checker.checkRevocation(
                    certificate = "test-cert".encodeToByteArray(),
                    options = RevocationCheckOptions(failOnUnknown = true),
                )
            assertEquals(RevocationStatus.UNAVAILABLE, result.status)
        }

    @Test
    fun delegatesToOcspCheckerWhenAvailable() =
        runTest {
            val mockOcsp =
                object : OCSPChecker {
                    override suspend fun checkOCSP(
                        certificate: ByteArray,
                        issuerCertificate: ByteArray?,
                        options: RevocationCheckOptions,
                    ) = RevocationCheckResult(
                        status = RevocationStatus.GOOD,
                        method = RevocationCheckMethod.OCSP,
                        checkedAt = 1000L,
                    )
                }
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = mockOcsp,
                    crlChecker = unavailableCrl,
                    execution = createTestExecution(),
                )
            val result =
                checker.checkRevocation(
                    certificate = "test-cert".encodeToByteArray(),
                    options = RevocationCheckOptions(checkOCSP = true, checkCRL = false),
                )
            assertEquals(RevocationStatus.GOOD, result.status)
            assertEquals(RevocationCheckMethod.OCSP, result.method)
        }

    @Test
    fun fallsThroughToCrlWhenOcspUnavailable() =
        runTest {
            val mockCrl =
                object : CRLChecker {
                    override suspend fun checkCRL(
                        certificate: ByteArray,
                        options: RevocationCheckOptions,
                    ) = RevocationCheckResult(
                        status = RevocationStatus.GOOD,
                        method = RevocationCheckMethod.CRL,
                        checkedAt = 1000L,
                    )
                }
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = unavailableOcsp,
                    crlChecker = mockCrl,
                    execution = createTestExecution(),
                )
            val result =
                checker.checkRevocation(
                    certificate = "test-cert".encodeToByteArray(),
                    options = RevocationCheckOptions(checkOCSP = true, checkCRL = true, preferOCSP = true),
                )
            assertEquals(RevocationStatus.GOOD, result.status)
            assertEquals(RevocationCheckMethod.CRL, result.method)
        }

    @Test
    fun respectsPreferCrlOrder() =
        runTest {
            val mockCrl =
                object : CRLChecker {
                    override suspend fun checkCRL(
                        certificate: ByteArray,
                        options: RevocationCheckOptions,
                    ) = RevocationCheckResult(
                        status = RevocationStatus.GOOD,
                        method = RevocationCheckMethod.CRL,
                        checkedAt = 1000L,
                    )
                }
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = unavailableOcsp,
                    crlChecker = mockCrl,
                    execution = createTestExecution(),
                )
            val result =
                checker.checkRevocation(
                    certificate = "test-cert".encodeToByteArray(),
                    options = RevocationCheckOptions(preferOCSP = false),
                )
            assertEquals(RevocationCheckMethod.CRL, result.method)
        }

    @Test
    fun reportsRevokedStatus() =
        runTest {
            val mockOcsp =
                object : OCSPChecker {
                    override suspend fun checkOCSP(
                        certificate: ByteArray,
                        issuerCertificate: ByteArray?,
                        options: RevocationCheckOptions,
                    ) = RevocationCheckResult(
                        status = RevocationStatus.REVOKED,
                        method = RevocationCheckMethod.OCSP,
                        checkedAt = 1000L,
                        revocationReason = RevocationReason.KEY_COMPROMISE,
                    )
                }
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = mockOcsp,
                    crlChecker = unavailableCrl,
                    execution = createTestExecution(),
                )
            val result = checker.checkRevocation(certificate = "test-cert".encodeToByteArray())
            assertEquals(RevocationStatus.REVOKED, result.status)
            assertEquals(RevocationReason.KEY_COMPROMISE, result.revocationReason)
        }

    @Test
    fun handlesOcspException() =
        runTest {
            val throwingOcsp =
                object : OCSPChecker {
                    override suspend fun checkOCSP(
                        certificate: ByteArray,
                        issuerCertificate: ByteArray?,
                        options: RevocationCheckOptions,
                    ): RevocationCheckResult = throw RevocationCheckException("Connection refused")
                }
            val checker =
                CompositeRevocationChecker(
                    ocspChecker = throwingOcsp,
                    crlChecker = unavailableCrl,
                    execution = createTestExecution(),
                )
            val result =
                checker.checkRevocation(
                    certificate = "test-cert".encodeToByteArray(),
                    options = RevocationCheckOptions(failOnUnknown = false),
                )
            assertEquals(RevocationStatus.GOOD, result.status)
        }

    // -- Test infrastructure --

    private class TestSessionExecution(
        override val sessionContext: SessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager get() = throw NotImplementedError()
        override val log: SessionLogService = TestLogService(sessionContext)
        override val conf: ContextConfig = TestContextConfig()
    }

    private class TestContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw NotImplementedError()
        override val tenant: TenantConfigService get() = throw NotImplementedError()
        override val principal: PrincipalConfigService get() = throw NotImplementedError()

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError()
    }

    private class TestLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionLogService {
        override val logManager: SessionLogManager = TestLogManager(sessionContext)
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): SessionLogService = this

        override fun executeAsync(message: LogMessage) = IdkOkResult(Unit)

        override fun toAsync(): AsyncLogService = TestAsyncLogService(sessionContext)
    }

    private class TestLogManager(
        private val ctx: SessionContext,
    ) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): SessionLogManager = this

        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default

        override fun withTagAsync(
            tag: String,
            config: LoggerConfig?,
        ): AsyncLogService = TestAsyncLogService(ctx)

        override fun withTag(
            tag: String,
            config: LoggerConfig?,
        ): SessionLogService = TestLogService(ctx)
    }

    private class TestAsyncLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : AsyncLogService {
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-async-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this

        override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)

        override fun toSync(): SessionLogService = TestLogService(sessionContext)
    }
}
