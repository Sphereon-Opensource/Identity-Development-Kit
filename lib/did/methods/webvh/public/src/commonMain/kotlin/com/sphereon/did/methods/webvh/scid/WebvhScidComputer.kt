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

package com.sphereon.did.methods.webvh.scid

import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.MultihashAlgorithm
import com.sphereon.crypto.core.generic.MultihashCodec
import com.sphereon.crypto.core.generic.hash
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Computes a `did:webvh` v1.0 §3.1 Self-Certifying IDentifier (SCID).
 *
 * Spec algorithm:
 * 1. Caller builds a preliminary entry with the placeholder string `{SCID}`
 *    in every place the SCID will appear (`versionId`, `parameters.scid`,
 *    `state.id`, embedded DID URLs).
 * 2. JCS-canonicalize that placeholder entry to bytes.
 * 3. SHA-256 the canonical bytes; wrap in multihash header `0x12 0x20`.
 * 4. Multibase-encode the multihash with the base58btc prefix `z`. The
 *    result is exactly 46 characters for SHA-256.
 * 5. Caller substitutes the SCID back in for `{SCID}`.
 */
object WebvhScidComputer {
    const val PLACEHOLDER: String = "{SCID}"

    /**
     * Compute the SCID for a placeholder entry. The input MUST contain the
     * literal `{SCID}` placeholder string in every location the SCID will
     * appear after substitution.
     */
    fun computeScid(placeholderEntry: JsonObject): String {
        val canonical = Jcs.canonicalize(placeholderEntry as JsonElement)
        val digest = hash(canonical, DigestAlg.SHA256)
        val multihash = MultihashCodec.encode(digest, MultihashAlgorithm.SHA2_256)
        return Multibase.encode(multihash, MultibaseEncoding.BASE58BTC)
    }

    /**
     * Replace every occurrence of the `{SCID}` placeholder in `text` with
     * the supplied `scid`. Convenience for the recursive substitution
     * the spec calls for after `computeScid`.
     */
    fun substitute(
        text: String,
        scid: String
    ): String = text.replace(PLACEHOLDER, scid)
}
