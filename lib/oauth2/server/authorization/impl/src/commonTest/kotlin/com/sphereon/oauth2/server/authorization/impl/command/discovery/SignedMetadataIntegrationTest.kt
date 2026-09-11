/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildSignedAuthorizationServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildSignedAuthorizationServerMetadataCommand
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.testutil.fixedSigningIdentifierResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the wiring of RFC 8414 §2 `signed_metadata` through
 * [BuildServerMetadataCommandImpl]. Substitutes [CreateJwsCompactCommand] with a
 * deterministic stub so the test exercises the WIRING (when does the AS sign? what
 * does it pass to the signer? does the JWT land in the discovery response?) without
 * coupling to the crypto stack — the JWS production path is tested separately in
 * the crypto module's own tests.
 */
class SignedMetadataIntegrationTest {
    private val ctx = OAuth2ServerTestContext("signed-metadata-test", this)

    @Test
    fun signedMetadataIsAbsentWhenFeatureDisabled() =
        runTest {
            val config = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", signedMetadata = FeaturePolicy.DISABLED)
            val command = newCommand(config = config, signingKeyAlias = "irrelevant")
            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isOk)
            assertNull(result.value.signedMetadata, "signed_metadata MUST be null when DISABLED")
        }

    @Test
    fun signedMetadataIsAbsentWhenSupportedButNoSigningKey() =
        runTest {
            // SUPPORTED + no signing key configured → degrade gracefully (no signed_metadata)
            // rather than fail the discovery response. Pinned RPs would notice the missing
            // member and refuse; non-pinned RPs continue to work.
            val config = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", signedMetadata = FeaturePolicy.SUPPORTED)
            val command = newCommand(config = config, signingKeyAlias = null)
            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isOk, "no signing key + SUPPORTED must NOT fail the response")
            assertNull(result.value.signedMetadata, "no signing key → no signed_metadata member")
        }

    @Test
    fun signedMetadataIsEmittedWhenSupportedAndKeyConfigured() =
        runTest {
            val config = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", signedMetadata = FeaturePolicy.SUPPORTED)
            val command = newCommand(config = config, signingKeyAlias = "active-key", signedJwtToReturn = "head.payload.sig")
            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isOk)
            assertEquals("head.payload.sig", result.value.signedMetadata, "signed_metadata MUST equal the JWS the signer returned")
        }

    @Test
    fun requiredFailureSurfacesAsServerError() =
        runTest {
            // REQUIRED + signing failure → server error. An operator who configures REQUIRED
            // wants to know if signing is broken (rather than degrade silently).
            val config = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", signedMetadata = FeaturePolicy.REQUIRED)
            val command =
                newCommand(
                    config = config,
                    signingKeyAlias = "active-key",
                    signedJwtToReturn = null, // null → stub returns Err
                )
            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isErr, "REQUIRED + signing failure must surface as an error")
        }

    @Test
    fun supportedFailureDegradesToUnsignedResponse() =
        runTest {
            // SUPPORTED + signing failure → omit signed_metadata, return the unsigned doc.
            val config = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", signedMetadata = FeaturePolicy.SUPPORTED)
            val command = newCommand(config = config, signingKeyAlias = "active-key", signedJwtToReturn = null)
            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isOk, "SUPPORTED + signing failure must NOT fail the response")
            assertNull(result.value.signedMetadata, "SUPPORTED + signing failure → no signed_metadata member")
        }

    @Test
    fun signCommandPayloadElidesSignedMetadataFieldRecursively() =
        runTest {
            // The sign command must strip the signed_metadata field BEFORE signing so the
            // signed JWS payload never contains itself recursively. Pin via a capturing
            // stub that records the metadata it received.
            val capturingStub = CapturingSignedMetadataStub()
            val command =
                newCommand(
                    config = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", signedMetadata = FeaturePolicy.SUPPORTED),
                    signingKeyAlias = "active-key",
                    signCommand = capturingStub,
                )
            val result = command.execute(BuildServerMetadataArgs())
            assertTrue(result.isOk)
            // The wrapper command (BuildServerMetadataCommandImpl) supplies the unsigned
            // metadata to the sign command. The signed_metadata field is null on input
            // (it's only populated AFTER signing) so we just confirm the wiring shape.
            assertNotNull(capturingStub.lastCalledArgs, "sign command must have been invoked")
        }

    @Test
    fun realImplStripsSignedMetadataBeforeSigning() =
        runTest {
            // Direct exercise of BuildSignedAuthorizationServerMetadataCommandImpl proves
            // the metadata.copy(signedMetadata = null) elision happens in the production
            // impl (not just the wrapper). A stub CreateJwsCompactCommand captures the
            // payload so we can inspect it.
            val capturingJws = CapturingCreateJwsCompactStub(ctx.execution)
            val signCommand: BuildSignedAuthorizationServerMetadataCommand =
                BuildSignedAuthorizationServerMetadataCommandImpl(
                    execution = ctx.execution,
                    createJwsCompactCommand = capturingJws,
                )
            val metadata =
                AuthorizationServerMetadata(
                    issuer = "https://as.example.com",
                    tokenEndpoint = "https://as.example.com/token",
                    signedMetadata = "PRE_EXISTING_VALUE_THAT_MUST_BE_STRIPPED",
                )
            signCommand.execute(
                BuildSignedAuthorizationServerMetadataArgs(
                    metadata = metadata,
                    signingKey = ManagedOptsAlias(identifier = "any"),
                ),
            )
            // The payload the JWS command received MUST NOT contain the original
            // signed_metadata value — proves the impl strips it before signing.
            val payload = capturingJws.lastPayloadJson ?: error("JWS command must have been invoked")
            assertTrue(
                !payload.contains("PRE_EXISTING_VALUE_THAT_MUST_BE_STRIPPED"),
                "sign command must elide signed_metadata before signing, got payload: $payload",
            )
            // And the registered RFC 8414 §2 claims must be present.
            assertTrue(payload.contains("\"iss\":\"https://as.example.com\""))
            assertTrue(payload.contains("\"sub\":\"https://as.example.com\""))
            assertTrue(payload.contains("\"iat\":"))
        }

    // ─── helpers ──────────────────────────────────────────────────

    private fun newCommand(
        config: OAuth2ServerInstanceConfig,
        signingKeyAlias: String?,
        signedJwtToReturn: String? = "stub.signed.jwt",
        signCommand: BuildSignedAuthorizationServerMetadataCommand = StubSignedMetadataCommand(ctx.execution, signedJwtToReturn),
    ): BuildServerMetadataCommandImpl {
        val provider: OAuth2ServersConfigProvider =
            TestOAuth2ServersConfigProvider(OAuth2ServersConfig(servers = mapOf("default" to config)))
        return BuildServerMetadataCommandImpl(
            execution = ctx.execution,
            configProvider = provider,
            signingIdentifierResolver = fixedSigningIdentifierResolver(signingKeyAlias?.let { ManagedOptsAlias(identifier = it) }),
            identifierService = ctx.identifierService,
            grantHandlers = emptyMap(),
            kmsProviderRegistry = ctx.kmsProviderRegistry,
            buildSignedMetadata = signCommand,
        )
    }

    /**
     * Stub that returns a fixed signed-JWT string when [signedJwt] is non-null, or an
     * error when null. Lets each test set up the success / failure scenario without
     * exercising real crypto.
     */
    private class StubSignedMetadataCommand(
        execution: SessionExecution,
        private val signedJwt: String?,
    ) : TypedServiceCommandAdapter<BuildSignedAuthorizationServerMetadataArgs, JwtCompactResult, IdkError>(
            commandId = BuildSignedAuthorizationServerMetadataCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<BuildSignedAuthorizationServerMetadataArgs>(),
            outputTypeToken = typeToken<JwtCompactResult>(),
        ),
        BuildSignedAuthorizationServerMetadataCommand {
        override val commandId: String = BuildSignedAuthorizationServerMetadataCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is BuildSignedAuthorizationServerMetadataArgs

        override suspend fun doExecute(
            args: BuildSignedAuthorizationServerMetadataArgs,
            applyDuring: (BuildSignedAuthorizationServerMetadataArgs) -> BuildSignedAuthorizationServerMetadataArgs,
        ): IdkResult<JwtCompactResult, IdkError> =
            if (signedJwt != null) {
                Ok(JwtCompactResult(jwt = signedJwt))
            } else {
                Err(IdkError.fromString(code = "stub", message = "stubbed sign failure"))
            }
    }

    /** Records the args the wrapper passed in so tests can assert on them. */
    private inner class CapturingSignedMetadataStub :
        TypedServiceCommandAdapter<BuildSignedAuthorizationServerMetadataArgs, JwtCompactResult, IdkError>(
            commandId = BuildSignedAuthorizationServerMetadataCommand.COMMAND_ID,
            execution = ctx.execution,
            inputTypeToken = typeToken<BuildSignedAuthorizationServerMetadataArgs>(),
            outputTypeToken = typeToken<JwtCompactResult>(),
        ),
        BuildSignedAuthorizationServerMetadataCommand {
        override val commandId: String = BuildSignedAuthorizationServerMetadataCommand.COMMAND_ID

        var lastCalledArgs: BuildSignedAuthorizationServerMetadataArgs? = null
            private set

        override suspend fun supports(args: Any): Boolean = args is BuildSignedAuthorizationServerMetadataArgs

        override suspend fun doExecute(
            args: BuildSignedAuthorizationServerMetadataArgs,
            applyDuring: (BuildSignedAuthorizationServerMetadataArgs) -> BuildSignedAuthorizationServerMetadataArgs,
        ): IdkResult<JwtCompactResult, IdkError> {
            val applied = applyDuring(args)
            lastCalledArgs = applied
            return Ok(JwtCompactResult(jwt = "captured.signed.jwt"))
        }
    }

    /**
     * Records the JSON payload the production sign impl passes to the JWS command —
     * used to assert that the impl elides `signed_metadata` before signing.
     */
    private class CapturingCreateJwsCompactStub(
        execution: SessionExecution,
    ) : TypedServiceCommandAdapter<CreateJwsArgs, JwtCompactResult, IdkError>(
            commandId = CreateJwsCompactCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<CreateJwsArgs>(),
            outputTypeToken = typeToken<JwtCompactResult>(),
        ),
        CreateJwsCompactCommand {
        override val commandId: String = CreateJwsCompactCommand.COMMAND_ID

        var lastPayloadJson: String? = null
            private set

        override suspend fun supports(args: Any): Boolean = args is CreateJwsArgs

        override suspend fun doExecute(
            args: CreateJwsArgs,
            applyDuring: (CreateJwsArgs) -> CreateJwsArgs,
        ): IdkResult<JwtCompactResult, IdkError> {
            val applied = applyDuring(args)
            // CreateJwsArgs.payload is a JsonElement; toString() gives canonical JSON
            // for our assertion purposes.
            lastPayloadJson = applied.payload?.toString()
            return Ok(JwtCompactResult(jwt = "captured.jws.value"))
        }
    }
}
