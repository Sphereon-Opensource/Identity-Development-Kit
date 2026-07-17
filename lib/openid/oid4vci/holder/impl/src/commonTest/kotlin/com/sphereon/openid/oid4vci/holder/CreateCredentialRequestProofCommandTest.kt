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
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.holder.impl.CreateCredentialRequestProofCommandImpl
import com.sphereon.openid.oid4vci.common.model.stringValues
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateCredentialRequestProofCommandTest {
    @Test
    fun jwtProofAddsKeyAttestationHeader() =
        runTest {
            val jws = CapturingCreateJwsCompactCommand()
            val command = CreateCredentialRequestProofCommandImpl(ProofTestSessionExecution(), jws)

            val result =
                command.execute(
                    CreateCredentialRequestProofArgs(
                        issuerUrl = "https://issuer.example.com",
                        cNonce = "nonce-1",
                        signingKeyIds = listOf("holder-key-1"),
                        keyAttestationJwt = "key.attestation.jwt",
                    ),
                )

            assertEquals("jwt", result.value.proofs.proofType)
            assertEquals("key.attestation.jwt", jws.capturedArgs?.opts?.protectedHeader?.get("key_attestation")?.jsonPrimitive?.content)
        }

    @Test
    fun attestationProofReturnsKeyAttestationWithoutSigningPopJwt() =
        runTest {
            val jws = CapturingCreateJwsCompactCommand()
            val command = CreateCredentialRequestProofCommandImpl(ProofTestSessionExecution(), jws)

            val result =
                command.execute(
                    CreateCredentialRequestProofArgs(
                        issuerUrl = "https://issuer.example.com",
                        cNonce = "nonce-1",
                        signingKeyIds = listOf("holder-key-1"),
                        keyAttestationJwt = "key.attestation.jwt",
                        proofType = "attestation",
                    ),
                )

            assertEquals("attestation", result.value.proofs.proofType)
            assertEquals("key.attestation.jwt", result.value.proofs.stringValues().single())
            assertNull(jws.capturedArgs, "attestation proof type should not mint an extra PoP JWT")
        }
}

private class CapturingCreateJwsCompactCommand : CreateJwsCompactCommand {
    var capturedArgs: CreateJwsArgs? = null

    override val commandId: String = CreateJwsCompactCommand.COMMAND_ID
    override val inputTypeToken: TypeToken<CreateJwsArgs> = typeToken()
    override val outputTypeToken: TypeToken<JwtCompactResult> = typeToken()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
        capturedArgs = args
        return Ok(JwtCompactResult(jwt = "proof.jwt.sig"))
    }

    override suspend fun supports(args: Any): Boolean = args is CreateJwsArgs
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
