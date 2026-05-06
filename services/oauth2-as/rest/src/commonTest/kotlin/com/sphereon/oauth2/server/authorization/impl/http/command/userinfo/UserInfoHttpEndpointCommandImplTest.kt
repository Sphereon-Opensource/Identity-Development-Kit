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

package com.sphereon.oauth2.server.authorization.impl.http.command.userinfo

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.AuthenticationScheme
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.command.clientauth.ClientCertificateExtractor
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestArgs
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestCommand
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenArgs
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.TokenPayload
import com.sphereon.oauth2.server.resource.model.VerifiedResourceRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Unit tests for [UserInfoHttpEndpointCommandImpl] that prove the HTTP shell delegates
 * binding-related access-token validation to [ValidateAccessTokenCommand] and only owns the
 * AS-specific `use_dpop_nonce` retry challenge plus error-shape mapping.
 */
class UserInfoHttpEndpointCommandImplTest {
    private class FakeUserInfoCommand(
        private val handler: suspend (HandleUserInfoRequestArgs) -> IdkResult<UserInfoResponse, IdkError>,
    ) : HandleUserInfoRequestCommand {
        var invocations: Int = 0
            private set
        var lastArgs: HandleUserInfoRequestArgs? = null
            private set

        override val commandId: String get() = HandleUserInfoRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleUserInfoRequestArgs>()
        override val outputTypeToken get() = typeToken<UserInfoResponse>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleUserInfoRequestArgs

        override suspend fun execute(args: HandleUserInfoRequestArgs): IdkResult<UserInfoResponse, IdkError> {
            invocations += 1
            lastArgs = args
            return handler(args)
        }
    }

    private class FakeValidateAccessTokenCommand(
        private val handler: suspend (ValidateAccessTokenArgs) -> IdkResult<VerifiedResourceRequest, IdkError>,
    ) : ValidateAccessTokenCommand {
        var invocations: Int = 0
            private set
        var lastArgs: ValidateAccessTokenArgs? = null
            private set

        override val commandId: String get() = ValidateAccessTokenCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<ValidateAccessTokenArgs>()
        override val outputTypeToken get() = typeToken<VerifiedResourceRequest>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is ValidateAccessTokenArgs

        override suspend fun execute(args: ValidateAccessTokenArgs): IdkResult<VerifiedResourceRequest, IdkError> {
            invocations += 1
            lastArgs = args
            return handler(args)
        }
    }

    private fun oidcEnabledConfig() =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(oidc = FeaturePolicy.SUPPORTED)),
            ),
        )

    private fun nonceRequiredConfig() =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers =
                    mapOf(
                        "default" to
                            OAuth2ServerInstanceConfig(
                                oidc = FeaturePolicy.SUPPORTED,
                                dpopNonceRequired = true,
                            ),
                    ),
            ),
        )

    private fun verifiedJwtPayload(
        sub: String = "user-123",
        scope: String? = "openid",
    ) = TokenPayload.Jwt(
        sub = sub,
        iss = "https://issuer.example",
        aud = listOf("https://audience.example"),
        exp = Instant.fromEpochSeconds(2_000_000_000L),
        iat = Instant.fromEpochSeconds(1_000_000_000L),
        scope = scope,
        clientId = "client-x",
        dpopJkt = null,
        certificateThumbprintS256 = null,
        jti = null,
    )

    private fun verifiedRequest(
        accessToken: String = "abc.def.ghi",
        scheme: AuthenticationScheme = AuthenticationScheme.BEARER,
    ) = VerifiedResourceRequest(
        tokenPayload = verifiedJwtPayload(),
        scheme = scheme,
        accessToken = accessToken,
        authorizationServer = "https://issuer.example",
        dpop = null,
    )

    @Test
    fun supportsBothGetAndPost() =
        runTest {
            val command = newCommand()
            assertTrue(command.supports(GenericHttpRequest(method = "GET", path = "/userinfo")))
            assertTrue(command.supports(GenericHttpRequest(method = "POST", path = "/userinfo")))
        }

    /**
     * Bearer happy path: validate succeeds, the HTTP shell forwards the access token to
     * [HandleUserInfoRequestCommand], and the response carries the claims body.
     */
    @Test
    fun success_bearer_returnsClaimsJsonAndDelegatesOnce() =
        runTest {
            val validate = FakeValidateAccessTokenCommand { Ok(verifiedRequest()) }
            val handle =
                FakeUserInfoCommand {
                    Ok(UserInfoResponse(JsonObject(mapOf("sub" to JsonPrimitive("user-123")))))
                }
            val command = newCommand(validate = validate, handle = handle)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("Authorization" to "Bearer abc.def.ghi"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertTrue(result.value.body!!.contains("user-123"))
            assertEquals(1, validate.invocations, "validate must be invoked exactly once on the bearer path")
            assertEquals(1, handle.invocations, "handle-userinfo must run after a successful validate")
            assertEquals("abc.def.ghi", handle.lastArgs?.accessToken)
            assertNull(result.value.headers["DPoP-Nonce"], "Bearer responses must not carry a DPoP-Nonce")
        }

    /**
     * RFC 8705 §3.2: when validate signals a `cnf.x5t#S256` mismatch (rendered as the generic
     * `invalid_token` code by the resource-server error type), the HTTP shell returns 401
     * `invalid_token` and never reaches the userinfo handler.
     */
    @Test
    fun validate_rejectsForCnfX5tMismatch_returns401InvalidToken() =
        runTest {
            val validate =
                FakeValidateAccessTokenCommand {
                    Err(
                        IdkError.fromDTO(
                            ResourceServerError.InvalidToken(
                                reason = "TLS client certificate does not match the access token cnf.x5t#S256 binding",
                            ),
                        ),
                    )
                }
            val handle = FakeUserInfoCommand { error("must not run on validate failure") }
            val command = newCommand(validate = validate, handle = handle)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("Authorization" to "Bearer abc.def.ghi"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertTrue(result.value.body!!.contains("invalid_token"))
            assertTrue(result.value.body!!.contains("cnf.x5t#S256"))
            assertEquals(1, validate.invocations)
            assertEquals(0, handle.invocations)
        }

    /**
     * RFC 9449 §7.1: when validate signals an `invalid_dpop_proof` error (e.g. missing DPoP
     * header for a DPoP-authenticated request), the HTTP shell returns 401 `invalid_dpop_proof`
     * with a `DPoP-Nonce` header so the client can retry under the current rolling nonce.
     */
    @Test
    fun validate_rejectsForMissingDpopProof_returns401InvalidDpopProofWithNonceHeader() =
        runTest {
            val validate =
                FakeValidateAccessTokenCommand {
                    Err(
                        IdkError.fromDTO(
                            ResourceServerError.InvalidDpopProof(
                                reason = "Missing DPoP header for DPoP-authenticated request",
                            ),
                        ),
                    )
                }
            val handle = FakeUserInfoCommand { error("must not run on validate failure") }
            val command = newCommand(validate = validate, handle = handle)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("Authorization" to "DPoP abc.def.ghi"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertTrue(result.value.body!!.contains("invalid_dpop_proof"))
            assertEquals(
                "test-nonce-current",
                result.value.headers["DPoP-Nonce"],
                "DPoP-bearing failure responses must carry the current rolling nonce",
            )
            assertEquals(1, validate.invocations)
            assertEquals(0, handle.invocations)
        }

    @Test
    fun missingAuthorizationHeader_returns401WithoutCallingValidate() =
        runTest {
            val validate = FakeValidateAccessTokenCommand { error("must not be invoked when no auth header is present") }
            val command = newCommand(validate = validate)

            val request = GenericHttpRequest(method = "GET", path = "/userinfo")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertTrue(result.value.body!!.contains("invalid_token"))
            assertEquals(0, validate.invocations)
        }

    @Test
    fun unsupportedAuthorizationScheme_returns401WithoutCallingValidate() =
        runTest {
            val validate = FakeValidateAccessTokenCommand { error("must not be invoked for unsupported schemes") }
            val command = newCommand(validate = validate)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("Authorization" to "Basic Zm9vOmJhcg=="),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertEquals(0, validate.invocations)
        }

    /**
     * RFC 9449 §8: when the AS requires a DPoP nonce and the bearer-token presentation cannot
     * carry one, the userinfo endpoint returns 401 `use_dpop_nonce` with a freshly rotated
     * `DPoP-Nonce` header. Validate must not be invoked because the AS short-circuits before
     * reaching the resource-server boundary.
     */
    @Test
    fun bearerWhenNonceRequired_returns401UseDpopNonceWithoutCallingValidate() =
        runTest {
            val validate = FakeValidateAccessTokenCommand { error("must not be invoked on the use_dpop_nonce path") }
            val command =
                newCommand(
                    config = nonceRequiredConfig(),
                    validate = validate,
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("Authorization" to "Bearer abc.def.ghi"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertTrue(result.value.body!!.contains("use_dpop_nonce"))
            assertEquals("test-nonce-rotated", result.value.headers["DPoP-Nonce"])
            assertEquals(0, validate.invocations)
        }

    @Test
    fun oidcDisabled_returns404() =
        runTest {
            val command =
                newCommand(
                    config = TestOAuth2ServersConfigProvider(),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers = mapOf("Authorization" to "Bearer abc.def.ghi"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(404, result.value.statusCode)
        }

    @Test
    fun validateRequest_carriesHttpHeadersAndUrl_forBindingChecks() =
        runTest {
            var capturedArgs: ValidateAccessTokenArgs? = null
            val validate =
                FakeValidateAccessTokenCommand { args ->
                    capturedArgs = args
                    Ok(verifiedRequest())
                }
            val handle = FakeUserInfoCommand { Ok(UserInfoResponse(JsonObject(emptyMap()))) }
            val command = newCommand(validate = validate, handle = handle)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/userinfo",
                    headers =
                        mapOf(
                            "Authorization" to "Bearer abc.def.ghi",
                            "Host" to "as.example.test",
                        ),
                )
            command.execute(request)

            val args = assertNotNull(capturedArgs, "validate should have received a non-null args object")
            assertEquals("GET", args.request.method)
            assertEquals("http://as.example.test/userinfo", args.request.url)
            assertEquals("Bearer abc.def.ghi", args.request.headers["Authorization"])
        }

    private fun newCommand(
        config: TestOAuth2ServersConfigProvider = oidcEnabledConfig(),
        validate: FakeValidateAccessTokenCommand =
            FakeValidateAccessTokenCommand { Ok(verifiedRequest()) },
        handle: FakeUserInfoCommand =
            FakeUserInfoCommand { Ok(UserInfoResponse(JsonObject(emptyMap()))) },
    ) = UserInfoHttpEndpointCommandImpl(
        execution = TestSessionExecution(),
        handleUserInfoRequestCommand = handle,
        validateAccessTokenCommand = validate,
        configProvider = config,
        dpopNonceManager = NoOpNonceManager,
        clientCertificateExtractor = NoOpClientCertExtractor,
    )
}

/**
 * Test stub: nonce manager that returns fixed values and accepts everything. Tests that
 * exercise nonce rejection construct their own.
 */
private object NoOpNonceManager : DpopNonceManager {
    override suspend fun currentNonce(): String = "test-nonce-current"

    override suspend fun rotate(): String = "test-nonce-rotated"

    override suspend fun isValid(nonce: String): Boolean = true
}

/** Test stub: never surfaces a TLS client certificate. Suite covers Bearer / DPoP only. */
private object NoOpClientCertExtractor : ClientCertificateExtractor {
    override suspend fun extractCertificate(request: GenericHttpRequest): IdkResult<ByteArray?, AuthorizationServerError> = Ok(null)
}
