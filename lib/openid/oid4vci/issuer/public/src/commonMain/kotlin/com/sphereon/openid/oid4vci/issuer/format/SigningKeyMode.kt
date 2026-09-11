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

package com.sphereon.openid.oid4vci.issuer.format

import com.sphereon.core.compat.JsExportCompat

/**
 * Key reference mode for the signing key identifier in issued credentials.
 *
 * Determines how the issuer's signing key is referenced in the JWT protected header,
 * enabling verifiers to discover and resolve the public key for signature verification.
 */
@JsExportCompat
sealed class SigningKeyMode {
    /**
     * DID-based kid: creates a DID using the specified [method], extracts the
     * `assertionMethod` verification method ID as the `kid` JWT header value.
     *
     * Supports any DID method registered in the `DidProviderRegistry` (e.g., "jwk", "key", "web").
     */
    data class Did(
        val method: String,
    ) : SigningKeyMode()

    /**
     * X.509 certificate chain: includes the certificate chain in the `x5c` JWT header.
     * No `kid` is set. The chain is sourced from the KMS key's `x5c` field,
     * falling back to resolved configured x5c material.
     */
    data object X5c : SigningKeyMode()

    /**
     * JWK thumbprint: sets `kid` to a URN per RFC 9278:
     * `urn:ietf:params:oauth:jwk-thumbprint:sha-256:<base64url-hash>`.
     *
     * Requires the issuer to expose the public key via `/.well-known/jwt-vc-issuer` JWKS.
     */
    data object JwkThumbprint : SigningKeyMode()

    /**
     * OpenID Federation: entity statement trust chain.
     * Not yet implemented — throws [UnsupportedOperationException] if used.
     */
    data object Federation : SigningKeyMode()

    /**
     * No identifier in the JWT header. The credential can still be verified
     * via `/.well-known/jwt-vc-issuer` JWKS if the issuer exposes that endpoint.
     */
    data object None : SigningKeyMode()

    companion object {
        /**
         * Parses a configuration string into a [SigningKeyMode].
         *
         * @param value Config value: `"did:jwk"`, `"did:key"`, `"x5c"`, `"jwk-thumbprint"`,
         *              `"federation"`, or `null` (maps to [None]).
         * @throws IllegalArgumentException if the value is not recognized.
         */
        fun fromConfig(value: String?): SigningKeyMode =
            when {
                value == null -> None

                value.startsWith("did:") -> Did(value.removePrefix("did:"))

                value.equals("x5c", ignoreCase = true) -> X5c

                value.equals("jwk-thumbprint", ignoreCase = true) -> JwkThumbprint

                value.equals("federation", ignoreCase = true) -> Federation

                else -> throw IllegalArgumentException(
                    "Unknown signingKeyMode: $value. Expected: did:<method>, x5c, jwk-thumbprint, federation, or null",
                )
            }
    }
}
