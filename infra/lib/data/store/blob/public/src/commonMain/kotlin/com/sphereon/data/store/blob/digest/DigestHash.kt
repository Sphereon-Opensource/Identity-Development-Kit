/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.digest

import org.kotlincrypto.core.digest.Digest
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512

/**
 * Returns a mutable digest instance for incremental hashing.
 */
fun getDigest(digestAlgorithm: DigestAlg = DigestAlg.SHA256): Digest =
    when (digestAlgorithm) {
        DigestAlg.SHA256 -> SHA256()
        DigestAlg.SHA384 -> SHA384()
        DigestAlg.SHA512 -> SHA512()
        else -> throw IllegalArgumentException("digestAlgorithm $digestAlgorithm is not yet supported")
    }

/**
 * Hashes [dataInput] with [digestAlgorithm].
 */
fun hash(
    dataInput: ByteArray,
    digestAlgorithm: DigestAlg = DigestAlg.SHA256,
): ByteArray {
    val digest = getDigest(digestAlgorithm)
    digest.update(dataInput)
    return digest.digest()
}
