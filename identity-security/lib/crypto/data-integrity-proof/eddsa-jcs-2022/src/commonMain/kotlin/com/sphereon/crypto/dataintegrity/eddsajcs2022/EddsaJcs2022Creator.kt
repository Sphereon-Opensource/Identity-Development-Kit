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

package com.sphereon.crypto.dataintegrity.eddsajcs2022

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

/**
 * W3C VC-DI 1.0 + VC-DI-EDDSA `eddsa-jcs-2022` cryptosuite "Add Proof"
 * implementation.
 *
 * Spec: https://www.w3.org/TR/vc-di-eddsa/#eddsa-jcs-2022
 *
 * Pipeline (per spec §3.3.1 / §3.3.2):
 * 1. Build the proof config object from [options], with `proofValue` empty.
 * 2. Canonicalize the proof config and the unsecured document with JCS.
 * 3. `hashData = SHA-256(canonicalProofConfig) || SHA-256(canonicalDocument)`.
 * 4. Sign `hashData` with Ed25519 via the injected [KeyManagerService].
 * 5. Encode the signature as multibase base58btc (`z…`) and return as
 *    `proof.proofValue`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteCreator>())
class EddsaJcs2022Creator(
    private val keyManagerService: KeyManagerService,
) : DataIntegrityCryptosuiteCreator {
    override val cryptosuiteId: String = EddsaJcs2022Cryptosuite.ID

    override suspend fun createProof(
        unsecuredDocument: JsonObject,
        options: ProofOptions,
    ): IdkResult<DataIntegrityProof, IdkError> {
        if (options.cryptosuite != EddsaJcs2022Cryptosuite.ID) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "EddsaJcs2022Creator received options for cryptosuite '${options.cryptosuite}'",
                ),
            )
        }

        val managedKey = try {
            keyManagerService.getKeyResult(
                KeyInfo<KeyType>(
                    alias = options.signingKeyRef,
                    keyVisibility = KeyVisibility.PUBLIC,
                    keyEncoding = KeyEncoding.JOSE,
                ),
            ).getOrElse { return Err(it) }.key
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Ed25519 signing key", message = "Signing key '${options.signingKeyRef}' was not resolved"))
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EddsaJcs2022Creator: signing key resolution failed: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        val jwk = managedKey.key as? JwkType
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EddsaJcs2022Creator requires a JOSE Ed25519 signing key"))
        try {
            EddsaJcs2022Cryptosuite.requireEd25519Key(jwk)
        } catch (expected: IllegalArgumentException) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = expected.message ?: "Invalid Ed25519 signing key"))
        }

        val proofWithoutValue = options.toEddsaJcs2022ProofConfig()

        val hashData = EddsaJcs2022Cryptosuite.hashData(unsecuredDocument, proofWithoutValue)

        val keyInfo = KeyInfo<KeyType>(alias = options.signingKeyRef, signatureAlgorithm = SignatureAlgorithm.ED25519)
        val signature =
            try {
                keyManagerService.createRawSignature(keyInfo, hashData, requireX5Chain = false)
            } catch (expected: Exception) {
                return Err(
                    IdkError.fromString(
                        message = "EddsaJcs2022Creator: signing failed for key '${options.signingKeyRef}': ${expected.message}",
                        code = "PROOF_GENERATION_ERROR",
                        exception = expected,
                    ),
                )
            }

        return Ok(proofWithoutValue.copy(proofValue = EddsaJcs2022Cryptosuite.encodeProofValue(signature)))
    }
}

/**
 * Builds the proof configuration that is canonicalized and signed by
 * `eddsa-jcs-2022`. Extensions remain top-level proof properties and are
 * validated by [DataIntegrityProof] before they can enter the hash input.
 */
internal fun ProofOptions.toEddsaJcs2022ProofConfig(): DataIntegrityProof =
    DataIntegrityProof(
        type = DataIntegrityProof.TYPE_DATA_INTEGRITY,
        cryptosuite = EddsaJcs2022Cryptosuite.ID,
        proofPurpose = proofPurpose,
        verificationMethod = verificationMethod,
        proofValue = "",
        id = proofId,
        created = created,
        expires = expires,
        domain = domain,
        domainSet = domainSet,
        challenge = challenge,
        nonce = nonce,
        previousProof = previousProof,
        additionalProofProperties = additionalProofProperties ?: JsonObject(emptyMap()),
    )
