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

package com.sphereon.identity.matching.crypto

import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import kotlinx.serialization.Serializable

/**
 * Result of an authenticated encryption operation.
 *
 * Carries the ciphertext along with key version metadata for decryption
 * and key rotation support.
 *
 * @property ciphertext The encrypted data as a Base64url-encoded string (IV + authTag + ciphertext)
 * @property keyVersion The version identifier of the encryption key used
 * @property algorithm The content encryption algorithm used (default: A256GCM = AES-256-GCM)
 */
@Serializable
data class EncryptedPayload(
    val ciphertext: String,
    val keyVersion: String,
    val algorithm: ContentEncryptionAlgorithm = ContentEncryptionAlgorithm.A256GCM,
)
