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

package com.sphereon.oauth2.server.authorization.impl.command.device

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.GenerateTokenArgs
import com.sphereon.core.api.random.NextBytesArgs
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.ByteArrayResult
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationArgs
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryDeviceAuthorizationStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Unit tests for [IssueDeviceAuthorizationCommandImpl] covering client validation, code-shape
 * invariants, and collision retry behaviour. The collision test wires a stub [SecureRandom] that
 * emits deterministic byte sequences so the userCode generator hits an existing record.
 */
class IssueDeviceAuthorizationCommandImplTest {
    private val ctx = OAuth2ServerTestContext("issue-device-auth-test", this)

    private val issuer = "https://as.example.com"
    private val configProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = issuer)),
            ),
        )

    private val fixedClock: Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-04-26T12:00:00Z")
        }

    private val deviceClient =
        TestFixtures.confidentialClient.copy(
            clientId = "device-client",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE, GrantType.DEVICE_CODE),
        )

    private val nonDeviceClient =
        TestFixtures.confidentialClient.copy(
            clientId = "no-device-client",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
        )

    private suspend fun setupClientRegistry(vararg clients: com.sphereon.oauth2.server.authorization.model.ClientRegistration): InMemoryClientRegistryImpl {
        val registry = InMemoryClientRegistryImpl(InMemoryOAuth2BackingStorageImpl())
        for (client in clients) {
            val r = registry.registerClient(client)
            assertTrue(r.isOk, "Failed to register client ${client.clientId}")
        }
        return registry
    }

    private fun newCommand(
        registry: InMemoryClientRegistryImpl,
        storage: InMemoryDeviceAuthorizationStorageImpl,
        secureRandom: SecureRandom = defaultSecureRandom(),
    ): IssueDeviceAuthorizationCommandImpl =
        IssueDeviceAuthorizationCommandImpl(
            execution = ctx.execution,
            deviceAuthorizationStorage = storage,
            clientRegistry = registry,
            serversConfigProvider = configProvider,
            secureRandom = secureRandom,
            clock = fixedClock,
        )

    @Test
    fun issuesValidCodesForRegisteredClient() =
        runTest {
            val registry = setupClientRegistry(deviceClient)
            val storage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())
            val command = newCommand(registry, storage)

            val result =
                command.execute(
                    IssueDeviceAuthorizationArgs(
                        clientId = deviceClient.clientId,
                        scope = "read offline_access",
                    ),
                )

            assertTrue(result.isOk, "Expected issuance to succeed")
            val issued = result.value

            // Spec invariants
            assertTrue(issued.deviceCode.isNotBlank())
            assertEquals(USER_CODE_LENGTH_WITH_SEPARATOR, issued.userCode.length)
            assertEquals('-', issued.userCode[USER_CODE_GROUP_SIZE])
            assertEquals(USER_CODE_GROUP_SIZE, issued.userCode.indexOf('-'))
            assertEquals("$issuer/device", issued.verificationUri)
            assertTrue(
                issued.verificationUriComplete.startsWith("$issuer/device?user_code="),
                "verification_uri_complete should be the verification_uri plus user_code parameter",
            )
            assertEquals(1800, issued.expiresIn)
            assertEquals(5, issued.intervalSeconds)

            // Persistence side-effect: a PENDING record should exist keyed by both deviceCode
            // and userCode so the verification UI can resolve the typed code.
            val byDeviceCode = storage.findByDeviceCode(issued.deviceCode)
            assertTrue(byDeviceCode.isOk)
            val record = byDeviceCode.value
            assertNotNull(record)
            assertEquals(DeviceAuthorizationState.PENDING, record.state)
            assertEquals(issued.userCode, record.userCode)
            assertEquals(deviceClient.clientId, record.clientId)
            assertEquals("read offline_access", record.scope)

            val byUserCode = storage.findByUserCode(issued.userCode)
            assertTrue(byUserCode.isOk)
            assertEquals(record.deviceCode, byUserCode.value?.deviceCode)
        }

    @Test
    fun rejectsClientWithoutDeviceCodeGrantType() =
        runTest {
            val registry = setupClientRegistry(nonDeviceClient)
            val storage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())
            val command = newCommand(registry, storage)

            val result =
                command.execute(IssueDeviceAuthorizationArgs(clientId = nonDeviceClient.clientId))

            assertTrue(result.isErr)
            assertEquals("unauthorized_client", result.error.code)
        }

    @Test
    fun rejectsUnknownClient() =
        runTest {
            val registry = setupClientRegistry(deviceClient)
            val storage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())
            val command = newCommand(registry, storage)

            val result =
                command.execute(IssueDeviceAuthorizationArgs(clientId = "unknown-client"))

            assertTrue(result.isErr)
            assertEquals("invalid_client", result.error.code)
        }

    @Test
    fun userCodeAvoidsAmbiguousChars() =
        runTest {
            val registry = setupClientRegistry(deviceClient)
            val storage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())
            // Each call needs a fresh storage to avoid userCode-collision rejections at scale.
            val ambiguousChars = setOf('0', 'O', '1', 'I')
            val sampleSize = 100
            for (iteration in 0 until sampleSize) {
                val perRunStorage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())
                val command = newCommand(registry, perRunStorage)
                val result =
                    command.execute(IssueDeviceAuthorizationArgs(clientId = deviceClient.clientId))
                assertTrue(result.isOk, "issue $iteration must succeed")
                val userCode = result.value.userCode
                for (ch in userCode) {
                    if (ch == '-') continue
                    assertTrue(
                        ch !in ambiguousChars,
                        "user_code at iteration $iteration contains visually ambiguous character '$ch': $userCode",
                    )
                }
            }
            // Discard the empty `storage` from outer scope (kept to match other test signatures).
            assertTrue(storage.findByDeviceCode("nope").isOk)
        }

    @Test
    fun userCodeRetriesOnCollisionAndFailsAfterMaxRetries() =
        runTest {
            val registry = setupClientRegistry(deviceClient)
            val storage = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())

            // CollidingSecureRandom returns a constant byte pattern for each randomBytes() call,
            // forcing the userCode generator to produce the same userCode every retry. The
            // device code goes through generateToken() which we let pass through to a real
            // CSPRNG so each attempt yields a unique device code (no collision on the deviceCode
            // index, only on the userCode index).
            val realRandom = defaultSecureRandom()
            val collidingRandom =
                object : SecureRandom {
                    override suspend fun generateToken(args: GenerateTokenArgs): IdkResult<StringResult, IdkError> = realRandom.generateToken(args)

                    override suspend fun nextBytes(args: NextBytesArgs): IdkResult<ByteArrayResult, IdkError> =
                        // 8 zero bytes -> first character of the unambiguous alphabet repeated
                        // 8 times. Format `XXXX-XXXX` where every X is the alphabet's first
                        // character ('2'); userCode = "2222-2222".
                        Ok(ByteArrayResult(bytes = ByteArray(args.length)))

                    override val commands: SecureRandom.Commands get() = realRandom.commands
                }

            val command = newCommand(registry, storage, secureRandom = collidingRandom)

            // First issuance should succeed and persist user_code = "2222-2222".
            val first = command.execute(IssueDeviceAuthorizationArgs(clientId = deviceClient.clientId))
            assertTrue(first.isOk)
            assertEquals("2222-2222", first.value.userCode)

            // Second issuance retries up to USER_CODE_MAX_RETRIES (5) times, every retry
            // collides on "2222-2222", then yields server_error.
            val second = command.execute(IssueDeviceAuthorizationArgs(clientId = deviceClient.clientId))
            assertTrue(second.isErr, "Second issuance must fail after exhausting retry budget")
            assertEquals("server_error", second.error.code)
        }

    private companion object {
        // 8 alphabet chars + 1 separator
        const val USER_CODE_LENGTH_WITH_SEPARATOR = 9
        const val USER_CODE_GROUP_SIZE = 4
    }
}
