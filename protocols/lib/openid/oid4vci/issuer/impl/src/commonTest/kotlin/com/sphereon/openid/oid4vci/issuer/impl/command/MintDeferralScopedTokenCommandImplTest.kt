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
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.signing.AsSigningRequirement
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.AsSigningSelection
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationGrant
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationPolicySnapshot
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerDeployment
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.issuer.impl.store.KvCredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.impl.store.KvCredentialRequestIdentityStore
import com.sphereon.openid.oid4vci.issuer.impl.store.KvDeferredCredentialStore
import com.sphereon.openid.oid4vci.issuer.impl.store.InMemoryTestKvStoreManager
import com.sphereon.openid.oid4vci.issuer.impl.store.UnconfiguredKvStoreService
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenArgs
import com.sphereon.openid.oid4vci.issuer.command.MintDeferralScopedTokenCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

    private fun managedIdentifier() =
        ManagedOptsKeyInfo(
            identifier = KeyInfo<KeyType>(alias = "as-signing-key", kid = "as-signing-kid", signatureAlgorithm = SignatureAlgorithm.RSA_SHA256),
        )

    private val issuerInstanceId = "11111111-1111-4111-8111-111111111111"
    private val asInstanceId = Uuid.parse("22222222-2222-4222-8222-222222222222")
    private val asIssuer = "https://as.example/oauth2"

    private fun asConfig(): OAuth2ServersConfigProvider = object : OAuth2ServersConfigProvider {
        private val root = OAuth2ServersConfig(
            defaultServer = "hosted",
            servers = mapOf("hosted" to OAuth2ServerInstanceConfig(issuer = asIssuer)),
        )
        override fun getConfig(): OAuth2ServersConfig = root
        override fun getServer(id: String): OAuth2ServerInstanceConfig? = root.servers[id]
        override fun getDefaultServer(): OAuth2ServerInstanceConfig = root.getDefaultServer()
        override fun resolveIssuer(serverId: String, tenantId: String): String =
            root.servers[serverId]?.issuer ?: error("No configured issuer")
    }

    private fun snapshot(issuerId: String = issuerInstanceId) = Oid4vciAuthorizationPolicySnapshot(
        issuerId = Uuid.parse(issuerId),
        authorizationServerId = asInstanceId,
        authorizationServerIssuer = asIssuer,
        authorizationServerDeployment = Oid4vciAuthorizationServerDeployment.HOSTED,
        authorizationServerRuntimeKey = "hosted",
        applicableGrants = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
        profile = Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
        profileRevision = 1,
        bindingRevision = 1,
    )


    private suspend fun command(
        jwsCommand: CapturingJwsCommand,
        signer: ManagedOptsKeyInfo? = managedIdentifier(),
        configProvider: Oid4vciIssuerConfigProvider = FakeConfigProvider(),
        clock: Clock = fixedClock(),
        correlationId: String = "corr-1",
        transactionId: String = "txn-1",
        seedBinding: Boolean = true,
        persistedIssuerInstanceId: String = issuerInstanceId,
        currentIssuerInstanceId: String = issuerInstanceId,
        selectionFailure: Exception? = null,
    ): MintDeferralScopedTokenCommand {
        val execution = TestSessionExecution()
        val manager = InMemoryTestKvStoreManager()
        val sessionStore = KvCredentialIssuanceSessionStore(manager, UnconfiguredKvStoreService, execution)
        val deferredStore = KvDeferredCredentialStore(manager, UnconfiguredKvStoreService, execution)
        val identityStore = KvCredentialRequestIdentityStore(manager, UnconfiguredKvStoreService, execution)
        if (seedBinding) {
            val now = Clock.System.now().toEpochMilliseconds()
            val session = IssuanceSession(
                sessionId = "session-$transactionId",
                instanceId = persistedIssuerInstanceId,
                issuerId = persistedIssuerInstanceId,
                credentialConfigurationIds = listOf("config-1"),
                status = IssuanceSessionStatus.DEFERRED,
                lifecycleCorrelationId = correlationId,
                authorizationPolicySnapshot = snapshot(persistedIssuerInstanceId),
                createdAt = now,
                expiresAt = now + 3_600_000L,
            )
            assertTrue(sessionStore.create(session).isOk)
            assertTrue(deferredStore.create(DeferredCredentialEntry(
                transactionId = transactionId,
                issuanceSessionId = session.sessionId,
                instanceId = persistedIssuerInstanceId,
                credentialConfigurationId = "config-1",
                status = DeferredCredentialStatus.PENDING,
                createdAt = now,
                expiresAt = now + 3_600_000L,
            )).isOk)
        }
        return MintDeferralScopedTokenCommandImpl(
            execution = execution,
            createJwsCompactCommand = jwsCommand,
            signingIdentifierResolverProvider =
                Provider {
                    object : AsServerSigningIdentifierResolver {
                        override suspend fun selectSigning(
                            captured: CapturedAsServerConfig,
                            requirement: AsSigningRequirement,
                            requestedAlgorithm: String?,
                        ): AsSigningSelection {
                            assertEquals("hosted", captured.serverKey)
                            assertEquals(AsSigningRequirement.REQUIRED, requirement)
                            selectionFailure?.let { throw it }
                            return AsSigningSelection(signer, signer?.let { setOf("RS256") } ?: emptySet())
                        }
                    }
                },
            issuerConfigProvider = configProvider,
            oauth2ConfigProvider = asConfig(),
            deferredStore = deferredStore,
            sessionStore = sessionStore,
            requestIdentityStore = identityStore,
            issuerInstanceIdProvider = object : Oid4vciIssuerInstanceIdProvider {
                override fun currentInstanceId(): String = currentIssuerInstanceId
            },
            clock = clock,
        )
    }

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
    fun mintRejectsTransactionWithoutPersistedIssuanceBindingBeforeSigning() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            val cmd = command(jwsCommand, seedBinding = false)

            val result =
                cmd.execute(
                    MintDeferralScopedTokenArgs(
                        correlationId = "corr-unbacked",
                        transactionId = "txn-without-persisted-issuance-binding",
                        ttlSeconds = 60L,
                    ),
                )

            assertTrue(result.isErr, "an unbacked transaction must fail before a deferral token can be minted")
            assertNull(jwsCommand.capturedArgs, "the signing command must not receive an unbacked transaction")
        }

    @Test
    fun mintProducesAtJwtWithDeferredCredentialScopeAndCorrelationClaims() =
        runTest {
            val jwsCommand = CapturingJwsCommand()
            val cmd = command(jwsCommand, correlationId = "corr-123", transactionId = "txn-456")

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
            assertEquals(asIssuer, payload["iss"]?.jsonPrimitive?.content)
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

    @Test
    fun persistedIssuerBTransactionCannotMintInAmbientIssuerASession() = runTest {
        val jwsCommand = CapturingJwsCommand()
        val command = command(
            jwsCommand,
            persistedIssuerInstanceId = "33333333-3333-4333-8333-333333333333",
            currentIssuerInstanceId = issuerInstanceId,
        )
        val result = command.execute(MintDeferralScopedTokenArgs("corr-1", "txn-1", 60L))
        assertTrue(result.isErr)
        assertNull(jwsCommand.capturedArgs)
    }

    @Test
    fun ordinarySigningSelectionFailureIsControlledBeforeJws() = runTest {
        val jwsCommand = CapturingJwsCommand()
        val command = command(jwsCommand, selectionFailure = IllegalStateException("signing store unavailable"))
        val result = command.execute(MintDeferralScopedTokenArgs("corr-1", "txn-1", 60L))
        assertTrue(result.isErr)
        assertEquals("INVALID_STATE", result.error.code)
        assertNull(jwsCommand.capturedArgs)
    }

    @Test
    fun signingSelectionCancellationEscapesWithoutJws() = runTest {
        val jwsCommand = CapturingJwsCommand()
        val cancellation = CancellationException("selected signing cancelled")
        val command = command(jwsCommand, selectionFailure = cancellation)
        val escaped = assertFailsWith<CancellationException> {
            command.execute(MintDeferralScopedTokenArgs("corr-1", "txn-1", 60L))
        }
        assertTrue(escaped === cancellation || escaped.cause === cancellation)
        assertNull(jwsCommand.capturedArgs)
    }
}
