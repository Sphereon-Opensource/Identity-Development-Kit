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
 * Well-known cryptographic binding methods per OID4VCI.
 *
 * OID4VCI Section 11.2.3 defines cryptographic_binding_methods_supported as a list of
 * strings. This enum covers the fixed well-known values; DID-based methods are represented
 * as raw strings of the form "did:<method>" and handled via the companion helpers.
 */
@Serializable
enum class CryptographicBindingMethod(
    val value: String,
) {
    @SerialName("jwk")
    JWK("jwk"),

    @SerialName("cose_key")
    COSE_KEY("cose_key"),
    ;

    companion object {
        /** Constructs the raw DID binding method string for the given DID method (e.g. "did:key"). */
        fun did(method: String): String = "did:$method"

        /** Returns the matching well-known enum entry, or null for DID strings or unknown values. */
        fun fromValue(value: String): CryptographicBindingMethod? = entries.find { it.value == value }

        /** Returns true when the raw binding method value represents a DID-based method. */
        fun isDid(value: String): Boolean = value.startsWith("did:")
    }
}
