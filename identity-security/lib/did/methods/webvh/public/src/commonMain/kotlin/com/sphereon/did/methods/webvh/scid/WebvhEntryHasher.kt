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
import kotlinx.serialization.json.JsonObject

/**
 * Computes the per-entry hash that goes into a `did:webvh` versionId.
 *
 * Spec §3.1 / §3.3:
 *
 *   `versionId = "<n>-<entryHash>"`
 *   `entryHash = base58btc( multihash( JCS(entryWithPredecessorVersionId), SHA-256 ) )`
 *
 * Predecessor rule:
 * - For entry 1, `entry.versionId` is set to the literal SCID value.
 * - For entry n > 1, `entry.versionId` is set to the previous entry's
 *   final versionId string.
 *
 * The `proof` field is removed from the entry before hashing. The final
 * versionId (with the new entry hash) is then written back into the entry,
 * and the Data Integrity proof is computed over the entry that carries that
 * final versionId.
 */
object WebvhEntryHasher {
    /**
     * Compute the entry hash for an entry whose `versionId` field has been
     * set to the predecessor versionId (the SCID for entry 1, or the prior
     * entry's versionId for entry n>1) and whose `proof` field has been
     * removed.
     */
    fun computeEntryHash(entryWithoutProof: JsonObject): String {
        val canonical = Jcs.canonicalize(entryWithoutProof)
        val digest = hash(canonical, DigestAlg.SHA256)
        val multihash = MultihashCodec.encode(digest, MultihashAlgorithm.SHA2_256)
        return Multibase.encode(multihash, MultibaseEncoding.BASE58BTC)
    }
}
