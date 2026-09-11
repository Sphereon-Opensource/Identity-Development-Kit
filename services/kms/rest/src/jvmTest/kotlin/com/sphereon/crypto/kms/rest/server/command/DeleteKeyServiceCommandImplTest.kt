/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.command

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
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.key.persistence.KeyReferenceResolutionException
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.server.adapter.keyDeleteErrorResponse
import com.sphereon.crypto.kms.rest.server.service.KmsRestService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteKeyServiceCommandImplTest {
    @Test
    fun codedAmbiguitySurvivesServiceCommandAndMapsToHttpConflict() =
        runTest {
            val expected =
                KeyReferenceResolutionException(
                    code = KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE,
                    message = "ambiguous key reference",
                )
            val command = DeleteKeyServiceCommandImpl(unusedSessionExecution(), deleteOnlyService { _, _ -> throw expected })

            val result = command.execute(DeleteKeyInput("shared-alias"))

            assertTrue(result.isErr)
            assertEquals(KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE, result.error.code)
            val response = keyDeleteErrorResponse(result.error.exception ?: error("coded exception was discarded"))
            assertEquals(409, response.statusCode)
            assertTrue(response.body!!.contains("\"code\":\"${KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE}\""))
        }

    @Test
    fun codedHistoryFailureSurvivesServiceCommandAndMapsToHttpConflict() =
        runTest {
            val expected =
                KeyReferenceResolutionException(
                    code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
                    message = "durable history unavailable",
                )
            val command =
                DeleteKeyServiceCommandImpl(
                    unusedSessionExecution(),
                    deleteOnlyService { _, _ -> throw expected },
                )

            val result = command.execute(DeleteKeyInput("possibly-indexed", "provider-1"))

            assertTrue(result.isErr)
            assertEquals(KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, result.error.code)
            val response = keyDeleteErrorResponse(result.error.exception ?: error("coded exception was discarded"))
            assertEquals(409, response.statusCode)
            assertTrue(response.body!!.contains("\"code\":\"${KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED}\""))
        }

    @Test
    fun genuineMissingKeyRetainsNotFoundError() =
        runTest {
            val command =
                DeleteKeyServiceCommandImpl(
                    unusedSessionExecution(),
                    deleteOnlyService { _, _ -> false },
                )

            val result = command.execute(DeleteKeyInput("missing-key", "provider-1"))

            assertTrue(result.isOk)
            assertEquals("missing-key", result.value.aliasOrKid)
            assertFalse(result.value.deleted)
        }

    @Test
    fun unrelatedPersistenceFailureRemainsInternalError() =
        runTest {
            val expected = RuntimeException("database unavailable")
            val command = DeleteKeyServiceCommandImpl(unusedSessionExecution(), deleteOnlyService { _, _ -> throw expected })

            val result = command.execute(DeleteKeyInput("key-alias", "provider-1"))

            assertTrue(result.isErr)
            assertEquals("UNKNOWN_ERROR", result.error.code)
            assertEquals(expected, result.error.exception)
            val response = keyDeleteErrorResponse(result.error.exception ?: error("exception was discarded"))
            assertEquals(500, response.statusCode)
        }

    private fun unusedSessionExecution(): SessionExecution = TestSessionExecution

    private fun deleteOnlyService(delete: suspend (String, String?) -> Boolean): KmsRestService =
        object : KmsRestService {
            override suspend fun deleteKey(aliasOrKid: String, providerId: String?): Boolean = delete(aliasOrKid, providerId)

            override suspend fun getKey(aliasOrKid: String, providerId: String?): ManagedKeyInfoType<*> =
                unexpected("getKey")

            override suspend fun listKeys(providerId: String?): Array<ManagedKeyReference> = unexpected("listKeys")

            override suspend fun storeKey(
                keyInfo: ResolvedKeyInfoType<*>,
                certChain: Array<String>?,
            ): ManagedKeyInfoType<*> = unexpected("storeKey")

            override suspend fun generateKey(
                alias: String?,
                use: JwkUse?,
                keyOperations: Array<KeyOperations>?,
                alg: SignatureAlgorithm?,
                providerId: String?,
            ): ManagedKeyPair = unexpected("generateKey")
        }

    private fun unexpected(method: String): Nothing = error("KmsRestService.$method must not be called")

    private object TestSessionExecution : SessionExecution {
        override val tenantId: String = "test-tenant"
        override val principalId: String = "test-principal"
        override val correlationId: String = "test-correlation"
        override val sessionContextManager: SessionContextManager get() = error("unused")
        override val sessionContext: SessionContext = NoOpSessionContext
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
        override val conf: ContextConfig = NoOpContextConfig
    }

    private object NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = error("unused")
        override val tenant: TenantConfigService get() = error("unused")
        override val principal: PrincipalConfigService get() = error("unused")

        override fun conf(level: ConfigLevel): ConfigService = error("unused")
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "delete-key-command-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = kotlin.error("unused")

        override suspend fun setConfig(config: LoggerConfig): LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = kotlin.error("unused")
    }
}
