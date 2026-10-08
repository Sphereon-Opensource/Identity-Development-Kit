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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.crypto.core.generic.SignatureAlgorithm

/**
 * Map an IDK [SignatureAlgorithm] to the canonical JWS `alg` header value (RFC 7518).
 *
 * Used by `BuildServerMetadataCommandImpl` to derive `id_token_signing_alg_values_supported`
 * from the resolved KMS signing key, and by `CreateIdTokenCommandImpl` to pick the matching
 * digest for `at_hash` / `c_hash`. Keeping the mapping local to discovery (rather than relying
 * on `SignatureAlgorithm.jose?.value`) lets us reject COSE-only or non-OIDC algs explicitly
 * instead of silently returning `null` and degrading metadata quality.
 *
 * Throws on unsupported algorithms — the caller (metadata builder) is expected to handle the
 * fallback to a defensible default rather than emit a wrong/missing alg in discovery.
 */
internal fun keyAlgorithmToJwsAlg(alg: SignatureAlgorithm): String =
    when (alg) {
        SignatureAlgorithm.RSA_SHA256 -> "RS256"
        SignatureAlgorithm.RSA_SHA384 -> "RS384"
        SignatureAlgorithm.RSA_SHA512 -> "RS512"
        SignatureAlgorithm.ECDSA_SHA256 -> "ES256"
        SignatureAlgorithm.ECDSA_SHA384 -> "ES384"
        SignatureAlgorithm.ECDSA_SHA512 -> "ES512"
        SignatureAlgorithm.ED25519 -> "EdDSA"
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> "PS256"
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> "PS384"
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> "PS512"
        else -> error("Unsupported signature algorithm for JWS: $alg")
    }
