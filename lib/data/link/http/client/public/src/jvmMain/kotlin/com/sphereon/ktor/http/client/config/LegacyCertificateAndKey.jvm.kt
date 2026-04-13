/*
 * © 2025 Sphereon International B.V.
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

import java.security.PrivateKey
import java.security.cert.X509Certificate
import io.ktor.network.tls.CertificateAndKey as KtorCertificateAndKey

/**
 * On the JVM, delegate to the real Ktor class.
 */
data class LegacyCertificateAndKeyJvm(
    override val certificateChain: Array<X509Certificate>,
    override val key: PrivateKey,
) : LegacyCertificateAndKey<X509Certificate, PrivateKey> {

    /** Convert this into the Ktor type for CIO's HTTPS config */
    fun toKtor(): KtorCertificateAndKey = KtorCertificateAndKey(
        certificateChain,
        key
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KtorCertificateAndKey

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
