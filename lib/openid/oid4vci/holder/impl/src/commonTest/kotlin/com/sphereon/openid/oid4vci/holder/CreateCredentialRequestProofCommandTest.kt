/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.holder.impl.CreateCredentialRequestProofCommandImpl
import com.sphereon.openid.oid4vci.common.model.stringValues
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueResult
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaClientAttestationAuthRequest
import com.sphereon.wallet.wsca.WscaClientAttestationAuthResult
import com.sphereon.wallet.wsca.WscaDpopProofRequest
import com.sphereon.wallet.wsca.WscaDpopProofResult
import com.sphereon.wallet.wsca.WscaUserAuthentication
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateCredentialRequestProofCommandTest {
    @Test
    fun jwtProofAddsKeyAttestationHeader() =
        runTest {
            val wsca = RecordingWsca()
            val command = CreateCredentialRequestProofCommandImpl(ProofTestSessionExecution(), wsca)

            val result =
                command.execute(
                    CreateCredentialRequestProofArgs(
                        walletUnitId = "wallet-unit-1",
                        operationBinding = "issuance-1",
                        issuerUrl = "https://issuer.example.com",
                        cNonce = "nonce-1",
                        signingKeyIds = listOf("holder-key-1"),
                        keyAttestationJwt = "key.attestation.jwt",
                    ),
                )

            assertEquals("jwt", result.value.proofs.proofType)
            val protectedHeader =
                Json.parseToJsonElement(
                    requireNotNull(wsca.capturedSigningInput)
                        .decodeToString()
                        .substringBefore('.')
                        .decodeFromBase64Url()
                        .decodeToString(),
                ).jsonObject
            assertEquals("key.attestation.jwt", protectedHeader["key_attestation"]?.jsonPrimitive?.content)
        }

    @Test
    fun attestationProofReturnsKeyAttestationWithoutSigningPopJwt() =
        runTest {
            val wsca = RecordingWsca()
            val command = CreateCredentialRequestProofCommandImpl(ProofTestSessionExecution(), wsca)

            val result =
                command.execute(
                    CreateCredentialRequestProofArgs(
                        walletUnitId = null,
                        operationBinding = null,
                        issuerUrl = "https://issuer.example.com",
                        cNonce = "nonce-1",
                        signingKeyIds = listOf("holder-key-1"),
                        keyAttestationJwt = "key.attestation.jwt",
                        proofType = "attestation",
                    ),
                )

            assertEquals("attestation", result.value.proofs.proofType)
            assertEquals("key.attestation.jwt", result.value.proofs.stringValues().single())
            assertNull(wsca.capturedSigningInput, "attestation proof type should not mint an extra PoP JWT")
        }
}

private class RecordingWsca : Wsca {
    var capturedSigningInput: ByteArray? = null

    override val wscdProfile: WscdProfile
        get() = error("Not needed for this test")
    override val userAuthentication: WscaUserAuthentication
        get() = error("Not needed for this test")

    override suspend fun ensureKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
        keyAlias: String?,
    ): IdkResult<WalletAttestedKeyRef, IdkError> =
        Ok(
            WalletAttestedKeyRef(
                keyId = requireNotNull(keyAlias),
                algorithm = "ES256",
                publicKeyJwk = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AQ\",\"y\":\"Ag\"}",
                keyRef = keyAlias,
                walletUnitId = walletUnitId,
            ),
        )

    override suspend fun createCredentialKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
    ): IdkResult<WalletAttestedKeyRef, IdkError> = error("Not needed for this test")

    override suspend fun sign(
        walletUnitId: String,
        keyRef: WalletAttestedKeyRef,
        signingInput: ByteArray,
        operationBinding: String,
    ): IdkResult<ByteArray, IdkError> {
        capturedSigningInput = signingInput
        return Ok(byteArrayOf(1, 2, 3))
    }

    override suspend fun createDpopProof(request: WscaDpopProofRequest): IdkResult<WscaDpopProofResult, IdkError> =
        error("Not needed for this test")

    override suspend fun createClientAttestationAuth(
        request: WscaClientAttestationAuthRequest,
    ): IdkResult<WscaClientAttestationAuthResult, IdkError> = error("Not needed for this test")

    override suspend fun attestKeys(request: KeyAttestationIssueRequest): IdkResult<KeyAttestationIssueResult, IdkError> =
        error("Not needed for this test")
}

private class ProofTestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for unit tests")
    override val log: SessionLogService = ProofNoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = ProofNoOpContextConfig()
}

private class ProofNoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-credential-proof-log"
    override val isEnabled: Boolean = false
    override val scope = IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("Not needed for unit tests")

    override suspend fun setConfig(config: LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for unit tests")
}

private class ProofNoOpContextConfig : ContextConfig {
    override val app: AppConfigService
        get() = throw NotImplementedError("Not needed for unit tests")
    override val tenant: TenantConfigService
        get() = throw NotImplementedError("Not needed for unit tests")
    override val principal: PrincipalConfigService
        get() = throw NotImplementedError("Not needed for unit tests")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for unit tests")
}
