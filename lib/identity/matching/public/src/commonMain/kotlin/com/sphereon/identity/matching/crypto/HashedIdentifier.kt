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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.JwaAlgorithm
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Result of an HMAC hashing operation on an identifier.
 *
 * Carries the hash value along with key version metadata for rotation support.
 *
 * @property hash The HMAC digest as a multibase-encoded multihash string
 * @property keyVersion The version identifier of the HMAC key used
 * @property algorithm The JWA algorithm used for hashing (default: HS256 = HMAC w/ SHA-256)
 */
@JsExportCompat
@Serializable
data class HashedIdentifier
    @JvmOverloads
    constructor(
        val hash: String,
        val keyVersion: String,
        val algorithm: JwaAlgorithm = JwaAlgorithm.HS256,
    )
