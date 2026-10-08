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

package com.sphereon.openid.oid4vc.common.vcdm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

/** A VCDM version identifier. Unknown versions remain valid and lossless. */
@Serializable
@JvmInline
value class VcdmVersion(val value: String) {
    companion object {
        val V1_1 = VcdmVersion("1.1")
        val V2_0 = VcdmVersion("2.0")
    }
}

/** The VCDM document shape represented by [VcdmDocument]. */
@Serializable
enum class VcdmDocumentKind {
    CREDENTIAL,
    PRESENTATION,
}

/**
 * A semantic VCDM document.
 *
 * Keeping [json] as a JsonObject allows newer or application-specific terms to pass through
 * without being dropped by a closed Kotlin DTO. For a VCDM 1.1 JWT, [json] is the result of the
 * normative registered-claim decoding transform; [VcdmClassification.rawPayload] retains the
 * untouched JWT payload.
 */
@Serializable
data class VcdmDocument(
    val version: VcdmVersion,
    val kind: VcdmDocumentKind,
    val json: JsonObject,
)

/** The outcome of VCDM validation. A valid result has no errors, and vice versa. */
data class VcdmValidationResult(
    val valid: Boolean,
    val errors: List<VcdmError> = emptyList(),
) {
    init {
        require(valid == errors.isEmpty()) {
            "VcdmValidationResult.valid must be true exactly when errors is empty"
        }
    }
}
