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

package com.sphereon.ktor.http.client.provider

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

data class CompositeTrustManager(
    val allTrustManagers: Set<X509TrustManager>,
) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = allTrustManagers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        var lastException: CertificateException? = null
        for (tm in allTrustManagers) {
            try {
                tm.checkClientTrusted(chain, authType)
                return // Success: one trust manager accepted
            } catch (e: CertificateException) {
                lastException = e
            }
        }
        throw lastException ?: CertificateException("None of the TrustManagers trust this client certificate chain")
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        var lastException: CertificateException? = null
        for (tm in allTrustManagers) {
            try {
                tm.checkServerTrusted(chain, authType)
                return // one trust manager accepted
            } catch (e: CertificateException) {
                lastException = e
            }
        }
        throw lastException ?: CertificateException("None of the TrustManagers trust this server certificate chain")
    }
}
