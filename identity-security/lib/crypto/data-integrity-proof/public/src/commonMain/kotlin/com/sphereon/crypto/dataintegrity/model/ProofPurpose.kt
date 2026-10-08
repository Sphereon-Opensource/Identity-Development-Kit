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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * W3C Verifiable Credentials Data Integrity 1.0 §2.1 proof purpose.
 *
 * Indicates the relationship between the verification method used to create
 * the proof and the action the proof is meant to support.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataIntegrityProofPurpose", exact = true)
@JsExportCompat
@Serializable
enum class ProofPurpose(
    val value: String,
) {
    @SerialName("assertionMethod")
    ASSERTION_METHOD("assertionMethod"),

    @SerialName("authentication")
    AUTHENTICATION("authentication"),

    @SerialName("keyAgreement")
    KEY_AGREEMENT("keyAgreement"),

    @SerialName("capabilityInvocation")
    CAPABILITY_INVOCATION("capabilityInvocation"),

    @SerialName("capabilityDelegation")
    CAPABILITY_DELEGATION("capabilityDelegation"),
    ;

    companion object {
        @JvmStatic
        @JsStatic
        fun fromValue(value: String): ProofPurpose? = entries.find { it.value == value }
    }
}
