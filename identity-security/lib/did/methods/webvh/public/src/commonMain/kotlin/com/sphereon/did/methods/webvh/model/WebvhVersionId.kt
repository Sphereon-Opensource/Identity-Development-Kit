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

package com.sphereon.did.methods.webvh.model

import kotlin.jvm.JvmInline

/**
 * `did:webvh` v1.0 versionId of the form `<n>-<entryHash>` where `n` is the
 * 1-based version number and `entryHash` is the multihash-base58btc of the
 * canonical log entry.
 *
 * For entry 1 the entry hash input uses the SCID as the predecessor versionId
 * placeholder; for entry n>1 the predecessor is the previous entry's
 * versionId. See `WebvhEntryHasher`.
 */
@JvmInline
value class WebvhVersionId(
    val raw: String
) {
    init {
        require(raw.contains('-')) { "versionId must be of form '<n>-<entryHash>': $raw" }
    }

    val versionNumber: Int
        get() = raw.substringBefore('-').toInt()

    val entryHash: String
        get() = raw.substringAfter('-')

    override fun toString(): String = raw

    companion object {
        fun of(
            versionNumber: Int,
            entryHash: String
        ): WebvhVersionId = WebvhVersionId("$versionNumber-$entryHash")

        fun tryParse(raw: String): WebvhVersionId? =
            try {
                WebvhVersionId(raw)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
