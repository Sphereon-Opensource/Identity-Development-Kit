/*
 * Copyright (c) 2025 Sphereon International B.V.
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

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC

/**
 * Compute an HMAC digest using the whyoleg cryptography library (multiplatform).
 *
 * @param key The symmetric HMAC key bytes
 * @param message The data to compute the MAC over
 * @param algorithm The hash algorithm to use
 * @return The raw HMAC digest bytes
 */
suspend fun computeHmac(key: ByteArray, message: ByteArray, algorithm: DigestAlg): ByteArray {
    val provider = CryptographyProvider.Default
    val hmac = provider.get(HMAC)
    val digestId = algorithm.toCryptoGraphicAlgorithm()
    val hmacKey = hmac.keyDecoder(digestId).decodeFromByteArray(HMAC.Key.Format.RAW, key)
    return hmacKey.signatureGenerator().generateSignature(message)
}

/**
 * Generate cryptographically secure random bytes using the whyoleg cryptography library.
 *
 * @param size Number of random bytes to generate
 * @return Random byte array of the specified size
 */
suspend fun generateHmacKey(algorithm: DigestAlg): ByteArray {
    val provider = CryptographyProvider.Default
    val hmac = provider.get(HMAC)
    val digestId = algorithm.toCryptoGraphicAlgorithm()
    val key = hmac.keyGenerator(digestId).generateKey()
    return key.encodeToByteArray(HMAC.Key.Format.RAW)
}
