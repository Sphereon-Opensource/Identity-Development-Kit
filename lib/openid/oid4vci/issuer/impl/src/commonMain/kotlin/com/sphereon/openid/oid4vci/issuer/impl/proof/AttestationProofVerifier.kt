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

package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OID4VCI 1.0 §7.2.1 standalone key-attestation proof carrier.
 *
 * The proof value IS the key-attestation JWT — there's no outer proof envelope. This
 * verifier delegates straight to [KeyAttestationVerifier], then synthesises a
 * [VerifiedProof] from the FIRST entry of `attested_keys` (per §7.2.1: when batch
 * issuance is in flight the wallet aligns its `attested_keys` array with the requested
 * credentials array; for single-credential requests the first entry is the binding key).
 *
 * Trust resolution and policy enforcement are identical to the JWT-header carrier — the
 * difference is only where the attestation rides on the wire.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<ProofVerifier>())
class AttestationProofVerifier(
    private val keyAttestationVerifier: KeyAttestationVerifier,
    private val issuerConfigProvider: Oid4vciIssuerConfigProvider,
) : ProofVerifier {
    override val supportedProofType: String = "attestation"

    override suspend fun verify(
        proofValue: JsonElement,
        expectedAudience: String,
        expectedClientId: String?,
        credentialConfigId: String,
        proofTypeSupported: ProofTypeSupported?,
    ): IdkResult<VerifiedProof, IdkError> {
        val attestationJwt =
            (proofValue as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull()
                ?: return Err(
                    IdkError.fromString(
                        code = Oid4vciErrors.INVALID_PROOF,
                        message = "Invalid attestation proof: proof value must be a key-attestation JWT string",
                    ),
                )

        val trustConfig = issuerConfigProvider.keyAttesterTrustFor(credentialConfigId, supportedProofType)
        val policy = proofTypeSupported?.keyAttestationsRequired

        val validated =
            keyAttestationVerifier
                .verify(
                    keyAttestationJwt = attestationJwt,
                    trustConfig = trustConfig,
                    policy = policy,
                ).getOrElse { return Err(it) }

        val firstAttestedKey =
            validated.attestedKeys.firstOrNull()
                ?: return Err(
                    IdkError.fromString(
                        code = Oid4vciErrors.INVALID_PROOF,
                        message = "Invalid attestation proof: attested_keys is empty",
                    ),
                )
        val holderBindingKey =
            (firstAttestedKey.toJsonObject() as? JsonObject)
                ?: return Err(
                    IdkError.fromString(
                        code = Oid4vciErrors.INVALID_PROOF,
                        message = "Invalid attestation proof: attested key did not serialize to a JWK object",
                    ),
                )

        return Ok(
            VerifiedProof(
                holderBindingKey = holderBindingKey,
                holderIdentifier = validated.claims["iss"]?.jsonPrimitive?.content,
                keyId = firstAttestedKey.kid,
                algorithm = firstAttestedKey.alg?.value,
            ),
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? = if (isString) content else content.takeIf { it.isNotEmpty() && it != "null" }
}
