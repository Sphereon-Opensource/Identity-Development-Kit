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

package com.sphereon.trust.core.revocation

import com.sphereon.core.api.context.SessionExecution
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.di.session.SessionScope
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * JVM implementation of OCSP checker using Java's security APIs.
 * 
 * This implementation follows RFC 6960 (OCSP) to check certificate revocation status.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OCSPChecker>())
class JvmOCSPChecker(
    private val execution: SessionExecution
) : OCSPChecker {

    private val logger = execution.log.logManager.withTagAsync("JvmOCSPChecker")
    private val loggerSync = execution.log.logManager.withTag("JvmOCSPChecker")
    private val cache = mutableMapOf<String, CachedOCSPResponse>()

    override suspend fun checkOCSP(
        certificate: ByteArray,
        issuerCertificate: ByteArray?,
        options: RevocationCheckOptions
    ): RevocationCheckResult {
        return try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val cert = certificateFactory.generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate

            // Get OCSP responder URL
            val ocspUrl = options.ocspResponderUrl ?: extractOCSPUrl(cert)
            if (ocspUrl == null) {
                logger.debug("No OCSP responder URL found in certificate")
                return RevocationCheckResult(
                    status = RevocationStatus.UNAVAILABLE,
                    method = RevocationCheckMethod.OCSP,
                    checkedAt = Clock.System.now().toEpochMilliseconds(),
                    errorMessage = "No OCSP responder URL found"
                )
            }

            logger.debug("OCSP responder URL: $ocspUrl")

            // Check cache
            if (options.useCache) {
                val cacheKey = "${cert.serialNumber}_$ocspUrl"
                cache[cacheKey]?.let { cached ->
                    val age = System.currentTimeMillis() - cached.timestamp
                    if (age < options.maxCacheAgeMs) {
                        logger.debug("Using cached OCSP response (age: ${age}ms)")
                        return cached.result.copy(fromCache = true)
                    }
                }
            }

            // Parse issuer certificate if provided
            val issuerCert = issuerCertificate?.let {
                certificateFactory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate
            }

            // Build OCSP request
            val ocspRequest = buildOCSPRequest(cert, issuerCert)

            // Send OCSP request
            val ocspResponse = sendOCSPRequest(ocspUrl, ocspRequest, options.timeoutMs)

            // Parse OCSP response
            val result = parseOCSPResponse(ocspResponse, cert)

            // Update cache
            if (options.useCache && result.status != RevocationStatus.UNAVAILABLE) {
                val cacheKey = "${cert.serialNumber}_$ocspUrl"
                cache[cacheKey] = CachedOCSPResponse(
                    result = result,
                    timestamp = System.currentTimeMillis()
                )
            }

            result

        } catch (e: Exception) {
            logger.error("OCSP check failed", exception = e)
            RevocationCheckResult(
                status = RevocationStatus.UNAVAILABLE,
                method = RevocationCheckMethod.OCSP,
                checkedAt = Clock.System.now().toEpochMilliseconds(),
                errorMessage = "OCSP check failed: ${e.message}"
            )
        }
    }

    private fun extractOCSPUrl(certificate: X509Certificate): String? {
        return try {
            // Extract OCSP URL from Authority Information Access extension
            val aiaExtension = certificate.getExtensionValue("1.3.6.1.5.5.7.1.1")
            if (aiaExtension != null) {
                // Parse AIA extension to extract OCSP URL
                // This is a simplified implementation
                // Full implementation would need proper ASN.1 parsing
                val urlString = String(aiaExtension)
                val ocspUrlPattern = Regex("http[s]?://[^\\s]+")
                ocspUrlPattern.find(urlString)?.value
            } else {
                null
            }
        } catch (e: Exception) {
            loggerSync.warn("Failed to extract OCSP URL from certificate")
            null
        }
    }

    private fun buildOCSPRequest(
        certificate: X509Certificate,
        issuerCertificate: X509Certificate?
    ): ByteArray {
        // This is a placeholder implementation
        // Full implementation would use BouncyCastle or similar library
        // to properly construct an OCSP request following RFC 6960

        loggerSync.debug("Building OCSP request for certificate serial: ${certificate.serialNumber}")

        // For now, return empty request
        // TODO: Implement proper OCSP request generation
        return ByteArray(0)
    }

    private fun sendOCSPRequest(
        ocspUrl: String,
        request: ByteArray,
        timeoutMs: Long
    ): ByteArray {
        loggerSync.debug("Sending OCSP request to $ocspUrl")

        val url = URL(ocspUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/ocsp-request")
            connection.setRequestProperty("Accept", "application/ocsp-response")
            connection.doOutput = true
            connection.connectTimeout = timeoutMs.toInt()
            connection.readTimeout = timeoutMs.toInt()

            // Send request
            connection.outputStream.use { output ->
                output.write(request)
            }

            // Read response
            if (connection.responseCode != 200) {
                throw RevocationCheckException("OCSP responder returned ${connection.responseCode}")
            }

            return connection.inputStream.readBytes()

        } finally {
            connection.disconnect()
        }
    }

    private fun parseOCSPResponse(
        response: ByteArray,
        certificate: X509Certificate
    ): RevocationCheckResult {
        // This is a placeholder implementation
        // Full implementation would properly parse the OCSP response
        // following RFC 6960

        loggerSync.debug("Parsing OCSP response (${response.size} bytes)")

        // TODO: Implement proper OCSP response parsing
        // For now, return UNAVAILABLE
        return RevocationCheckResult(
            status = RevocationStatus.UNAVAILABLE,
            method = RevocationCheckMethod.OCSP,
            checkedAt = Clock.System.now().toEpochMilliseconds(),
            errorMessage = "OCSP response parsing not yet implemented"
        )
    }

    private data class CachedOCSPResponse(
        val result: RevocationCheckResult,
        val timestamp: Long
    )
}
