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

package com.sphereon.oauth2.common.validation

import com.sphereon.crypto.core.generic.DigestAlg

/**
 * Maps a JWS `alg` header value (RFC 7518) to the digest algorithm used for OIDC Core
 * §3.1.3.3 / §3.1.3.6 `at_hash` / `c_hash` / `s_hash` computations.
 *
 * OIDC Core §3.1.3.6 pins the digest to "the hash algorithm used in the alg Header Parameter of
 * the ID Token's JOSE Header". The family prefix is ignored (RS/ES/PS/HS pick the same digest
 * per numeric suffix); only the suffix (256/384/512) selects the digest.
 *
 * Returns `null` for unrecognised `alg` values so callers can surface `invalid_request` /
 * `server_error` without throwing.
 */
public fun jwsAlgToDigest(alg: String): DigestAlg? =
    when (alg.uppercase()) {
        "RS256", "ES256", "PS256", "HS256" -> DigestAlg.SHA256
        "RS384", "ES384", "PS384", "HS384" -> DigestAlg.SHA384
        "RS512", "ES512", "PS512", "HS512" -> DigestAlg.SHA512
        else -> null
    }

/**
 * JWS `alg` values IDK's built-in `JwtService` can honour for ID-token signing. The startup
 * consistency validator rejects advertised metadata that references anything outside this set.
 * Extending this requires first ensuring the signer handles the added alg family.
 */
public val SUPPORTED_ID_TOKEN_SIGNING_ALGS: Set<String> =
    setOf(
        "RS256",
        "RS384",
        "RS512",
        "ES256",
        "ES384",
        "ES512",
        "PS256",
        "PS384",
        "PS512",
        "HS256",
        "HS384",
        "HS512",
    )
