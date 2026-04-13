/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAttestationChallengeStorage
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerifyClientAuthenticationCommandImplTest {
    private val ctx = OAuth2ServerTestContext("client-auth-test", this)
    private val challengeStorage = InMemoryAttestationChallengeStorage()

    private fun createCommand(
        clientRegistry: ClientRegistry = StubClientRegistry(),
        jwtService: JwtService = StubJwtService(),
        config: OAuth2ServerInstanceConfig =
            OAuth2ServerInstanceConfig(
                baseUrl = "https://auth.example.com",
                attestation = FeaturePolicy.SUPPORTED,
            ),
    ): VerifyClientAuthenticationCommandImpl {
        val configProvider =
            TestOAuth2ServersConfigProvider(
                OAuth2ServersConfig(servers = mapOf("default" to config)),
            )
        return VerifyClientAuthenticationCommandImpl(
            ctx.execution,
            clientRegistry,
            jwtService,
            configProvider,
            challengeStorage,
        )
    }

    // ========================================================================
    // Basic Auth
    // ========================================================================

    @Test
    fun testBasicAuthSuccess() =
        runTest {
            val registry =
                StubClientRegistry(
                    verifyResult = true,
                )
            val command = createCommand(clientRegistry = registry)

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication =
                            ClientAuthenticationConfig.Basic(
                                ClientCredentials("client1", "secret1"),
                            ),
                        clientId = "client1",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("client1", result.value.clientId)
            assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, result.value.method)
            assertNull(result.value.clientInstanceKey)
        }

    @Test
    fun testBasicAuthInvalidCredentials() =
        runTest {
            val registry = StubClientRegistry(verifyResult = false)
            val command = createCommand(clientRegistry = registry)

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication =
                            ClientAuthenticationConfig.Basic(
                                ClientCredentials("client1", "wrong-secret"),
                            ),
                        clientId = "client1",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isErr)
        }

    // ========================================================================
    // Post Auth
    // ========================================================================

    @Test
    fun testPostAuthSuccess() =
        runTest {
            val registry = StubClientRegistry(verifyResult = true)
            val command = createCommand(clientRegistry = registry)

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication =
                            ClientAuthenticationConfig.Post(
                                ClientCredentials("client1", "secret1"),
                            ),
                        clientId = "client1",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("client1", result.value.clientId)
            assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_POST, result.value.method)
        }

    @Test
    fun testPostAuthInvalidCredentials() =
        runTest {
            val registry = StubClientRegistry(verifyResult = false)
            val command = createCommand(clientRegistry = registry)

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication =
                            ClientAuthenticationConfig.Post(
                                ClientCredentials("client1", "wrong"),
                            ),
                        clientId = "client1",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isErr)
        }

    // ========================================================================
    // None / Anonymous
    // ========================================================================

    @Test
    fun testNoneAuthPassesThrough() =
        runTest {
            val command = createCommand()

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication = ClientAuthenticationConfig.None("public-client"),
                        clientId = "public-client",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals("public-client", result.value.clientId)
            assertEquals(ClientAuthenticationMethod.NONE, result.value.method)
        }

    @Test
    fun testAnonymousAuthPassesThrough() =
        runTest {
            val command = createCommand()

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication = ClientAuthenticationConfig.Anonymous,
                        clientId = "",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(ClientAuthenticationMethod.NONE, result.value.method)
        }

    // ========================================================================
    // Attestation — disabled
    // ========================================================================

    @Test
    fun testAttestationRejectedWhenDisabled() =
        runTest {
            val command =
                createCommand(
                    config =
                        OAuth2ServerInstanceConfig(
                            baseUrl = "https://auth.example.com",
                            attestation = FeaturePolicy.DISABLED,
                        ),
                )

            val result =
                command.execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication =
                            ClientAuthenticationConfig.AttestationJwt(
                                com.sphereon.oauth2.common.model
                                    .ClientAttestation("att.jwt", "pop.jwt"),
                            ),
                        clientId = "client1",
                        tokenEndpointUrl = "https://auth.example.com/token",
                    ),
                )

            assertTrue(result.isErr)
        }

    // ========================================================================
    // Stubs
    // ========================================================================

    private class StubClientRegistry(
        private val client: ClientRegistration? = null,
        private val verifyResult: Boolean = true,
    ) : ClientRegistry {
        override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> = Ok(client)

        override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> = Ok(registration)

        override suspend fun updateClient(
            clientId: String,
            registration: ClientRegistration,
        ): IdkResult<ClientRegistration, AuthorizationServerError> = Ok(registration)

        override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> = Ok(Unit)

        override suspend fun listClients(
            limit: Int,
            offset: Int,
        ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

        override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(client != null)

        override suspend fun verifyClientCredentials(
            clientId: String,
            clientSecret: String,
        ): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(verifyResult)
    }

    private class StubJwtService(
        private val verifyValid: Boolean = true,
    ) : JwtService {
        private val notImpl = IdkError(code = "not_implemented", message = IdkError.Message(i18nKey = "", defaultMessage = "Not implemented"))

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = Err(notImpl)

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = Err(notImpl)

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
            if (!verifyValid) {
                return Err(
                    IdkError(
                        code = "verification_failed",
                        message = IdkError.Message(i18nKey = "", defaultMessage = "Verification failed"),
                    ),
                )
            }
            return Ok(
                JwsValidationResult(
                    jws = JwsJsonGeneralWithIdentifiers(payload = "", signatures = emptyList()),
                    isValid = true,
                    parsedPayload = JsonObject(emptyMap()),
                ),
            )
        }

        override fun assembleJwsGeneral(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwsJsonGeneral = throw NotImplementedError()

        override fun assembleJwsFlattened(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwsJsonFlattened = throw NotImplementedError()

        override fun assembleJwsCompact(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ): JwtCompactResult = throw NotImplementedError()

        override val commands: JwtService.Commands
            get() = throw NotImplementedError()
    }
}
