/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofArgs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofCommand
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.wsca.Wsca
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

/**
 * Creates OID4VCI 1.0 Final Appendix F.1 credential-request proofs.
 *
 * Holder keys are created and used exclusively through [Wsca]. WSCA selects the WSCD; that WSCD
 * owns its configured KMS. This command never accepts or resolves a KMS provider or managed key.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialRequestProofCommand>())
class CreateCredentialRequestProofCommandImpl(
    execution: SessionExecution,
    private val wsca: Wsca,
) : TypedServiceCommandAdapter<CreateCredentialRequestProofArgs, CreatedProof, IdkError>(
        commandId = CreateCredentialRequestProofCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateCredentialRequestProofArgs>(),
        outputTypeToken = typeToken<CreatedProof>(),
    ),
    CreateCredentialRequestProofCommand {
    override val commandId: String get() = CreateCredentialRequestProofCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateCredentialRequestProofArgs

    override suspend fun doExecute(
        args: CreateCredentialRequestProofArgs,
        applyDuring: (CreateCredentialRequestProofArgs) -> CreateCredentialRequestProofArgs,
    ): IdkResult<CreatedProof, IdkError> {
        val applied = applyDuring(args)

        if (applied.proofType == ATTESTATION_PROOF_TYPE) {
            val attestationJwt =
                applied.keyAttestationJwt?.takeIf { it.isNotBlank() }
                    ?: return Err(
                        IdkError.fromString(
                            code = "MISSING_KEY_ATTESTATION",
                            message = "Proof type 'attestation' requires keyAttestationJwt",
                        ),
                    )
            return Ok(CreatedProof(proofs = CredentialRequestProofs.attestation(attestationJwt)))
        }

        if (applied.proofType != JWT_PROOF_TYPE) {
            return Err(
                IdkError.fromString(
                    code = "UNSUPPORTED_PROOF_TYPE",
                    message = "Proof type '${applied.proofType}' is not supported by the holder",
                ),
            )
        }

        val walletUnitId = requireNotNull(applied.walletUnitId)
        val operationBinding = requireNotNull(applied.operationBinding)
        val signatureAlgorithm = signatureAlgorithm(applied.signingAlgorithm).getOrElse { return Err(it) }
        val signedJwts = mutableListOf<String>()
        for (signingKeyId in applied.signingKeyIds) {
            val keyRef =
                wsca
                    .ensureKey(
                        walletUnitId = walletUnitId,
                        usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                        algorithm = signatureAlgorithm,
                        keyAlias = signingKeyId,
                    ).getOrElse { return Err(it) }
            val jwt =
                createSingleProofJwt(
                    args = applied,
                    keyRef = keyRef,
                    walletUnitId = walletUnitId,
                    operationBinding = operationBinding,
                ).getOrElse { return Err(it) }
            signedJwts += jwt
        }

        return Ok(CreatedProof(proofs = CredentialRequestProofs.jwt(signedJwts)))
    }

    private suspend fun createSingleProofJwt(
        args: CreateCredentialRequestProofArgs,
        keyRef: WalletAttestedKeyRef,
        walletUnitId: String,
        operationBinding: String,
    ): IdkResult<String, IdkError> {
        val protectedHeader =
            buildJsonObject {
                put("typ", JsonPrimitive(PROOF_JWT_TYP))
                put("alg", JsonPrimitive(args.signingAlgorithm))
                when (args.keyInclusionMode) {
                    JwsIdentifierMode.AUTO,
                    JwsIdentifierMode.JWK,
                    -> {
                        val publicJwk =
                            keyRef.publicKeyJwk
                                ?: return Err(
                                    IdkError.fromString(
                                        code = "oid4vci.credential_proof_key_missing_jwk",
                                        message = "WSCA holder key '${keyRef.keyId}' does not expose its public JWK",
                                    ),
                                )
                        put("jwk", json.parseToJsonElement(publicJwk))
                    }

                    JwsIdentifierMode.KID -> put("kid", JsonPrimitive(keyRef.keyId))
                    JwsIdentifierMode.DID -> {
                        if (!keyRef.keyId.startsWith("did:")) {
                            return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "DID proof mode requires the WSCA key id to be a DID URL",
                                ),
                            )
                        }
                        put("kid", JsonPrimitive(keyRef.keyId))
                    }

                    JwsIdentifierMode.X5C ->
                        return Err(
                            IdkError.INVALID_STATE(
                                message = "X5C proof mode requires a certificate chain surfaced by the selected WSCD",
                            ),
                        )
                }
                args.keyAttestationJwt?.takeIf { it.isNotBlank() }?.let { put("key_attestation", JsonPrimitive(it)) }
            }
        val payload =
            buildJsonObject {
                put("aud", JsonPrimitive(args.issuerUrl))
                put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
                args.cNonce?.let { put("nonce", JsonPrimitive(it)) }
                args.clientId?.let { put("iss", JsonPrimitive(it)) }
            }
        val encodedHeader = json.encodeToString(protectedHeader).encodeToByteArray().encodeToBase64Url()
        val encodedPayload = json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        val signingInput = "$encodedHeader.$encodedPayload".encodeToByteArray()
        val signature =
            wsca
                .sign(
                    walletUnitId = walletUnitId,
                    keyRef = keyRef,
                    signingInput = signingInput,
                    operationBinding = operationBinding,
                ).getOrElse { return Err(it) }
        return Ok("$encodedHeader.$encodedPayload.${signature.encodeToBase64Url()}")
    }

    private fun signatureAlgorithm(value: String): IdkResult<SignatureAlgorithm, IdkError> =
        try {
            Ok(SignatureAlgorithm.fromJose(JwaAlgorithm.fromValue(value)))
        } catch (expected: Exception) {
            Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported OID4VCI proof signing algorithm '$value'",
                    throwable = expected,
                ),
            )
        }

    companion object {
        const val PROOF_JWT_TYP = "openid4vci-proof+jwt"
        const val JWT_PROOF_TYPE = "jwt"
        const val ATTESTATION_PROOF_TYPE = "attestation"
        val json = Json { encodeDefaults = false; explicitNulls = false }
    }
}
