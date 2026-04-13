package com.sphereon.ktor.http.client.provider

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

data class CompositeTrustManager(val allTrustManagers: Set<X509TrustManager>): X509TrustManager {
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
        allTrustManagers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
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

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
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