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
 */

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwsHeaderBuilder
import com.sphereon.crypto.jose.jws.JwsPayloadBuilder
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofArgs
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofCommand
import com.sphereon.openid.oid4vci.holder.CreatedProof
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * Creates one or more JWT key-binding proofs for an OID4VCI credential request.
 *
 * Per OID4VCI 1.1 Appendix F.1:
 * - JWT header: typ=openid4vci-proof+jwt, alg=<signing-alg>, plus a key identifier header
 *   whose form depends on [CreateCredentialRequestProofArgs.keyInclusionMode]:
 *   KID → kid, JWK → jwk, X5C → x5c, DID → kid (DID URL), AUTO → resolved automatically
 * - JWT payload: aud=<issuer-url>, iat=<current-timestamp>, nonce=<c_nonce> (optional), iss=<client_id> (optional)
 *
 * Always returns the `proofs` (plural) format per OID4VCI spec.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialRequestProofCommand>())
class CreateCredentialRequestProofCommandImpl(
    execution: SessionExecution,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
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

        val signingKeyIds = applied.signingKeyIds
        val count = signingKeyIds.size
        log.debug("Creating $count JWT proof(s) for issuer: ${applied.issuerUrl}")

        val signedJwts = mutableListOf<String>()
        for (signingKeyId in signingKeyIds) {
            val jwtResult = createSingleProofJwt(applied, signingKeyId).getOrElse { return Err(it) }
            signedJwts.add(jwtResult)
        }

        val createdProof =
            CreatedProof(
                proofs = CredentialRequestProofs.jwt(signedJwts),
            )

        log.debug("Successfully created $count JWT proof(s)")
        return Ok(createdProof)
    }

    private suspend fun createSingleProofJwt(
        args: CreateCredentialRequestProofArgs,
        signingKeyId: String,
    ): IdkResult<String, IdkError> {
        val iat = Clock.System.now().epochSeconds

        // Build the protected header with only typ and alg — the key identifier header
        // (kid, jwk, x5c, etc.) is added by CreateJwsCompactCommand based on keyInclusionMode.
        val protectedHeader =
            JwsHeaderBuilder
                .create()
                .typ(PROOF_JWT_TYP)
                .alg(args.signingAlgorithm)
                .apply {
                    args.keyAttestationJwt?.takeIf { it.isNotBlank() }?.let { claim("key_attestation", it) }
                }
                .build()

        // Build the payload per OID4VCI 1.1 Appendix F.1
        val payloadBuilder =
            JwsPayloadBuilder
                .create()
                .aud(args.issuerUrl)
                .iat(iat)

        args.cNonce?.let { payloadBuilder.claim("nonce", it) }
        args.clientId?.let { payloadBuilder.iss(it) }

        val payload = payloadBuilder.build()

        // Use KID-based managed identifier to sign with the specified key.
        // The KMS resolves the full key material regardless of keyInclusionMode,
        // so ManagedOptsKid is correct for all modes.
        val issuer = ManagedOptsKid(identifier = signingKeyId)

        val jwsArgs =
            CreateJwsArgs(
                issuer = issuer,
                payload = payload,
                mode = args.keyInclusionMode,
                opts =
                    CreateJwsOpts(
                        // Prevent PrepareJwsCommand from overwriting iss in the payload since we set
                        // it explicitly above via clientId.
                        noIssPayloadUpdate = true,
                        // Do NOT set noIdentifierInHeader — PrepareJwsCommand adds kid/jwk/x5c
                        // based on the resolved key and keyInclusionMode.
                        protectedHeader = protectedHeader,
                    ),
            )

        val result = createJwsCompactCommand.execute(jwsArgs).getOrElse { return Err(it) }
        return Ok(result.jwt)
    }

    companion object {
        const val PROOF_JWT_TYP = "openid4vci-proof+jwt"
        const val JWT_PROOF_TYPE = "jwt"
        const val ATTESTATION_PROOF_TYPE = "attestation"
    }
}
