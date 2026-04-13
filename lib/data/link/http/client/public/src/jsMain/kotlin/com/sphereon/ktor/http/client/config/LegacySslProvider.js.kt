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

class JsSslProviderImpl(
    private val opts: LegacySslConfig,
) : LegacySslProvider {
    override suspend fun getCertificates(): List<LegacyCertificateAndKeyJs> {
/*  FIXME: Only in-memory keystores (Bytes) on JS for now
        opts.keyStoreOpts?.let {
            val raw: ByteArray = when (val s = it.source) {
                is KeyStoreOpts.Source.Bytes -> s.data
                else -> throw IllegalStateException("Only in-memory keystores supported on JS targets")
            }
            val b64: String = raw.encodeToBase64()
            return listOf(CertificateAndKey(arrayOf(b64), b64))
        }
*/
        return emptyList()
    }

    override suspend fun getTrustManager(): dynamic = null
}

actual fun createLegacySslProvider(opts: LegacySslConfig): LegacySslProvider = JsSslProviderImpl(opts)
