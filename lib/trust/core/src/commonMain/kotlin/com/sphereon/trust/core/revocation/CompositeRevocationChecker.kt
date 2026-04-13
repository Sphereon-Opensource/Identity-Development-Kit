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

package com.sphereon.trust.core.revocation

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.context.SessionExecution
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.di.session.SessionScope

/**
 * Composite revocation checker that tries multiple revocation checking methods.
 *
 * This checker attempts to verify revocation status using multiple methods
 * (OCSP, CRL) based on the provided options, preferring OCSP over CRL by default.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RevocationChecker>())
class CompositeRevocationChecker(
    private val ocspChecker: Lazy<OCSPChecker?>,
    private val crlChecker: Lazy<CRLChecker?>,
    private val execution: SessionExecution
) : RevocationChecker {

    private val logger = execution.log.logManager.withTagAsync("CompositeRevocationChecker")

    override suspend fun checkRevocation(
        certificate: ByteArray,
        issuerCertificate: ByteArray?,
        options: RevocationCheckOptions
    ): RevocationCheckResult {
        logger.debug("Checking certificate revocation status")

        val methods = determineCheckOrder(options)
        var lastError: String? = null

        for (method in methods) {
            try {
                val result = when (method) {
                    RevocationCheckMethod.OCSP -> {
                        val checker = ocspChecker.value
                        if (checker != null && options.checkOCSP) {
                            logger.debug("Attempting OCSP revocation check")
                            checker.checkOCSP(certificate, issuerCertificate, options)
                        } else {
                            null
                        }
                    }
                    RevocationCheckMethod.CRL -> {
                        val checker = crlChecker.value
                        if (checker != null && options.checkCRL) {
                            logger.debug("Attempting CRL revocation check")
                            checker.checkCRL(certificate, options)
                        } else {
                            null
                        }
                    }
                    else -> null
                }

                if (result != null && result.status != RevocationStatus.UNAVAILABLE) {
                    logger.info("Revocation status determined via ${result.method}: ${result.status}")
                    return result
                }

                lastError = result?.errorMessage
            } catch (expected: Exception) {
                logger.warn("Revocation check failed using $method")
                lastError = expected.message
            }
        }

        // All methods failed or returned UNAVAILABLE
        logger.warn("Could not determine revocation status: $lastError")

        return if (options.failOnUnknown) {
            RevocationCheckResult(
                status = RevocationStatus.UNAVAILABLE,
                method = RevocationCheckMethod.NONE,
                checkedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                errorMessage = lastError ?: "Could not determine revocation status"
            )
        } else {
            // Treat as GOOD if we can't determine status and failOnUnknown is false
            RevocationCheckResult(
                status = RevocationStatus.GOOD,
                method = RevocationCheckMethod.NONE,
                checkedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                errorMessage = "Revocation check not performed: $lastError",
                details = mapOf("assumed" to "true")
            )
        }
    }

    private fun determineCheckOrder(options: RevocationCheckOptions): List<RevocationCheckMethod> {
        val methods = mutableListOf<RevocationCheckMethod>()

        if (options.preferOCSP) {
            if (options.checkOCSP) methods.add(RevocationCheckMethod.OCSP)
            if (options.checkCRL) methods.add(RevocationCheckMethod.CRL)
        } else {
            if (options.checkCRL) methods.add(RevocationCheckMethod.CRL)
            if (options.checkOCSP) methods.add(RevocationCheckMethod.OCSP)
        }

        return methods
    }
}

/**
 * Interface for OCSP checking.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OCSPChecker", exact = true)
interface OCSPChecker {
    suspend fun checkOCSP(
        certificate: ByteArray,
        issuerCertificate: ByteArray?,
        options: RevocationCheckOptions
    ): RevocationCheckResult
}

/**
 * Interface for CRL checking.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CRLChecker", exact = true)
interface CRLChecker {
    suspend fun checkCRL(
        certificate: ByteArray,
        options: RevocationCheckOptions
    ): RevocationCheckResult
}
