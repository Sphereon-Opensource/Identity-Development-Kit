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
@Serializable(with = DataIntegrityProofSerializer::class)
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
        /**
         * Cryptosuite-defined proof properties not covered by the typed
         * Data Integrity model. These remain top-level JSON properties on the
         * wire and are included in proof configuration canonicalization.
         *
         * A key in this object may not shadow a typed property. Silently
         * dropping such a collision would make the model and signed input
         * disagree.
         */
        val additionalProofProperties: kotlinx.serialization.json.JsonObject =
            kotlinx.serialization.json.JsonObject(emptyMap()),
        /**
         * The unordered-set form of the Data Integrity `domain` property.
         * The list order is retained for wire round-tripping; callers must
         * not provide both [domain] and this property.
         */
        val domainSet: List<String>? = null,
    ) {
        init {
            require(domain == null || domainSet == null) {
                "DataIntegrityProof.domain and domainSet are mutually exclusive"
            }
            domainSet?.let { values ->
                require(values.isNotEmpty()) { "DataIntegrityProof.domainSet must not be empty" }
                require(values.all(String::isNotBlank)) { "DataIntegrityProof.domainSet values must not be blank" }
                require(values.distinct().size == values.size) { "DataIntegrityProof.domainSet values must be unique" }
            }
            val conflicts = additionalProofProperties.keys intersect TYPED_PROPERTY_NAMES
            require(conflicts.isEmpty()) {
                "DataIntegrityProof extension properties shadow typed properties: ${conflicts.sorted().joinToString()}"
            }
        }

        companion object {
            /** Suite-agnostic type per W3C VC-DI 1.0. */
            const val TYPE_DATA_INTEGRITY: String = "DataIntegrityProof"

            internal val TYPED_PROPERTY_NAMES: Set<String> =
                setOf(
                    "type",
                    "cryptosuite",
                    "proofPurpose",
                    "verificationMethod",
                    "proofValue",
                    "id",
                    "created",
                    "expires",
                    "domain",
                    "challenge",
                    "nonce",
                    "previousProof",
                )
        }
    }
