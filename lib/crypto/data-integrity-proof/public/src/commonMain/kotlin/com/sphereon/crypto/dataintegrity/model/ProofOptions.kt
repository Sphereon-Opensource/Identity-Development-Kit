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

package com.sphereon.crypto.dataintegrity.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Inputs to the W3C VC-DI 1.0 "Add Proof" algorithm.
 *
 * @property cryptosuite The id of a cryptosuite registered in `CryptosuiteRegistry`.
 * @property verificationMethod The DID URL or other URI that resolves to the
 *   public key counterpart of [signingKeyRef].
 * @property proofPurpose The relationship between the verification method and
 *   the action being proven.
 * @property signingKeyRef Reference to a key in the IDK `KeyStoreService`.
 *   Can be an alias, a kid, or any other provider-defined identifier.
 * @property created ISO-8601 / xsd:dateTime; defaults to the current time
 *   when null at the algorithm layer.
 * @property previousProof IDs of prior proofs in a proof chain. Single
 *   element produces a string on the wire; multi-element produces an array.
 * @property additionalProofProperties Extra fields that should be included
 *   in the proof object (and therefore covered by the signature). The
 *   cryptosuite is responsible for honouring or rejecting unknown fields.
 */
@Serializable
data class ProofOptions(
    val cryptosuite: String,
    val verificationMethod: String,
    val proofPurpose: ProofPurpose,
    val signingKeyRef: String,
    val created: String? = null,
    val expires: String? = null,
    val domain: String? = null,
    val challenge: String? = null,
    val nonce: String? = null,
    val previousProof: List<String>? = null,
    val proofId: String? = null,
    val additionalProofProperties: JsonObject? = null,
)
