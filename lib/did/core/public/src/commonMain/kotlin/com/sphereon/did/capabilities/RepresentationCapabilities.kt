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

package com.sphereon.did.capabilities

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.models.VerificationMethodType
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Supported verification method representations.
 *
 * Describes which verification method types (public key formats) are supported
 * by a DID method.
 *
 * @property jsonWebKey2020 Whether JsonWebKey2020 format is supported
 * @property multikey Whether Multikey format is supported
 * @property ed25519VerificationKey2020 Whether Ed25519VerificationKey2020 format is supported
 * @property ed25519VerificationKey2018 Whether Ed25519VerificationKey2018 (legacy) format is supported
 * @property ecdsaSecp256k1VerificationKey2019 Whether EcdsaSecp256k1VerificationKey2019 format is supported
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidRepresentationCapabilities", exact = true)
@JsExportCompat
@Serializable
data class RepresentationCapabilities(
    val jsonWebKey2020: Boolean = true,
    val multikey: Boolean = false,
    val ed25519VerificationKey2020: Boolean = false,
    val ed25519VerificationKey2018: Boolean = false,
    val ecdsaSecp256k1VerificationKey2019: Boolean = false,
) {
    companion object {
        /**
         * Only JsonWebKey2020 support.
         */
        val JWK_ONLY: RepresentationCapabilities =
            RepresentationCapabilities(
                jsonWebKey2020 = true,
            )

        /**
         * JsonWebKey2020 and Multikey support.
         */
        val JWK_AND_MULTIKEY: RepresentationCapabilities =
            RepresentationCapabilities(
                jsonWebKey2020 = true,
                multikey = true,
            )

        /**
         * All verification method formats supported.
         */
        val ALL: RepresentationCapabilities =
            RepresentationCapabilities(
                jsonWebKey2020 = true,
                multikey = true,
                ed25519VerificationKey2020 = true,
                ed25519VerificationKey2018 = true,
                ecdsaSecp256k1VerificationKey2019 = true,
            )
    }

    /**
     * Gets a list of supported verification method types.
     */
    fun getSupportedTypes(): List<VerificationMethodType> =
        buildList {
            if (jsonWebKey2020) {
                add(VerificationMethodType.JSON_WEB_KEY_2020)
            }
            if (multikey) {
                add(VerificationMethodType.MULTIKEY)
            }
            if (ed25519VerificationKey2020) {
                add(VerificationMethodType.ED25519_VERIFICATION_KEY_2020)
            }
            if (ed25519VerificationKey2018) {
                add(VerificationMethodType.ED25519_VERIFICATION_KEY_2018)
            }
            if (ecdsaSecp256k1VerificationKey2019) {
                add(VerificationMethodType.ECDSA_SECP256K1_VERIFICATION_KEY_2019)
            }
        }

    /**
     * Checks if a specific verification method type is supported.
     */
    fun supportsType(type: VerificationMethodType): Boolean =
        when (type) {
            VerificationMethodType.JSON_WEB_KEY_2020 -> jsonWebKey2020
            VerificationMethodType.MULTIKEY -> multikey
            VerificationMethodType.ED25519_VERIFICATION_KEY_2020 -> ed25519VerificationKey2020
            VerificationMethodType.ED25519_VERIFICATION_KEY_2018 -> ed25519VerificationKey2018
            VerificationMethodType.ECDSA_SECP256K1_VERIFICATION_KEY_2019 -> ecdsaSecp256k1VerificationKey2019
        }
}
