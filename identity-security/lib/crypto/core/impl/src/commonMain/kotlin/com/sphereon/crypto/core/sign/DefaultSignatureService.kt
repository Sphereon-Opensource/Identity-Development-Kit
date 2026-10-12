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
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
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
import com.sphereon.crypto.jose.jws.command.managedSigningSelector
import com.sphereon.crypto.jose.jws.command.jwsSigningAlgorithmCompatibilityFailure
import com.sphereon.crypto.jose.jws.command.verifyManagedJwsSigningResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierKeyResult
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Default IDK implementation of [SignatureService].
 *
 * Uses the routed signing command for JWS and [KeyManagerService] for local RAW/COSE operations.
 * When EDK is on the classpath, EDK's implementation replaces this binding
 * and adds support for CAdES/PAdES/JAdES/XAdES.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CoreSignatureService>())
class DefaultSignatureService(
    private val execution: SessionExecution,
    private val keyManagerService: KeyManagerService,
    private val prepareJwsCommand: PrepareJwsCommand,
    private val createRawSignatureCommand: CreateRawSignatureCommand,
) : CoreSignatureService {
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
        val prepared = prepareResult.getOrElse { error ->
            log.warn("JWS_SIGNING_REFUSED stage=prepare code=${error.code}")
            return Err(error)
        }
        val resolvedKey = (prepared.identifier as ManagedIdentifierKeyResult).keyInfo
        val headerAlgorithm = prepared.jws.protectedHeader?.get("alg")?.jsonPrimitive?.contentOrNull
        val managedSelection = try {
            // Preparation yields public material. Keep private signing in the KMS selected by
            // that resolution, without borrowing custody for caller-supplied inline material.
            managedSigningSelector(ManagedOptsKeyInfo(identifier = keyInfo), resolvedKey).also { selector ->
                if (selector != null) {
                    resolvedKey.jwsSigningAlgorithmCompatibilityFailure(headerAlgorithm)?.let { error(it) }
                }
            }
        } catch (expected: Exception) {
            log.warn("JWS_SIGNING_REFUSED stage=managed-selection cause=${expected::class.simpleName}")
            return Err(IdkError.fromString("Invalid managed signing selection", exception = expected))
        }

        val signatureResult =
            try {
                // The local key manager intentionally bypasses transport routing. JWS must use
                // the existing authenticated command boundary to reach the tenant's KMS owner.
                createRawSignatureCommand.execute(CreateRawSignatureArgs(
                    keyInfo = managedSelection ?: resolvedKey,
                    input = prepared.signingInput,
                    requireX5Chain = false,
                ))
            } catch (expected: Exception) {
                log.error("JWS_SIGNING_FAILED stage=kms-sign cause=${expected::class.simpleName}")
                return Err(IdkError.UNKNOWN_ERROR(message = "JWS signing failed: ${expected.message}", exception = expected))
            }
        val signatureBytes = signatureResult.getOrElse { error ->
            log.warn("JWS_SIGNING_REFUSED stage=kms-sign code=${error.code}")
            return Err(error)
        }.signature
        if (managedSelection != null) {
            val matches = try {
                verifyManagedJwsSigningResult(resolvedKey, headerAlgorithm, prepared.signingInput, signatureBytes)
            } catch (expected: Exception) {
                log.error("JWS_SIGNING_FAILED stage=signature-consistency cause=${expected::class.simpleName}")
                return Err(IdkError.fromString("Managed signature verification failed", exception = expected))
            }
            if (!matches) {
                log.warn("JWS_SIGNING_REFUSED stage=signature-consistency reason=prepared-key-mismatch")
                return Err(IdkError.fromString("Managed signature does not match the prepared public key"))
            }
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

/**
 * Default unqualified signing binding for applications that do not select a richer signer.
 *
 * This is deliberately separate from [DefaultSignatureService].  Final applications can replace
 * this one binding with a DSS decorator while retaining the ordinary implementation as the
 * public [CoreSignatureService] dependency.  Replacing a class that contributed both bindings
 * would also remove the delegate and either create a cycle or leave the decorator unresolved.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SignatureService>())
class DefaultSignatureServiceBinding(
    private val coreSignatureService: CoreSignatureService,
) : SignatureService by coreSignatureService
