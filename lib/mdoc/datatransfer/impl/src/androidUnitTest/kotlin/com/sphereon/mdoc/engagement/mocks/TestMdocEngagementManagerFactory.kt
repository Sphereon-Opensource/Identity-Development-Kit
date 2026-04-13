package com.sphereon.mdoc.engagement.mocks

import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.asOkResult
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.engagement.EngagementConfiguration
import com.sphereon.mdoc.engagement.MdocEngagementFactory
import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.mdoc.engagement.MdocEngagementManagerImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineName
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped



/**
 * Factory for creating test MdocEngagementManagers with controllable mock dependencies.
 * This simplifies test setup and provides a consistent way to create managers for testing.
 */
object TestMdocEngagementManagerFactory {

    /**
     * Creates a test manager with mock dependencies.
     * The factory is configured to return TestEngagementInstances that can be controlled in tests.
     *
     * @param engagementFactory Optional factory for creating custom test engagements
     * @param scope Optional coroutine scope for controlling dispatcher behavior in tests (uses TestScope)
     */
    fun createTestManager(
        engagementFactory: ((EngagementConfiguration) -> TestEngagementInstance)? = null,
        scope: kotlinx.coroutines.CoroutineScope? = null
    ): MdocEngagementManager {
        val mockFactory = createMockFactory(engagementFactory)
        val mockKeyManager = createMockKeyManager()
        val mockLog = createMockLog()

        // If a test scope is provided, wrap it in a mockable CoroutineScopeScoped
        // that preserves the test scope when createChild() is called
        val scopeScoped = scope?.let { testScope ->
            val mockScoped = mockk<CoroutineScopeScoped>()
            every { mockScoped.coroutineContext } returns testScope.coroutineContext + CoroutineName("TestScope")
            every { mockScoped.createChild() } returns testScope
            mockScoped
        }
        return MdocEngagementManagerImpl(mockFactory, mockKeyManager, mockLog, scopeScoped = scopeScoped)
    }

    /**
     * Creates a mock engagement factory that returns TestEngagementInstances.
     */
    private fun createMockFactory(
        engagementFactory: ((EngagementConfiguration) -> TestEngagementInstance)?
    ): MdocEngagementFactory {
        val mockFactory = mockk<MdocEngagementFactory>()
        val mockHolder = mockk<MdocEngagementFactory.Holder>()

        // Mock the IdkResult-returning methods
        coEvery { mockHolder.createEngagement(any()) } answers {
            val configBuilder = firstArg<EngagementConfiguration.() -> Unit>()
            val config = EngagementConfiguration().apply(configBuilder)
            val engagementMethods = config.engagementMethods.toSet()
            val engagement = if (engagementFactory != null) {
                engagementFactory.invoke(config)
            } else {
                TestEngagementInstance(
                    data = mockk(relaxed = true),
                    engagementMethods = engagementMethods
                )
            }
            engagement.asOkResult()
        }

        coEvery { mockHolder.createFromBuilder(any()) } answers {
            // Create a new TestEngagementInstance each time to ensure unique IDs
            // We use a relaxed mock for data since we're testing manager logic, not data handling
            TestEngagementInstance(
                data = mockk(relaxed = true),
                engagementMethods = emptySet()
            ).asOkResult()
        }

        coEvery { mockHolder.createFromEphemeralKey(any(), any()) } answers {
            val configBuilder = secondArg<EngagementConfiguration.() -> Unit>()
            val config = EngagementConfiguration().apply(configBuilder)
            val engagementMethods = config.engagementMethods.toSet()
            TestEngagementInstance(
                data = mockk(relaxed = true),
                engagementMethods = engagementMethods
            ).asOkResult()
        }

        every { mockFactory.holder } returns mockHolder

        return mockFactory
    }

    /**
     * Creates a mock key manager that returns usable mock keys.
     */
    private fun createMockKeyManager(): KeyManagerService {
        val mockKeyManager = mockk<KeyManagerService>(relaxed = true)
        val mockKey = mockk<ManagedKeyInfoType<CoseKeyType>>(relaxed = true)

        coEvery {
            mockKeyManager.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA256)
                .generateKeyAsync(any())
        } returns mockk {
            every { toManagedKeyInfo<CoseKeyType>(any(), any()) } returns mockKey
        }

        return mockKeyManager
    }

    /**
     * Creates a mock log service.
     */
    private fun createMockLog(): SessionLogService {
        return mockk(relaxed = true)
    }
}
