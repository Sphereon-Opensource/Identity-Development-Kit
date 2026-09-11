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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofArgs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofCommand
import com.sphereon.openid.oid4vci.holder.CredentialRequestProofPreparationRequest
import com.sphereon.openid.oid4vci.holder.CredentialRequestProofPreparation
import com.sphereon.openid.oid4vci.holder.CreatedProof
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Creates OID4VCI 1.0 Final Appendix F.1 credential-request proofs.
 *
 * Holder keys are prepared and used exclusively through [CredentialRequestProofPreparation].
 * That seam selects the WSCA/WSCD; this command never accepts or resolves a KMS provider or
 * managed key.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialRequestProofCommand>())
class CreateCredentialRequestProofCommandImpl(
    execution: SessionExecution,
    private val proofPreparation: CredentialRequestProofPreparation,
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
        return proofPreparation.finalize(
            proofPreparation
                .prepare(
                    CredentialRequestProofPreparationRequest(
                        walletUnitId = walletUnitId,
                        operationBinding = operationBinding,
                        issuerUrl = applied.issuerUrl,
                        cNonce = applied.cNonce,
                        signingKeyIds = applied.signingKeyIds,
                        signingAlgorithm = applied.signingAlgorithm,
                        clientId = applied.clientId,
                        keyInclusionMode = applied.keyInclusionMode.name,
                        keyAttestationJwt = applied.keyAttestationJwt,
                        iatEpochSeconds = Clock.System.now().epochSeconds,
                    ),
                ).getOrElse { return Err(it) },
        )
    }

    companion object {
        const val JWT_PROOF_TYPE = "jwt"
        const val ATTESTATION_PROOF_TYPE = "attestation"
    }
}
