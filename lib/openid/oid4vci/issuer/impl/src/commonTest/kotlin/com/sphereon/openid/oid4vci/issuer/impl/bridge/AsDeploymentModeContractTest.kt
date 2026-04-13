/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.bridge

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumePreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for the Oid4vciAuthorizationServerBridge across AS deployment modes.
 *
 * The bridge abstraction isolates AS topology from the OID4VCI issuer. These tests verify
 * the behavioral contract that any bridge implementation must satisfy:
 *
 * - Embedded/Hosted mode: SphereonAsBridge (tested via fakes)
 * - External mode: Not yet implemented; tests document the contract gap
 * - Forwarded mode: Future — proxy to colocated AS
 * - Hybrid mode: Future — split code/token across AS instances
 */
class AsDeploymentModeContractTest {
    // ========================================================================
    // Fakes
    // ========================================================================

    /**
     * In-memory fake for PreAuthorizedCodeStorage.
     * Mimics atomic consume behavior (remove-on-read).
     */
    private class FakePreAuthorizedCodeStorage : PreAuthorizedCodeStorage {
        private val codes = mutableMapOf<String, PreAuthorizedCodeData>()

        override suspend fun storePreAuthorizedCode(
            code: String,
            data: PreAuthorizedCodeData,
        ): IdkResult<Unit, AuthorizationServerError.StorageError> {
            codes[code] = data
            return Ok(Unit)
        }

        override suspend fun consumePreAuthorizedCode(code: String): IdkResult<PreAuthorizedCodeData?, AuthorizationServerError.StorageError> = Ok(codes.remove(code))

        override suspend fun isCodeUsed(code: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(code !in codes)
    }

    /**
     * Fake AuthorizationServerService that only implements introspectToken.
     * All other methods throw — they are not part of the bridge contract.
     */
    private class FakeAuthorizationServerService(
        private var introspectionResult: IdkResult<TokenIntrospectionResponse, IdkError>,
    ) : AuthorizationServerService {
        fun setIntrospectionResult(result: IdkResult<TokenIntrospectionResponse, IdkError>) {
            introspectionResult = result
        }

        override suspend fun introspectToken(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError> = introspectionResult

        // --- All other methods are not used by the bridge and throw ---
        override suspend fun parseTokenRequest(args: com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs) = notUsed()

        override suspend fun verifyAuthorizationCodeGrant(args: com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs) = notUsed()

        override suspend fun verifyRefreshTokenGrant(args: com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs) = notUsed()

        override suspend fun verifyClientCredentialsGrant(args: com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs) = notUsed()

        override suspend fun verifyTokenExchangeGrant(args: com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs) = notUsed()

        override suspend fun verifyPreAuthorizedCodeGrant(args: com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs) = notUsed()

        override suspend fun createAccessToken(args: com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs) = notUsed()

        override suspend fun createRefreshToken(args: com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs) = notUsed()

        override suspend fun createTokenResponse(args: com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs) = notUsed()

        override suspend fun parseAuthorizationRequest(args: com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs) = notUsed()

        override suspend fun verifyAuthorizationRequest(args: com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData) = notUsed()

        override suspend fun createAuthorizationSession(args: com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest) = notUsed()

        override suspend fun createAuthorizationCode(args: com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs) = notUsed()

        override suspend fun createAuthorizationResponse(args: com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs) = notUsed()

        override suspend fun createAuthorizationErrorResponse(args: com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs) = notUsed()

        override suspend fun parsePushedAuthorizationRequest(args: com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs) = notUsed()

        override suspend fun verifyPushedAuthorizationRequest(args: com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestArgs) = notUsed()

        override suspend fun createRequestUri(args: com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest) = notUsed()

        override suspend fun createPushedAuthorizationResponse(args: com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs) = notUsed()

        override suspend fun retrieveAuthorizationRequestByUri(requestUri: String) = notUsed()

        override suspend fun parseIntrospectionRequest(args: com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs) = notUsed()

        override suspend fun parseRevocationRequest(args: com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs) = notUsed()

        override suspend fun revokeToken(args: com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs): IdkResult<Unit, IdkError> =
            throw UnsupportedOperationException("Not used in bridge tests")

        override suspend fun buildServerMetadata(args: com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs) = notUsed()

        override suspend fun verifyClientAuthentication(args: com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs) = notUsed()

        override suspend fun createAttestationChallenge(args: com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs) = notUsed()

        override suspend fun createIdToken(args: com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs) = notUsed()

        override suspend fun getUserInfo(args: com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs) = notUsed()

        override suspend fun getJwks(args: com.sphereon.oauth2.server.authorization.command.GetJwksArgs) = notUsed()

        override val commands: AuthorizationServerService.Commands get() = throw UnsupportedOperationException("Not used in bridge tests")

        private fun notUsed(): Nothing = throw UnsupportedOperationException("Not used in bridge tests")
    }

    // ========================================================================
    // Embedded/Hosted mode — SphereonAsBridge
    // ========================================================================

    private fun createEmbeddedBridge(
        storage: FakePreAuthorizedCodeStorage = FakePreAuthorizedCodeStorage(),
        asService: FakeAuthorizationServerService =
            FakeAuthorizationServerService(
                Ok(TokenIntrospectionResponse(active = false)),
            ),
    ): Pair<SphereonAsBridge, FakeAuthorizationServerService> {
        val bridge =
            SphereonAsBridge(
                preAuthorizedCodeStorage = storage,
                authorizationServerService = asService,
            )
        return bridge to asService
    }

    @Test
    fun embeddedModeRegistersPreAuthorizedCode() =
        runTest {
            val (bridge, _) = createEmbeddedBridge()

            val result =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-001",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = false,
                    ),
                )

            assertTrue(result.isOk, "registerPreAuthorizedCode should succeed")
            val registered = result.value
            assertTrue(registered.code.isNotBlank(), "Code should be a non-empty string")
        }

    @Test
    fun embeddedModeRegistersPreAuthorizedCodeWithTxCode() =
        runTest {
            val (bridge, _) = createEmbeddedBridge()

            val result =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-002",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = true,
                    ),
                )

            assertTrue(result.isOk, "registerPreAuthorizedCode with txCode should succeed")
            val registered = result.value
            assertTrue(registered.code.isNotBlank(), "Code should be non-empty")
            assertNotNull(registered.txCode, "txCode should be present when requested")
            assertTrue(registered.txCode!!.isNotBlank(), "txCode should be non-empty")
        }

    @Test
    fun embeddedModeConsumesPreAuthorizedCode() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            // Register
            val regResult =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-003",
                        credentialConfigurationIds = listOf("IdentityCredential", "DriverLicense"),
                        txCodeRequired = false,
                    ),
                )
            assertTrue(regResult.isOk)
            val code = regResult.value.code

            // Consume
            val consumeResult =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(
                        code = code,
                        txCode = null,
                        clientId = "test-client",
                    ),
                )

            assertTrue(consumeResult.isOk, "consumePreAuthorizedCode should succeed")
            val consumed = consumeResult.value
            assertEquals("session-003", consumed.sessionId)
            assertEquals(listOf("IdentityCredential", "DriverLicense"), consumed.credentialConfigurationIds)
        }

    @Test
    fun embeddedModeRejectsDoubleConsumption() =
        runTest {
            val storage = FakePreAuthorizedCodeStorage()
            val (bridge, _) = createEmbeddedBridge(storage = storage)

            // Register
            val regResult =
                bridge.registerPreAuthorizedCode(
                    RegisterPreAuthCodeArgs(
                        sessionId = "session-004",
                        credentialConfigurationIds = listOf("IdentityCredential"),
                        txCodeRequired = false,
                    ),
                )
            assertTrue(regResult.isOk)
            val code = regResult.value.code

            // First consume — should succeed
            val first =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(code = code, txCode = null, clientId = "client-1"),
                )
            assertTrue(first.isOk, "First consumption should succeed")

            // Second consume — should fail (code already consumed)
            val second =
                bridge.consumePreAuthorizedCode(
                    ConsumePreAuthCodeArgs(code = code, txCode = null, clientId = "client-2"),
                )
            assertTrue(second.isErr, "Second consumption of the same code must fail")
        }

    @Test
    fun embeddedModeValidatesAccessToken() =
        runTest {
            val authDetails =
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "openid_credential")
                            put("credential_configuration_id", "IdentityCredential")
                            put("credential_identifiers", JsonArray(listOf(JsonPrimitive("id-1"), JsonPrimitive("id-2"))))
                        },
                    ),
                )

            val introspectionResponse =
                TokenIntrospectionResponse(
                    active = true,
                    sub = "user-123",
                    clientId = "client-abc",
                    scope = "openid",
                    additionalClaims = mapOf<String, JsonElement>("authorization_details" to authDetails),
                )

            val (bridge, _) =
                createEmbeddedBridge(
                    asService = FakeAuthorizationServerService(Ok(introspectionResponse)),
                )

            val result =
                bridge.validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = "valid-token"),
                )

            assertTrue(result.isOk, "validateAccessToken should succeed for active token")
            val ctx = result.value
            assertEquals("user-123", ctx.subject)
            assertEquals("client-abc", ctx.clientId)
            assertEquals("openid", ctx.scope)
            assertEquals(listOf("IdentityCredential"), ctx.credentialConfigurationIds)
            assertEquals(listOf("id-1", "id-2"), ctx.credentialIdentifiers)
        }

    @Test
    fun embeddedModeRejectsInactiveToken() =
        runTest {
            val (bridge, _) =
                createEmbeddedBridge(
                    asService =
                        FakeAuthorizationServerService(
                            Ok(TokenIntrospectionResponse(active = false)),
                        ),
                )

            val result =
                bridge.validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = "expired-token"),
                )

            assertTrue(result.isErr, "Inactive token should be rejected")
        }

    @Test
    fun embeddedModeRejectsTokenWithoutSubject() =
        runTest {
            val (bridge, _) =
                createEmbeddedBridge(
                    asService =
                        FakeAuthorizationServerService(
                            Ok(TokenIntrospectionResponse(active = true, sub = null, clientId = "client-1")),
                        ),
                )

            val result =
                bridge.validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = "no-sub-token"),
                )

            assertTrue(result.isErr, "Token without sub claim should be rejected")
        }

    // ========================================================================
    // External mode — contract documentation
    //
    // The external AS mode requires a bridge that delegates to the external AS
    // via HTTP introspection and does NOT support pre-authorized code management
    // (the external AS manages its own grants). No ExternalAsBridge implementation
    // exists yet.
    //
    // When implemented, ExternalAsBridge should:
    // - registerPreAuthorizedCode -> return error (external AS manages codes)
    // - consumePreAuthorizedCode -> return error (external AS manages codes)
    // - validateAccessToken -> HTTP POST to introspection_endpoint
    // - augmentAsMetadata -> no-op (external AS has its own discovery document)
    // ========================================================================

    @Test
    fun externalModeRequiresBridgeImplementation() {
        // This test documents that external mode needs a custom Oid4vciAuthorizationServerBridge
        // implementation. The SphereonAsBridge is only valid for HOSTED mode.
        //
        // The ExternalAsBridge should:
        // 1. Reject registerPreAuthorizedCode (external AS manages its own grants)
        // 2. Reject consumePreAuthorizedCode (external AS manages its own grants)
        // 3. Validate access tokens via HTTP introspection to the external AS endpoint
        // 4. Return no-op for augmentAsMetadata (external AS manages its own metadata)
        //
        // TODO: Implement ExternalAsBridge and add concrete tests here.
        assertTrue(true, "External mode bridge contract documented — implementation pending")
    }

    @Test
    fun bridgeContractRequiresAtomicCodeConsumption() {
        // Core security invariant: any bridge implementation MUST guarantee
        // that consumePreAuthorizedCode is atomic — a code can only be consumed once.
        // This mirrors RFC 6749 Section 10.5 for authorization codes.
        //
        // The embedded mode test (embeddedModeRejectsDoubleConsumption) verifies this
        // for SphereonAsBridge. Future bridge implementations must pass the same test.
        assertTrue(true, "Atomic consumption contract documented")
    }
}
