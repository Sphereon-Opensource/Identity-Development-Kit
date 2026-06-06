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
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.did.serializers.VerificationMethodWithExtensionsSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.time.Instant

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
 * @property publicKeyJwk The public key in JWK format. Mutually exclusive on the wire with
 *           [publicKeyMultibase] / [blockchainAccountId]; may be null for EXTERNAL VMs whose
 *           key material is resolved out-of-band via the persistence layer's key-reference.
 * @property publicKeyMultibase The public key in multibase format. See [publicKeyJwk] note.
 * @property blockchainAccountId CAIP-10 blockchain account identifier for blockchain-backed
 *           verification methods (e.g. `eip155:1:0x…`). DID 1.1.
 * @property expiresAt Lifecycle metadata — when this VM ceases to be usable. DID 1.1.
 * @property revokedAt Lifecycle metadata — when this VM was revoked. DID 1.1.
 * @property extensions Unknown JSON properties on this VM, captured verbatim for lossless
 *           round-trip. Must not hold keys defined by the W3C DID Core schema.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationMethod", exact = true)
@JsExportCompat
@Serializable(with = VerificationMethodWithExtensionsSerializer::class)
data class VerificationMethod
    @JvmOverloads
    constructor(
        val id: String,
        val type: String,
        val controller: String,
        val publicKeyJwk: Jwk? = null,
        val publicKeyMultibase: String? = null,
        val blockchainAccountId: String? = null,
        val expiresAt: Instant? = null,
        val revokedAt: Instant? = null,
        val extensions: Map<String, JsonElement> = emptyMap(),
    ) {
        init {
            // DID-Core 1.0 documents may carry legacy key encodings (publicKeyBase58,
            // publicKeyHex, publicKeyPem) that round-trip through [extensions] rather than
            // dedicated fields. EXTERNAL VMs may also defer key material resolution to the
            // persistence layer's key-reference. The invariant is therefore "carry some key
            // material via canonical fields, a recognised legacy extension, or none at all
            // (deferred resolution)" — i.e. construction must never fail simply because the
            // legacy publicKey* form is in extensions.
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
