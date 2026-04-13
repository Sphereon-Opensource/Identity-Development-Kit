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
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * User-defined configuration for a verification method.
 *
 * This class represents how a key from the KMS should be expressed as a verification
 * method in a DID Document. The user explicitly controls which verification purposes
 * (relationships) the key should be associated with.
 *
 * Note: Verification purposes are NOT automatically derived from JWK key_use.
 * The user must explicitly specify which purposes apply.
 *
 * @property kmsKeyAlias Reference to KMS key by alias
 * @property kmsProviderId KMS provider ID
 * @property verificationMethodId Fragment ID (e.g., "key-1") used in the DID Document
 * @property purposes List of verification purposes this key serves
 * @property type The verification method type to use (defaults to JsonWebKey2020)
 * @property controller Override controller if needed (defaults to DID subject)
 * @property publicKeyJwk The resolved public key JWK (populated by DSL processor from KMS)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationMethodConfig", exact = true)
@JsExportCompat
@Serializable
data class VerificationMethodConfig(
    val kmsKeyAlias: String,
    val kmsProviderId: String,
    val verificationMethodId: String,
    val purposes: List<VerificationPurpose>,
    val type: VerificationMethodType = VerificationMethodType.JSON_WEB_KEY_2020,
    val controller: String? = null,
    val publicKeyJwk: com.sphereon.crypto.core.jose.Jwk? = null,
) {
    init {
        require(verificationMethodId.isNotBlank()) {
            "verificationMethodId must not be blank"
        }
        require(purposes.isNotEmpty()) {
            "At least one verification purpose must be specified"
        }
    }

    /**
     * Creates the full verification method ID for a given DID.
     *
     * @param did The DID to create the full ID for
     * @return The full verification method ID (e.g., "did:example:123#key-1")
     */
    fun createFullId(did: String): String = "$did#$verificationMethodId"

    /**
     * Checks if this config includes the specified purpose.
     */
    fun hasPurpose(purpose: VerificationPurpose): Boolean = purposes.contains(purpose)

    /**
     * Checks if this key can be used for authentication.
     */
    fun canAuthenticate(): Boolean = hasPurpose(VerificationPurpose.AUTHENTICATION)

    /**
     * Checks if this key can be used for signing credentials (assertions).
     */
    fun canSign(): Boolean = hasPurpose(VerificationPurpose.ASSERTION_METHOD)

    /**
     * Checks if this key can be used for encryption (key agreement).
     */
    fun canEncrypt(): Boolean = hasPurpose(VerificationPurpose.KEY_AGREEMENT)
}
