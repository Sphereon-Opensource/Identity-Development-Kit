package com.sphereon.crypto.kms.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.OperationCapability
import com.sphereon.crypto.core.kms.TestKmsMock
import com.sphereon.crypto.core.kms.TestKmsProviderMock
import com.sphereon.crypto.core.kms.command.EcPointMultiplyArgs
import com.sphereon.crypto.core.kms.command.EcdhDeriveArgs
import com.sphereon.crypto.core.kms.command.SignDigestArgs
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.core.kms.command.VerifyDigestArgs
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScdsaPrimitiveCommandImplTest {
    @Test
    fun signDigestFailsFastWhenProviderDoesNotAdvertiseCapability() =
        runTest {
            val (providerRegistry, provider) = unsupportedScdsaProviderRegistry()
            val command = SignDigestCommandImpl(TestSessionExecution(), providerRegistry)

            val result =
                command.execute(
                    SignDigestArgs(
                        keyInfo = scdsaKeyInfo(provider.id),
                        digest = byteArrayOf(1, 2, 3, 4),
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    @Test
    fun verifyDigestFailsFastWhenProviderDoesNotAdvertiseCapability() =
        runTest {
            val (providerRegistry, provider) = unsupportedScdsaProviderRegistry()
            val command = VerifyDigestCommandImpl(TestSessionExecution(), providerRegistry)

            val result =
                command.execute(
                    VerifyDigestArgs(
                        keyInfo = scdsaKeyInfo(provider.id),
                        digest = byteArrayOf(1, 2, 3, 4),
                        signature = ByteArray(64) { it.toByte() },
                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    @Test
    fun signDigestFailsFastWhenProviderDoesNotAdvertiseAlgorithm() =
        runTest {
            val providerRegistry = TestKmsMock()
            val provider = digestAlgorithmLimitedProvider()
            providerRegistry.registerProvider(provider, makeDefaultKms = true)
            val command = SignDigestCommandImpl(TestSessionExecution(), providerRegistry)

            val result =
                command.execute(
                    SignDigestArgs(
                        keyInfo = scdsaKeyInfo(provider.id, signatureAlgorithm = SignatureAlgorithm.RSA_SHA256),
                        digest = ByteArray(32) { it.toByte() },
                        signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    @Test
    fun verifyDigestFailsFastWhenProviderDoesNotAdvertiseAlgorithm() =
        runTest {
            val providerRegistry = TestKmsMock()
            val provider = digestAlgorithmLimitedProvider()
            providerRegistry.registerProvider(provider, makeDefaultKms = true)
            val command = VerifyDigestCommandImpl(TestSessionExecution(), providerRegistry)

            val result =
                command.execute(
                    VerifyDigestArgs(
                        keyInfo = scdsaKeyInfo(provider.id, signatureAlgorithm = SignatureAlgorithm.RSA_SHA256),
                        digest = ByteArray(32) { it.toByte() },
                        signature = ByteArray(256) { it.toByte() },
                        signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    @Test
    fun ecdhDeriveFailsFastWhenProviderDoesNotAdvertiseCapability() =
        runTest {
            val (providerRegistry, provider) = unsupportedScdsaProviderRegistry()
            val command = EcdhDeriveCommandImpl(TestSessionExecution(), providerRegistry)

            val result =
                command.execute(
                    EcdhDeriveArgs(
                        privateKeyInfo = scdsaKeyInfo(provider.id),
                        publicKeyInfo = scdsaKeyInfo(provider.id, alias = "secdsa-peer"),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    @Test
    fun ecPointMultiplyFailsFastWhenProviderDoesNotAdvertiseCapability() =
        runTest {
            val (providerRegistry, provider) = unsupportedScdsaProviderRegistry()
            val command = EcPointMultiplyCommandImpl(TestSessionExecution(), providerRegistry)

            val result =
                command.execute(
                    EcPointMultiplyArgs(
                        privateKeyInfo = scdsaKeyInfo(provider.id),
                        publicKeyInfo = scdsaKeyInfo(provider.id, alias = "secdsa-peer"),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    private fun unsupportedScdsaProviderRegistry(): Pair<TestKmsMock, TestKmsProviderMock> {
        val providerRegistry = TestKmsMock()
        val provider = TestKmsProviderMock()
        providerRegistry.registerProvider(provider, makeDefaultKms = true)
        return providerRegistry to provider
    }

    private fun scdsaKeyInfo(
        providerId: String,
        alias: String = "secdsa-test",
        signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
    ): KeyInfo<KeyType> =
        KeyInfo(
            providerId = providerId,
            alias = alias,
            signatureAlgorithm = signatureAlgorithm,
        )

    private fun digestAlgorithmLimitedProvider(): KmsProvider =
        object : KmsProvider by TestKmsProviderMock() {
            override val id: String = "digest-algorithm-limited-provider"

            override fun getCapabilities(): KmsProviderCapabilities =
                TestKmsProviderMock()
                    .getCapabilities()
                    .copy(
                        providerId = id,
                        operations =
                            arrayOf(
                                OperationCapability(
                                    operation = KmsProviderOperation.SIGN_DIGEST,
                                    supported = true,
                                    signatureAlgorithms = arrayOf(SignatureAlgorithm.ECDSA_SHA256),
                                ),
                                OperationCapability(
                                    operation = KmsProviderOperation.VERIFY_DIGEST,
                                    supported = true,
                                    signatureAlgorithms = arrayOf(SignatureAlgorithm.ECDSA_SHA256),
                                ),
                            ),
                    )

            override suspend fun signDigest(
                keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
                digest: ByteArray,
                signatureAlgorithm: SignatureAlgorithm,
                signatureEncoding: SignatureEncoding,
                requireX5Chain: Boolean,
            ): ByteArray = ByteArray(64)

            override suspend fun verifyDigest(
                keyInfo: com.sphereon.crypto.core.KeyInfoType<*>,
                digest: ByteArray,
                signature: ByteArray,
                signatureAlgorithm: SignatureAlgorithm,
                signatureEncoding: SignatureEncoding,
            ): Boolean = true
        }

    private class TestSessionExecution(
        override val sessionContext: SessionContext = createAnonymousSessionContext("scdsa-command-test", "scdsa-command-test-correlation"),
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")
        override val log: SessionLogService = NoOpSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "test-log"
        override val isEnabled: Boolean = false
        override val scope = com.sphereon.core.api.context.IdkScope.SESSION
        override val logManager: SessionLogManager
            get() = throw NotImplementedError("Not needed for test")

        override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

        override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw NotImplementedError("Not needed for test")
        override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
        override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
    }
}
