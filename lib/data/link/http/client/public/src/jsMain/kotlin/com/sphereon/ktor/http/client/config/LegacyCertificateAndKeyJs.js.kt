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

package com.sphereon.ktor.http.client.config

/**
 * On JS there's no X509Certificate/PrivateKey support,
 * so we just carry blobs (PEM/Base64 strings) as `Any`.
 */
data class LegacyCertificateAndKeyJs(
    override val certificateChain: Array<Any>,
    override val key: Any,
) : LegacyCertificateAndKey<Any, Any> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as LegacyCertificateAndKeyJs

        if (!certificateChain.contentEquals(other.certificateChain)) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = certificateChain.contentHashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}
