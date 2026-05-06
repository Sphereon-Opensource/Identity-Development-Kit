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

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.generic.MultibaseEncoding
import com.sphereon.crypto.core.generic.MultihashAlgorithm
import com.sphereon.crypto.core.generic.MultihashCodec
import com.sphereon.crypto.core.generic.hash

/**
 * Computes a `did:webvh` v1.0 §3.2.1 pre-rotation key hash:
 *
 *   `nextKeyHash = base58btc( multihash( utf8(multikey-string), SHA-256 ) )`
 *
 * The input is the **multikey string** itself (e.g. `z6Mk...`), encoded as
 * UTF-8 bytes, NOT the raw public key bytes. Verification recomputes this
 * value over the rotated key's multikey form and looks it up in the prior
 * entry's `nextKeyHashes`.
 */
object WebvhPreRotationHasher {
    fun hashMultikey(multikey: String): String {
        val digest = hash(multikey.encodeToByteArray(), DigestAlg.SHA256)
        val multihash = MultihashCodec.encode(digest, MultihashAlgorithm.SHA2_256)
        return Multibase.encode(multihash, MultibaseEncoding.BASE58BTC)
    }
}
