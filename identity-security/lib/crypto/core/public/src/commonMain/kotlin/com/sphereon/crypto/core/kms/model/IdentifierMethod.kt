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

package com.sphereon.crypto.core.kms.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * IdentifierMethod represents the various methods used for identifying cryptographic keys.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IdentifierMethod", exact = true)
@JsExportCompat
@Serializable
enum class IdentifierMethod {
    /**
     * The JWK (JSON Web Key) class represents a cryptographic key used for signing, encrypting,
     * and validating tokens standardized in the JSON Web Key (JWK) specification.
     */
    @SerialName("JWK")
    jwk,

    /**
     * The Kid class represents a young individual with basic attributes and behaviors.
     *
     */
    @SerialName("KID")
    kid,

    /**
     * The `cose_key` class represents a COSE (CBOR Object Signing and Encryption) key object.
     *
     * COSE keys are used in various cryptographic operations including signing, encryption,
     * key agreement, and message authentication codes. This class provides methods to
     * initialize, manage, and utilize COSE keys in compliance with the COSE specification.
     *
     */
    @SerialName("COSE_KEY")
    cose_key,

    /**
     * The x5c class represents a concept or entity related to the larger system or application.
     */
    @SerialName("X5C")
    x5c,

    // TODO missing DID? it's in the rest api spec
}
