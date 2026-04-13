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
 *
 */

@file:Suppress("TooGenericExceptionCaught")

package com.sphereon.crypto.core.sign

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.sign.model.CompleteSignatureRequest
import com.sphereon.crypto.core.sign.model.DigestRequest
import com.sphereon.crypto.core.sign.model.DigestResponse
import com.sphereon.crypto.core.sign.model.JwsSignatureParameters
import com.sphereon.crypto.core.sign.model.RawSignatureParameters
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.SignOutputData
import com.sphereon.crypto.core.sign.model.SignatureForm
import com.sphereon.crypto.core.sign.model.SignatureLevel
import com.sphereon.crypto.core.sign.model.SignatureParameters
import com.sphereon.crypto.jose.jws.assembleCompact
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Default IDK implementation of [SignatureService].
 *
 * Supports RAW/JWS/COSE signing by delegating to [KeyManagerService].
 * When EDK is on the classpath, EDK's implementation replaces this binding
 * and adds support for CAdES/PAdES/JAdES/XAdES.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SignatureService>())
class DefaultSignatureService(
    private val execution: SessionExecution,
    private val keyManagerService: KeyManagerService,
    private val prepareJwsCommand: PrepareJwsCommand,
) : SignatureService {
    @dev.zacsweers.metro.ContributesTo(scope = SessionScope::class)
    interface Graph {
        val signatureService: SignatureService
    }

    private val log get() = execution.log

    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray = keyManagerService.createRawSignature(keyInfo, input, requireX5Chain)

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean = keyManagerService.isValidRawSignature(keyInfo, input, signature)

    override fun supportedForms(): Set<SignatureForm> = setOf(SignatureForm.RAW, SignatureForm.JWS, SignatureForm.COSE)

    override suspend fun sign(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>,
        parameters: SignatureParameters?,
    ): IdkResult<SignOutput, IdkError> {
        if (parameters is JwsSignatureParameters) {
            return signJws(signInput, keyInfo, parameters)
        }

        val requireX5Chain = (parameters as? RawSignatureParameters)?.requireX5Chain ?: false

        val signResult = keyManagerService.createRawSignatureResult(keyInfo, signInput.input, requireX5Chain)
        val signatureBytes = signResult.getOrElse { error -> return Err(error) }.signature

        val output =
            SignOutputData(
                signedData = signatureBytes,
                signatureLevel = parameters?.signatureLevel ?: SignatureLevel.RAW,
                signingTime = Clock.System.now(),
                name = signInput.name,
                mimeType = signInput.mimeType,
            )

        log.debug("Successfully created RAW signature for document: ${signInput.name}")
        return Ok(output)
    }

    private suspend fun signJws(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>,
        parameters: JwsSignatureParameters,
    ): IdkResult<SignOutput, IdkError> {
        val jwsPayload = parameters.payload ?: signInput.input

        val prepareArgs =
            CreateJwsJsonArgs(
                issuer = parameters.issuer,
                payload = jwsPayload,
                mode = parameters.mode,
                opts = parameters.opts,
            )

        val prepareResult = prepareJwsCommand.execute(prepareArgs)
        val prepared = prepareResult.getOrElse { error -> return Err(error) }

        val signatureBytes =
            try {
                keyManagerService.createRawSignature(keyInfo, prepared.signingInput, requireX5Chain = false)
            } catch (expected: Exception) {
                return Err(IdkError.UNKNOWN_ERROR(message = "JWS signing failed: ${expected.message}", exception = expected))
            }

        val compactResult = prepared.assembleCompact(signatureBytes)

        val output =
            SignOutputData(
                signedData = compactResult.jwt.encodeToByteArray(),
                signatureLevel = SignatureLevel.JWS,
                signingTime = Clock.System.now(),
                name = signInput.name,
                mimeType = "application/jwt",
            )

        log.debug("Successfully created JWS signature for document: ${signInput.name}")
        return Ok(output)
    }

    override suspend fun createDigest(request: DigestRequest): IdkResult<DigestResponse, IdkError> {
        // For RAW signing, the "digest" is simply the input data itself.
        // The KMS provider handles hashing internally during signing.
        val sessionId = "raw-digest-${Clock.System.now().toEpochMilliseconds()}"

        return Ok(
            DigestResponse(
                digestToSign = request.input.input,
                signatureDocumentBytes = request.input.input,
                documentName = request.input.name,
                sessionId = sessionId,
                signingDate = Clock.System.now(),
                keyInfo = request.keyInfo,
                parameters = request.parameters,
            ),
        )
    }

    override suspend fun validate(
        signInput: SignInput,
        signature: ByteArray,
        keyInfo: KeyInfoType<*>,
    ): IdkResult<Boolean, IdkError> =
        try {
            val isValid = isValidRawSignature(keyInfo, signInput.input, signature)
            Ok(isValid)
        } catch (expected: Exception) {
            log.error("Signature validation failed: ${expected.message}", expected)
            Err(IdkError.UNKNOWN_ERROR(message = "Signature validation failed: ${expected.message}", exception = expected))
        }

    override suspend fun completeSignature(request: CompleteSignatureRequest): IdkResult<SignOutput, IdkError> {
        val output =
            SignOutputData(
                signedData = request.signatureValue,
                signatureLevel = request.parameters.signatureLevel,
                signingTime = Clock.System.now(),
                name = request.documentName,
            )

        return Ok(output)
    }
}
