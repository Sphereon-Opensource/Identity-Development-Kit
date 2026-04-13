/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents a verification method in a DID Document.
 *
 * A verification method is a set of parameters that can be used to independently verify a proof.
 * For example, a cryptographic public key can be used as a verification method with respect to
 * a digital signature.
 *
 * @property id The verification method ID. This is typically a DID URL fragment (e.g., "did:example:123#key-1")
 * @property type The type of verification method (e.g., "JsonWebKey2020", "Multikey")
 * @property controller The DID of the controller of this verification method
 * @property publicKeyJwk The public key in JWK format (mutually exclusive with publicKeyMultibase)
 * @property publicKeyMultibase The public key in multibase format (mutually exclusive with publicKeyJwk)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationMethod", exact = true)
@JsExportCompat
@Serializable
data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyJwk: Jwk? = null,
    val publicKeyMultibase: String? = null
) {
    init {
        require(publicKeyJwk != null || publicKeyMultibase != null) {
            "VerificationMethod must have either publicKeyJwk or publicKeyMultibase"
        }
    }

    /**
     * Gets the key ID (fragment) from the verification method ID.
     * For example, if id is "did:example:123#key-1", this returns "key-1".
     *
     * @return The fragment portion of the ID, or the full ID if no fragment is present
     */
    fun getKeyId(): String {
        val fragmentIndex = id.indexOf('#')
        return if (fragmentIndex >= 0) {
            id.substring(fragmentIndex + 1)
        } else {
            id
        }
    }

    /**
     * Gets the DID portion from the verification method ID.
     * For example, if id is "did:example:123#key-1", this returns "did:example:123".
     *
     * @return The DID portion of the ID, or the full ID if no fragment is present
     */
    fun getDid(): String {
        val fragmentIndex = id.indexOf('#')
        return if (fragmentIndex >= 0) {
            id.substring(0, fragmentIndex)
        } else {
            id
        }
    }

    /**
     * Checks if this verification method uses a JWK representation.
     */
    fun isJwk(): Boolean = publicKeyJwk != null

    /**
     * Checks if this verification method uses a Multibase representation.
     */
    fun isMultibase(): Boolean = publicKeyMultibase != null

    /**
     * Gets the verification method type as an enum, if recognized.
     *
     * @return The VerificationMethodType enum value, or null if the type is not recognized
     */
    fun getTypeEnum(): VerificationMethodType? = VerificationMethodType.fromValue(type)
}
