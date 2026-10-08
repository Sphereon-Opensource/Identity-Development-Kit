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
 * See the License for the specific License for the specific language governing
 * permissions and limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.impl.command.introspection.AuthServerIntrospectTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.introspection.InternalIntrospectionClientAuthorizer
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.testutil.fixedSigningIdentifierResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Access tokens must carry the SigningKeyStore `kid` in the JWS header so RPs can
 * select the JWKS entry. PKCS12 reload drops the KMS-side kid; mint must not depend
 * on it. Production change that would fail this test: dropping `kid` from the
 * protected header passed to [JwtService.createJwsCompact].
 */
class CreateAccessTokenKidHeaderTest {
    private val ctx = OAuth2ServerTestContext("create-access-token-kid-test", this)

    private val configProvider =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(issuer = ISSUER)),
            ),
        )

    @Test
    fun accessTokenProtectedHeaderCarriesStoreKidBeforeSign() =
        runTest {
            val jwtService = RecordingJwtService()
            val command =
                CreateAccessTokenCommandImpl(
                    execution = ctx.execution,
                    jwtService = jwtService,
                    tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl()),
                    secureRandom = defaultSecureRandom(),
                    configProvider = configProvider,
                    signingIdentifierResolver =
                        fixedSigningIdentifierResolver(
                            ManagedOptsKeyInfo(
                                identifier =
                                    KeyInfo<KeyType>(
                                        alias = "sts-id-token-signing",
                                        kid = STORE_KID,
                                        providerId = "software",
                                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    ),
                            ),
                        ),
                    eventService = null,
                )

            val result =
                command.execute(
                    CreateAccessTokenArgs(
                        subject = "user-1",
                        clientId = "portal",
                        scope = "openid",
                        audience = listOf("https://api.example.com"),
                    ),
                )
            assertTrue(result.isOk, "access token mint must succeed: ${if (result.isErr) result.error else ""}")
            val header = jwtService.lastArgs?.opts?.protectedHeader
            assertNotNull(header, "mint must pass a protected header to the signer")
            assertEquals(
                STORE_KID,
                header["kid"]?.jsonPrimitive?.contentOrNull,
                "protected header must carry the SigningKeyStore kid, not wait for KMS resolution",
            )
            assertEquals("at+jwt", header["typ"]?.jsonPrimitive?.contentOrNull)
            val payload = Json.parseToJsonElement(requireNotNull(jwtService.lastArgs).payload.toString()) as JsonObject
            assertEquals("https://api.example.com", payload["aud"]?.jsonPrimitive?.contentOrNull)
        }

    @Test
    fun jwtAccessTokenWithoutAudienceIsRejected() =
        runTest {
            val jwtService = RecordingJwtService()
            val command =
                CreateAccessTokenCommandImpl(
                    execution = ctx.execution,
                    jwtService = jwtService,
                    tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl()),
                    secureRandom = defaultSecureRandom(),
                    configProvider = configProvider,
                    signingIdentifierResolver =
                        fixedSigningIdentifierResolver(
                            ManagedOptsKeyInfo(
                                identifier =
                                    KeyInfo<KeyType>(
                                        alias = "sts-id-token-signing",
                                        kid = STORE_KID,
                                        providerId = "software",
                                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    ),
                            ),
                        ),
                    eventService = null,
                )

            val result =
                command.execute(
                    CreateAccessTokenArgs(
                        subject = "user-1",
                        clientId = "portal",
                        scope = "openid",
                    ),
                )

            assertTrue(result.isErr, "RFC 9068 JWT access token mint must fail without aud")
            assertEquals("invalid_target", result.error.code)
            assertEquals(null, jwtService.lastArgs, "the signer must not receive an audience-less JWT")
        }

    @Test
    fun mintedCompactJwtHeaderKidMatchesStoreKidWhenKmsKidDiffers() =
        runTest {
            val keyPair =
                ctx.keyManagerService.generateKeyAsync(
                    alias = "sts-kms-alias",
                    use = JwkUse.sig,
                    keyOperations = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            val kmsKid = keyPair.kid
            assertTrue(!kmsKid.isNullOrBlank() && kmsKid != STORE_KID, "fixture needs a KMS kid distinct from the store kid")

            val jwtService = (ctx.session.graph as JwtServiceImpl.Graph).jwtService
            val tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl())
            val federationMetadata = JsonObject(mapOf(
                "upstream_iss" to JsonPrimitive("https://idp.example.test"),
                "upstream_sub" to JsonPrimitive("idp-user-42"),
                "userinfo" to JsonObject(mapOf("given_name" to JsonPrimitive("Ada"))),
            ))
            val command =
                CreateAccessTokenCommandImpl(
                    execution = ctx.execution,
                    jwtService = jwtService,
                    tokenStorage = tokenStorage,
                    secureRandom = defaultSecureRandom(),
                    configProvider = configProvider,
                    signingIdentifierResolver =
                        fixedSigningIdentifierResolver(
                            ManagedOptsKeyInfo(
                                identifier =
                                    KeyInfo<KeyType>(
                                        alias = keyPair.alias,
                                        kid = STORE_KID,
                                        providerId = keyPair.providerId,
                                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    ),
                            ),
                        ),
                    eventService = null,
                )

            val result = command.execute(CreateAccessTokenArgs(
                subject = "user-1", clientId = "portal", scope = "openid",
                audience = listOf("https://api.example.com"),
                additionalClaims = mapOf("oidc.internal.federation_claims" to federationMetadata),
            ))
            assertTrue(result.isOk, "access token mint must succeed: ${if (result.isErr) result.error else ""}")
            val compact = result.value.value
            val payload = Json.parseToJsonElement(compact.split('.')[1].decodeFromBase64Url().decodeToString()) as JsonObject
            assertFalse(payload.keys.any { it.startsWith("oidc.") || it in setOf("given_name", "upstream_iss", "upstream_sub", "userinfo") })
            assertEquals(federationMetadata, assertNotNull(tokenStorage.getAccessToken(compact).value).additionalData["oidc.internal.federation_claims"])
            val introspector = AuthServerIntrospectTokenCommandImpl(
                execution = ctx.execution, tokenStorage = tokenStorage, configProvider = configProvider,
                internalClientAuthorizer = InternalIntrospectionClientAuthorizer { Ok(it == "credential-issuer") },
            )
            val internal = introspector.execute(IntrospectTokenArgs(token = compact, clientId = "credential-issuer"))
            assertTrue(internal.isOk)
            assertTrue(internal.value.active)
            assertEquals(federationMetadata, internal.value.additionalClaims["oidc.internal.federation_claims"])
            val owner = introspector.execute(IntrospectTokenArgs(token = compact, clientId = "portal"))
            assertTrue(owner.isOk)
            assertTrue(owner.value.active)
            assertTrue(owner.value.additionalClaims.isEmpty())
            val header = decodeHeader(compact)
            assertEquals(
                STORE_KID,
                header["kid"]?.jsonPrimitive?.contentOrNull,
                "compact JWT kid must be the store kid ($STORE_KID), not the KMS kid ($kmsKid)",
            )
        }

    private fun decodeHeader(compactJwt: String): JsonObject {
        val segments = compactJwt.split(".")
        assertEquals(3, segments.size, "compact JWT must have 3 segments, got: $compactJwt")
        val json = segments[0].decodeFromBase64Url().decodeToString()
        return Json.parseToJsonElement(json) as JsonObject
    }

    companion object {
        private const val ISSUER = "https://as.example.com"
        private const val STORE_KID = "sts-id-token-signing-1"
    }

    private class RecordingJwtService : JwtService {
        private val notImpl =
            IdkError(code = "not_implemented", message = IdkError.Message(i18nKey = "", defaultMessage = "Not implemented"))

        var lastArgs: CreateJwsArgs? = null
            private set

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            lastArgs = args
            val payloadJson =
                args.payload as? String
                    ?: return Err(
                        IdkError(
                            code = "bad_payload",
                            message = IdkError.Message(i18nKey = "", defaultMessage = "expected String payload"),
                        ),
                    )
            val header = args.opts.protectedHeader?.toString()?.encodeToByteArray()?.encodeToBase64Url() ?: "e30"
            val payloadSegment = payloadJson.encodeToByteArray().encodeToBase64Url()
            return Ok(JwtCompactResult(jwt = "$header.$payloadSegment.sig"))
        }

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = Err(notImpl)

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = Err(notImpl)

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> = Err(notImpl)

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
