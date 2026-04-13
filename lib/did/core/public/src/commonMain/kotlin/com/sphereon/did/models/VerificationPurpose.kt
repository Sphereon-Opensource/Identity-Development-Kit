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

package com.sphereon.did.models

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

/**
 * Verification purposes for DID verification methods.
 * Enum for type safety - serializes to/from string for JSON compat.
 *
 * These purposes define how a verification method can be used within the DID document.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationPurpose", exact = true)
@JsExportCompat
@Serializable
enum class VerificationPurpose(
    val value: String,
) {
    /**
     * Used to authenticate as the DID subject.
     * Example: Logging into a website using DID authentication.
     */
    @SerialName("authentication")
    AUTHENTICATION("authentication"),

    /**
     * Used to express claims, such as signing Verifiable Credentials.
     * Example: An issuer signing a credential to assert claims about a subject.
     */
    @SerialName("assertionMethod")
    ASSERTION_METHOD("assertionMethod"),

    /**
     * Used for key agreement protocols (encryption).
     * Example: Establishing an encrypted communication channel.
     */
    @SerialName("keyAgreement")
    KEY_AGREEMENT("keyAgreement"),

    /**
     * Used to invoke cryptographic capabilities.
     * Example: Invoking a capability granted by another DID.
     */
    @SerialName("capabilityInvocation")
    CAPABILITY_INVOCATION("capabilityInvocation"),

    /**
     * Used to delegate cryptographic capabilities.
     * Example: Delegating the right to perform an action to another DID.
     */
    @SerialName("capabilityDelegation")
    CAPABILITY_DELEGATION("capabilityDelegation"),
    ;

    companion object {
        /**
         * All verification purposes.
         */
        @JsStatic
        val ALL: List<VerificationPurpose> = entries.toList()

        /**
         * Find a verification purpose by its string value.
         *
         * @param value The string value (e.g., "authentication", "assertionMethod")
         * @return The matching VerificationPurpose, or null if not found
         */
        @JsStatic
        fun fromValue(value: String): VerificationPurpose? = entries.find { it.value == value }
    }
}
