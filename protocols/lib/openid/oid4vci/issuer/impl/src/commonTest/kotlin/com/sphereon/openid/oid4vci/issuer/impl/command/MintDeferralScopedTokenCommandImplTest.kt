/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class MintDeferralScopedTokenCommandImplTest {
    /**
     * Records the payload passed to [execute] and returns a deterministic fake JWS so
     * tests can decode and assert on the claims without standing up real crypto.
     *
     * Implements [CreateJwsCompactCommand] (the bound IDK Command) so the test fixture
     * matches the dependency the production impl actually injects.
     */
    private class CapturingJwsCommand : CreateJwsCompactCommand {
        var capturedArgs: CreateJwsArgs? = null
        var failure: IdkError? = null

        override val inputTypeToken: TypeToken<CreateJwsArgs> = typeToken()
        override val outputTypeToken: TypeToken<JwtCompactResult> = typeToken()
        override val isEnabled: Boolean = true

        override suspend fun execute(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            capturedArgs = args
            val err = failure
            if (err != null) {
                return com.sphereon.core.api
                    .Err(err)
            }
            val headerJson = (args.opts.protectedHeader ?: error("expected protected header")).toString()
            val payloadJson =
                args.payload as? String ?: error("expected JSON string payload")
            val header = base64UrlEncode(headerJson.encodeToByteArray())
            val payload = base64UrlEncode(payloadJson.encodeToByteArray())
            val signature = base64UrlEncode("fake-sig".encodeToByteArray())
            return Ok(JwtCompactResult(jwt = "$header.$payload.$signature"))
        }

        override suspend fun supports(args: Any): Boolean = args is CreateJwsArgs

        private fun base64UrlEncode(bytes: ByteArray): String = bytes.encodeToBase64Url()
    }

    private class FakeConfigProvider(
        override val issuerIdentifier: String = "https://issuer.example/oid4vci",
    ) : Oid4vciIssuerConfigProvider {
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()
        override val authorizationServers: List<String>? = null
        override val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null
    }

    private fun fixedClock(epochSeconds: Long = 1_700_000_000): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochSeconds(epochSeconds)
        }

    private fun managedIdentifier(): ManagedIdentifierOptsOrResult = ManagedOptsAlias(identifier = "as-signing-key")

    private fun command(
        jwsCommand: CapturingJwsCommand,
        signer: ManagedIdentifierOptsOrResult? = managedIdentifier(),
        configProvider: Oid4vciIssuerConfigProvider = FakeConfigProvider(),
        clock: Clock = fixedClock(),
    ): MintDeferralScopedTokenCommand =
        MintDeferralScopedTokenCommandImpl(
            execution = TestSessionExecution(),
            createJwsCompactCommand = jwsCommand,
            signingIdentifierResolverProvider =
                Provider {
                    object : AsServerSigningIdentifierResolver {
                        override suspend fun resolveSigningIdentifier(): ManagedIdentifierOptsOrResult? = signer
                    }
                },
            issuerConfigProvider = configProvider,
            clock = clock,
        )

    private fun decodePayload(jws: String): JsonObject {
        val parts = jws.split('.')
        check(parts.size == 3) { "expected 3 JWS parts, got ${parts.size}" }
        val payloadBytes = parts[1].decodeFromBase64Url()
        return Json.parseToJsonElement(payloadBytes.decodeToString()).jsonObject
    }

    private fun decodeHeader(jws: String): JsonObject {
        val parts = jws.split('.')
        check(parts.size == 3) { "expected 3 JWS parts, got ${parts.size}" }
        val headerBytes = parts[0].decodeFromBase64Url()
        return Json.parseToJsonElement(headerBytes.decodeToString()).jsonObject
    }

    @Test
    fun mintProducesAtJwtWithDeferredCredentialScopeAndCorrelationClaims() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            val cmd = command(jwsCommand)

            val result =
                cmd.execute(
                    MintDeferralScopedTokenArgs(
                        correlationId = "corr-123",
                        transactionId = "txn-456",
                        ttlSeconds = 3600L,
                    ),
                )

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            val jws = result.value.accessToken
            assertTrue(jws.isNotBlank(), "minted access token must be non-blank")
            assertEquals(3600L, result.value.expiresInSeconds)

            val header = decodeHeader(jws)
            assertEquals("at+jwt", header["typ"]?.jsonPrimitive?.content, "header typ must be at+jwt per RFC 9068")

            val payload = decodePayload(jws)
            assertEquals("https://issuer.example/oid4vci", payload["iss"]?.jsonPrimitive?.content)
            assertEquals("https://issuer.example/oid4vci", payload["aud"]?.jsonPrimitive?.content)
            assertEquals(1_700_000_000L, payload["iat"]?.jsonPrimitive?.content?.toLong())
            assertEquals(1_700_003_600L, payload["exp"]?.jsonPrimitive?.content?.toLong())
            assertEquals("deferred_credential", payload["scope"]?.jsonPrimitive?.content)
            assertEquals("corr-123", payload["correlation_id"]?.jsonPrimitive?.content)
            assertEquals("txn-456", payload["transaction_id"]?.jsonPrimitive?.content)
            assertNull(payload["cnf"], "no cnfJkt provided so no cnf claim must be emitted")
        }

    @Test
    fun mintIncludesCnfJktWhenSupplied() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            val cmd = command(jwsCommand)

            val result =
                cmd.execute(
                    MintDeferralScopedTokenArgs(
                        correlationId = "corr-1",
                        transactionId = "txn-1",
                        ttlSeconds = 600L,
                        cnfJkt = "abcdef-thumbprint",
                    ),
                )
            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")

            val payload = decodePayload(result.value.accessToken)
            val cnf = payload["cnf"]?.jsonObject
            assertNotNull(cnf, "cnf must be present when cnfJkt is supplied")
            assertEquals("abcdef-thumbprint", cnf["jkt"]?.jsonPrimitive?.content)
        }

    @Test
    fun mintUsesExplicitAudienceWhenSupplied() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            val cmd = command(jwsCommand)

            val result =
                cmd.execute(
                    MintDeferralScopedTokenArgs(
                        correlationId = "corr-1",
                        transactionId = "txn-1",
                        ttlSeconds = 60L,
                        audience = "https://issuer.example/oid4vci/credential",
                    ),
                )
            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")

            val payload = decodePayload(result.value.accessToken)
            assertEquals(
                "https://issuer.example/oid4vci/credential",
                payload["aud"]?.jsonPrimitive?.content,
                "aud must match the explicit audience override",
            )
        }

    @Test
    fun mintWithoutSigningIdentifierErrs() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            val cmd = command(jwsCommand, signer = null)

            val result =
                cmd.execute(
                    MintDeferralScopedTokenArgs(
                        correlationId = "corr-1",
                        transactionId = "txn-1",
                        ttlSeconds = 60L,
                    ),
                )

            assertTrue(result.isErr, "expected Err when no AS signing identifier configured")
            assertEquals("INVALID_STATE", result.error.code)
            assertNull(jwsCommand.capturedArgs, "JWS command must NOT be called when signing identifier missing")
        }

    @Test
    fun signingFailurePropagates() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            jwsCommand.failure = IdkError.INVALID_STATE(message = "kms unavailable")
            val cmd = command(jwsCommand)

            val result =
                cmd.execute(
                    MintDeferralScopedTokenArgs(
                        correlationId = "corr-1",
                        transactionId = "txn-1",
                        ttlSeconds = 60L,
                    ),
                )

            assertTrue(result.isErr, "expected Err propagated from the JWS command")
            assertEquals("INVALID_STATE", result.error.code)
        }
}
