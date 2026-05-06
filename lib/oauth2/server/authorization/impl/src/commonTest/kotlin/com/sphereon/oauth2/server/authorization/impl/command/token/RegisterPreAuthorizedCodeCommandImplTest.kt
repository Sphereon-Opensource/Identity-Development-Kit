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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterPreAuthorizedCodeCommandImplTest {
    private val ctx = OAuth2ServerTestContext("register-preauth-code-test", this)

    private class FakePreAuthorizedCodeStorage : PreAuthorizedCodeStorage {
        var stored: Pair<String, PreAuthorizedCodeData>? = null

        override suspend fun storePreAuthorizedCode(
            code: String,
            data: PreAuthorizedCodeData,
        ): IdkResult<Unit, AuthorizationServerError.StorageError> {
            stored = code to data
            return Ok(Unit)
        }

        override suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> = Ok(null)

        override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)
    }

    private fun configProvider(internalClients: Map<String, Pair<String, String>>) =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = "https://as.example.com", internalClients = internalClients)),
            ),
        )

    @Test
    fun storesCodeAfterValidatingBasicAuthCredentials() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val command =
                RegisterPreAuthorizedCodeCommandImpl(
                    execution = ctx.execution,
                    configProvider = configProvider(mapOf("issuer" to ("issuer-client" to "issuer-secret"))),
                    preAuthorizedCodeStorage = storage,
                )

            val result =
                command.execute(
                    RegisterPreAuthorizedCodeArgs(
                        basicAuthClientId = "issuer-client",
                        basicAuthClientSecret = "issuer-secret",
                        code = "preauth-1",
                        sessionId = "sess-1",
                        credentialConfigurationIds = listOf("config-1"),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("ok", result.value.status)
            val (storedCode, storedData) = assertNotNull(storage.stored)
            assertEquals("preauth-1", storedCode)
            assertEquals("sess-1", storedData.sessionId)
            assertEquals(listOf("config-1"), storedData.credentialConfigurationIds)
        }

    @Test
    fun rejectsRequestWithUnknownClientId() =
        runTest {
            val command =
                RegisterPreAuthorizedCodeCommandImpl(
                    execution = ctx.execution,
                    configProvider = configProvider(mapOf("issuer" to ("issuer-client" to "issuer-secret"))),
                    preAuthorizedCodeStorage = FakePreAuthorizedCodeStorage(),
                )

            val result =
                command.execute(
                    RegisterPreAuthorizedCodeArgs(
                        basicAuthClientId = "wrong-client",
                        basicAuthClientSecret = "issuer-secret",
                        code = "preauth-2",
                        sessionId = "sess-2",
                        credentialConfigurationIds = emptyList(),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_client", result.error.code)
        }

    @Test
    fun rejectsRequestWhenInternalClientsNotConfigured() =
        runTest {
            val command =
                RegisterPreAuthorizedCodeCommandImpl(
                    execution = ctx.execution,
                    configProvider = configProvider(emptyMap()),
                    preAuthorizedCodeStorage = FakePreAuthorizedCodeStorage(),
                )

            val result =
                command.execute(
                    RegisterPreAuthorizedCodeArgs(
                        basicAuthClientId = "anything",
                        basicAuthClientSecret = "anything",
                        code = "preauth-3",
                        sessionId = "sess-3",
                        credentialConfigurationIds = emptyList(),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_client", result.error.code)
        }
}
