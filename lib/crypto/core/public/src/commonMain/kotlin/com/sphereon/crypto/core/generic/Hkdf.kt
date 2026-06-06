/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.generic

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF

/**
 * Derive a key using HKDF (HMAC-based Key Derivation Function, RFC 5869), a thin wrapper over the
 * whyoleg cryptography library. Deterministic: identical inputs always produce identical output.
 *
 * @param inputKeyMaterial High-entropy input keying material (e.g. a session DEK, a shared secret)
 * @param salt Optional salt; an empty array is treated by HKDF as a zero-filled salt of hash length
 * @param info Context-binding info that separates derivations sharing the same input material
 * @param outputLength Desired length of the derived key in bytes
 * @param algorithm The hash backing the HMAC; defaults to SHA-256
 * @return The derived key, [outputLength] bytes long
 */
suspend fun deriveHkdf(
    inputKeyMaterial: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    outputLength: Int,
    algorithm: DigestAlg = DigestAlg.SHA256,
): ByteArray {
    val provider = CryptographyProvider.Default
    val hkdf = provider.get(HKDF)
    val derivation =
        hkdf.secretDerivation(
            digest = algorithm.toCryptoGraphicAlgorithm(),
            outputSize = outputLength.bytes,
            salt = salt,
            info = info,
        )
    return derivation.deriveSecretToByteArray(inputKeyMaterial)
}
