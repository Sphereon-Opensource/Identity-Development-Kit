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

package com.sphereon.openid.oid4vc.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Proof type for credential issuance per OID4VCI.
 *
 * OID4VCI Section 7.2.1: "proof_type" identifies the key proof mechanism.
 * Only JWT is implemented now; CWT and attestation will be added when needed.
 */
@Serializable
enum class ProofType(
    val value: String,
) {
    @SerialName("jwt")
    JWT("jwt"),
    ;

    companion object {
        fun fromValue(value: String): ProofType? = entries.find { it.value == value }
    }
}
