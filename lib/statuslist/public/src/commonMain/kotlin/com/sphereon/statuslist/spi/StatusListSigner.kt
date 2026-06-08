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

package com.sphereon.statuslist.spi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose

/**
 * Builds and signs the hostable status-list token (envelope construction + JWS/COSE signing) from
 * an already-encoded bit list. Lives once in IDK so every [StatusListDriver] — the in-memory one
 * and the EDK Postgres/MySQL drivers — produces identical, correctly-signed tokens without
 * duplicating crypto logic.
 */
interface StatusListSigner {
    suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError>
}

/**
 * Everything needed to assemble + sign a status-list token. [encodedList] is the spec-encoded bit
 * string (zlib+base64url for Token Status List, gzip+base64url multibase for W3C), produced by the
 * driver from the current bit state.
 */
data class SignStatusListTokenArgs(
    val spec: StatusListSpec,
    val proofFormat: StatusProofFormat,
    val issuer: String,
    /** Public URI of this list; becomes the token `sub` / credential `id`. */
    val statusListUri: String,
    val signingKeyAlias: String,
    /**
     * Signing-key reference mode for the JOSE header — `did:<method>`, `x5c`, `jwk-thumbprint`, or
     * null. Set to match the credentials that reference this list so wallets trust the same key/anchor.
     */
    val signingKeyMode: String? = null,
    /**
     * For DID signing modes, the verification-method URL used as the JOSE `kid`. did:web/did:webvh
     * are NOT derivable from the key, so this is how the kid is configured. A full DID URL
     * (`did:web:host#frag`) is used verbatim; if omitted, web/webvh default to the host (from
     * [statusListUri]) plus the [signingKeyAlias] as the fragment. did:jwk/did:key derive it.
     */
    val signingVerificationMethodId: String? = null,
    /** Optional PEM cert-chain path for `x5c` mode when the KMS key has no embedded chain. */
    val signingCertChainPath: String? = null,
    val bitsPerStatus: Int,
    val length: Int,
    val purposes: List<StatusPurpose>,
    val encodedList: String,
    val issuedAtEpochSeconds: Long,
    val ttlSeconds: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
)
