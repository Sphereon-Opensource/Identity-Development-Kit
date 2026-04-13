/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

import com.sphereon.core.api.context.SessionExecution
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.di.session.SessionScope
import kotlinx.datetime.Clock

/**
 * Composite revocation checker that tries multiple revocation checking methods.
 * Attempts OCSP and CRL based on options, preferring OCSP over CRL by default.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RevocationChecker>())
class CompositeRevocationChecker(
    private val ocspChecker: OCSPChecker,
    private val crlChecker: CRLChecker,
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
                        if (options.checkOCSP) {
                            logger.debug("Attempting OCSP revocation check")
                            ocspChecker.checkOCSP(certificate, issuerCertificate, options)
                        } else null
                    }
                    RevocationCheckMethod.CRL -> {
                        if (options.checkCRL) {
                            logger.debug("Attempting CRL revocation check")
                            crlChecker.checkCRL(certificate, options)
                        } else null
                    }
                    else -> null
                }

                if (result != null && result.status != RevocationStatus.UNAVAILABLE) {
                    logger.info("Revocation status determined via ${result.method}: ${result.status}")
                    return result
                }

                lastError = result?.errorMessage
            } catch (e: Exception) {
                logger.warn("Revocation check failed using $method")
                lastError = e.message
            }
        }

        logger.warn("Could not determine revocation status: $lastError")

        return if (options.failOnUnknown) {
            RevocationCheckResult(
                status = RevocationStatus.UNAVAILABLE,
                method = RevocationCheckMethod.NONE,
                checkedAt = Clock.System.now().toEpochMilliseconds(),
                errorMessage = lastError ?: "Could not determine revocation status"
            )
        } else {
            RevocationCheckResult(
                status = RevocationStatus.GOOD,
                method = RevocationCheckMethod.NONE,
                checkedAt = Clock.System.now().toEpochMilliseconds(),
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
