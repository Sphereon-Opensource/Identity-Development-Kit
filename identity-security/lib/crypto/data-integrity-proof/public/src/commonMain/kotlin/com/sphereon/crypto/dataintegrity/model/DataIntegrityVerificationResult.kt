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
 * Outcome of the W3C VC-DI 1.0 §4.4 "Verify Proof" orchestrator algorithm.
 *
 * Spec mandates the fields [verified], [verifiedDocument], [mediaType],
 * [warnings], and [errors]. [proofs] is an additional convenience field
 * holding the proof object(s) that were verified, in document order;
 * implementations MAY include other implementation-specific information
 * per §3.1.
 */
@Serializable
data class DataIntegrityVerificationResult(
    val verified: Boolean,
    val verifiedDocument: JsonObject? = null,
    val mediaType: String? = null,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val proofs: List<DataIntegrityProof> = emptyList(),
)
