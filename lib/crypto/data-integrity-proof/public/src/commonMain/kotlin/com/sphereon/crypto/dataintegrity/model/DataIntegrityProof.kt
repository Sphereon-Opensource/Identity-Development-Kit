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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * W3C Verifiable Credentials Data Integrity 1.0 proof object.
 *
 * Spec: https://www.w3.org/TR/vc-data-integrity/#proofs
 *
 * Field semantics:
 * - [type] is `DataIntegrityProof` for the suite-agnostic type, or a
 *   suite-specific type name for the legacy proof types (e.g.
 *   `Ed25519Signature2020`). For new development, use `DataIntegrityProof`
 *   together with [cryptosuite].
 * - [cryptosuite] identifies the cryptosuite registered in the
 *   `CryptosuiteRegistry`, e.g. `eddsa-jcs-2022`.
 * - [created] and [expires] are ISO-8601 / xsd:dateTime strings; per spec
 *   they are kept as strings to avoid precision loss across platforms.
 * - [proofValue] is a multibase-encoded signature (typically `z` +
 *   base58btc-encoded raw signature bytes).
 * - [previousProof] links a proof in a proof chain to one or more prior
 *   proofs by their `id`. Per W3C VC-DI 1.0 §2.1 it is a string OR an
 *   unordered list of strings on the wire; the model always exposes it as
 *   a `List<String>` and round-trips both wire forms via
 *   [PreviousProofSerializer].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataIntegrityProof", exact = true)
@JsExportCompat
@Serializable
data class DataIntegrityProof
    @JvmOverloads
    constructor(
        val type: String = TYPE_DATA_INTEGRITY,
        val cryptosuite: String,
        val proofPurpose: ProofPurpose,
        val verificationMethod: String,
        val proofValue: String,
        val id: String? = null,
        val created: String? = null,
        val expires: String? = null,
        val domain: String? = null,
        val challenge: String? = null,
        val nonce: String? = null,
        @Serializable(with = PreviousProofSerializer::class)
        val previousProof: List<String>? = null,
    ) {
        companion object {
            /** Suite-agnostic type per W3C VC-DI 1.0. */
            const val TYPE_DATA_INTEGRITY: String = "DataIntegrityProof"
        }
    }
