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
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
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
    private val nonceManager: NonceManager,
) : ProofVerifier {
    override val supportedProofType: String = "attestation"

    override suspend fun verify(
        proofValue: JsonElement,
        expectedAudience: String,
        expectedClientId: String?,
        credentialConfigId: String,
        proofTypeSupported: ProofTypeSupported?,
        expectedNonce: String?,
        consumeNonce: Boolean,
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
                    expectedAudience = expectedAudience.takeIf { policy != null },
                ).getOrElse { return Err(it) }

        val attestationNonce =
            validated.claims["c_nonce"]?.jsonPrimitive?.contentOrNull()
                ?: validated.claims["nonce"]?.jsonPrimitive?.contentOrNull()
        if (policy != null && attestationNonce == null) {
            return Err(
                IdkError.fromString(
                    code = Oid4vciErrors.INVALID_PROOF,
                    message = "Invalid attestation proof: production key attestation must carry c_nonce",
                ),
            )
        }
        if (expectedNonce != null && attestationNonce != expectedNonce) {
            return Err(
                IdkError.fromString(
                    code = "invalid_nonce",
                    message = "Invalid attestation proof: c_nonce does not match the credential request nonce",
                ),
            )
        }
        if (consumeNonce && attestationNonce != null) {
            val nonceEntry = nonceManager.consume(attestationNonce).getOrElse { return Err(it) }
            if (nonceEntry == null) {
                return Err(
                    IdkError.fromString(
                        code = "invalid_nonce",
                        message = "Invalid attestation proof: c_nonce is invalid or expired",
                    ),
                )
            }
        }

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
                keyAttestation = validated.keyAttestation,
            ),
        )
    }

    override fun extractNonce(proofValue: JsonElement): IdkResult<String?, IdkError> {
        val attestationJwt =
            (proofValue as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull()
                ?: return Err(
                    IdkError.fromString(
                        code = Oid4vciErrors.INVALID_PROOF,
                        message = "Invalid attestation proof: proof value must be a key-attestation JWT string",
                    ),
                )
        val claims = parseJwtClaims(attestationJwt).getOrElse { return Err(it) }
        return Ok(
            claims["c_nonce"]?.jsonPrimitive?.contentOrNull()
                ?: claims["nonce"]?.jsonPrimitive?.contentOrNull(),
        )
    }

    private fun parseJwtClaims(jwt: String): IdkResult<JsonObject, IdkError> {
        val payloadPart =
            jwt.split('.').getOrNull(1)
                ?: return Err(IdkError.fromString(code = Oid4vciErrors.INVALID_PROOF, message = "Invalid attestation proof: compact JWT payload is missing"))
        return try {
            val payload = payloadPart.decodeFromBase64Url().decodeToString()
            val parsed = Json.parseToJsonElement(payload)
            Ok(
                parsed as? JsonObject
                    ?: return Err(IdkError.fromString(code = Oid4vciErrors.INVALID_PROOF, message = "Invalid attestation proof: payload must be a JSON object")),
            )
        } catch (t: Throwable) {
            Err(IdkError.fromString(code = Oid4vciErrors.INVALID_PROOF, message = "Invalid attestation proof: failed to parse payload: ${t.message}"))
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? = if (isString) content else content.takeIf { it.isNotEmpty() && it != "null" }
}
