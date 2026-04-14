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

package com.sphereon.crypto.core.sign
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType

/**
 * Service interface for creating and verifying raw digital signatures.
 */
@JsExportCompat
interface SimpleSignatureService {
    /**
     * Creates a raw signature using the specified key info and input data.
     *
     * @param keyInfo The key info used for signing.
     * @param input The data to be signed.
     * @param requireX5Chain Whether the X5 chain should be included in the signature.
     * @return The generated signature as a byte array.
     * @throws IllegalArgumentException If the private key is not provided or not supported.
     */
    suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray

    /**
     * Verifies a raw signature against the provided input using the specified key info.
     *
     * @param keyInfo Key information that includes the public key and other details.
     * @param input The original data which the signature is supposed to represent.
     * @param signature The signature that needs to be verified.
     * @return true if the signature is valid, false otherwise.
     * @throws IllegalArgumentException if a private key is used to verify the signature.
     */
    suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean
}
