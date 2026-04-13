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
 * Verification method type enum.
 * Defines the cryptographic suite used for the verification method.
 *
 * Serializes to/from string for JSON compat.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationMethodType", exact = true)
@JsExportCompat
@Serializable
enum class VerificationMethodType(
    val value: String,
) {
    /**
     * JSON Web Key 2020 verification method.
     * Uses publicKeyJwk to express the public key.
     * Most widely supported format.
     */
    @SerialName("JsonWebKey2020")
    JSON_WEB_KEY_2020("JsonWebKey2020"),

    /**
     * Multikey verification method (W3C Data Integrity).
     * Uses publicKeyMultibase to express the public key.
     * Compact format using multicodec prefixes.
     */
    @SerialName("Multikey")
    MULTIKEY("Multikey"),

    /**
     * Ed25519 Verification Key 2020.
     * Uses publicKeyMultibase with Ed25519 keys.
     */
    @SerialName("Ed25519VerificationKey2020")
    ED25519_VERIFICATION_KEY_2020("Ed25519VerificationKey2020"),

    /**
     * Ed25519 Verification Key 2018.
     * Legacy format using publicKeyBase58.
     */
    @SerialName("Ed25519VerificationKey2018")
    ED25519_VERIFICATION_KEY_2018("Ed25519VerificationKey2018"),

    /**
     * ECDSA Secp256k1 Verification Key 2019.
     * Uses secp256k1 curve (Bitcoin/Ethereum compatible).
     */
    @SerialName("EcdsaSecp256k1VerificationKey2019")
    ECDSA_SECP256K1_VERIFICATION_KEY_2019("EcdsaSecp256k1VerificationKey2019"),
    ;

    companion object {
        /**
         * Find a verification method type by its string value.
         *
         * @param value The string value (e.g., "JsonWebKey2020")
         * @return The matching VerificationMethodType, or null if not found
         */
        @JsStatic
        fun fromValue(value: String): VerificationMethodType? = entries.find { it.value == value }
    }
}
