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

package com.sphereon.openid.oid4vci.common.model

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.CryptographicBindingMethod
import com.sphereon.openid.oid4vc.common.ProofType

// Type-safe accessors for CredentialConfigurationSupported.
// The wire format stays stringly-typed; these are convenience for Kotlin callers.

/**
 * Returns the [CredentialFormat] enum value for this configuration's [format] string,
 * using lenient matching to handle both "dc+sd-jwt" and "vc+sd-jwt" variants.
 * Returns null if the format string is not recognised.
 */
val CredentialConfigurationSupported.credentialFormat: CredentialFormat?
    get() = CredentialFormat.fromValueLenient(format)

/**
 * Returns the credential signing algorithms as typed [JwaAlgorithm] values.
 *
 * Per OID4VCI 1.0 final §12.2.3 / §A.3.2 the wire entries are format-specific: JWA strings
 * (`"ES256"`) for JWS-based formats, numeric COSE algorithm identifiers (`-7`) for `mso_mdoc`.
 * Both shapes round-trip into [JwaAlgorithm] here — strings via the JWA name, integers via
 * the IANA COSE → JWA mapping. Unknown values (no match in either registry) are dropped.
 */
val CredentialConfigurationSupported.signingAlgorithms: List<JwaAlgorithm>
    get() =
        credentialSigningAlgValuesSupported?.mapNotNull { element ->
            val primitive = element as? kotlinx.serialization.json.JsonPrimitive ?: return@mapNotNull null
            if (primitive.isString) {
                JwaAlgorithm.fromValue(primitive.content)
            } else {
                primitive.content.toIntOrNull()?.let { coseId ->
                    when (coseId) {
                        -7 -> JwaAlgorithm.fromValue("ES256")
                        -8 -> JwaAlgorithm.fromValue("EdDSA")
                        -35 -> JwaAlgorithm.fromValue("ES384")
                        -36 -> JwaAlgorithm.fromValue("ES512")
                        -47 -> JwaAlgorithm.fromValue("ES256K")
                        else -> null
                    }
                }
            }
        } ?: emptyList()

/**
 * Returns the supported proof types as a typed map of [ProofType] to [ProofTypeSupported].
 * Unknown proof type keys are silently dropped.
 */
val CredentialConfigurationSupported.proofTypes: Map<ProofType, ProofTypeSupported>
    get() =
        proofTypesSupported
            ?.mapNotNull { (key, value) ->
                ProofType.fromValue(key)?.let { it to value }
            }?.toMap() ?: emptyMap()

/**
 * Returns the well-known [CryptographicBindingMethod] entries from
 * [cryptographicBindingMethodsSupported]. DID-based and unknown values are dropped;
 * use [cryptographicBindingMethodStrings] to access those.
 */
val CredentialConfigurationSupported.cryptographicBindingMethods: List<CryptographicBindingMethod>
    get() = cryptographicBindingMethodsSupported?.mapNotNull { CryptographicBindingMethod.fromValue(it) } ?: emptyList()

/**
 * Returns all raw binding method strings (including DID-based values such as "did:key").
 * Equivalent to [CredentialConfigurationSupported.cryptographicBindingMethodsSupported]
 * but returns an empty list instead of null.
 */
val CredentialConfigurationSupported.cryptographicBindingMethodStrings: List<String>
    get() = cryptographicBindingMethodsSupported ?: emptyList()

/**
 * Returns the proof signing algorithms for this [ProofTypeSupported] as typed [JwaAlgorithm] values.
 * Unknown algorithm strings are silently dropped.
 */
val ProofTypeSupported.signingAlgorithms: List<JwaAlgorithm>
    get() = proofSigningAlgValuesSupported.mapNotNull { JwaAlgorithm.fromValue(it) }
