/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Primitive
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.encoding.parse
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Cross-platform CRL checker using Ktor HTTP client and awesn1 ASN.1 parsing.
 *
 * Extracts CRL Distribution Point URLs from the certificate, fetches the CRL,
 * and checks if the certificate's serial number appears in the revoked list.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<CRLChecker>())
class DefaultCRLChecker(
    private val httpClientFactory: HttpClientFactory,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
) : CRLChecker {
    private companion object {
        const val BYTE_MASK = 0xFF
        const val HEX_RADIX = 16
    }

    private val logger = execution.log.logManager.withTagAsync("DefaultCRLChecker")
    private val httpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }

    private val cache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.revocation.crl",
                ttlConfig = CacheTtlConfig(app = 60.minutes),
            ),
        )
    }

    override suspend fun checkCRL(
        certificate: ByteArray,
        options: RevocationCheckOptions,
    ): RevocationCheckResult {
        return try {
            val serialNumber =
                CertificateRevocationUtils.extractSerialNumber(certificate)
                    ?: return RevocationCheckResult(
                        status = RevocationStatus.UNAVAILABLE,
                        method = RevocationCheckMethod.CRL,
                        checkedAt = Clock.System.now().toEpochMilliseconds(),
                        errorMessage = "Could not extract serial number from certificate",
                    )

            val cdpUrl =
                options.crlDistributionPoint
                    ?: CertificateRevocationUtils.extractCdpUrls(certificate).firstOrNull()
                    ?: return RevocationCheckResult(
                        status = RevocationStatus.UNAVAILABLE,
                        method = RevocationCheckMethod.CRL,
                        checkedAt = Clock.System.now().toEpochMilliseconds(),
                        errorMessage = "No CRL Distribution Point found in certificate",
                    )

            // Check cache
            if (options.useCache) {
                val cacheKey = "${serialNumber}_$cdpUrl"
                val cached = cache.getApp(cacheKey)
                if (cached != null) {
                    logger.debug("Using cached CRL result for serial $serialNumber")
                    return RevocationCheckResult(
                        status = RevocationStatus.valueOf(cached),
                        method = RevocationCheckMethod.CRL,
                        checkedAt = Clock.System.now().toEpochMilliseconds(),
                        fromCache = true,
                    )
                }
            }

            logger.debug("Fetching CRL from $cdpUrl")
            val response =
                httpClient.get(cdpUrl) {
                    header("Accept", "application/pkix-crl, application/x-pkcs7-crl")
                }

            if (!response.status.isSuccess()) {
                return RevocationCheckResult(
                    status = RevocationStatus.UNAVAILABLE,
                    method = RevocationCheckMethod.CRL,
                    checkedAt = Clock.System.now().toEpochMilliseconds(),
                    errorMessage = "CRL server returned HTTP ${response.status.value}",
                )
            }

            val crlBytes = response.readRawBytes()
            val result = checkSerialInCrl(crlBytes, serialNumber)

            // Update cache
            if (options.useCache && result.status != RevocationStatus.UNAVAILABLE) {
                val cacheKey = "${serialNumber}_$cdpUrl"
                cache.putApp(cacheKey, result.status.name)
            }

            result
        } catch (expected: Exception) {
            logger.error("CRL check failed", exception = expected)
            RevocationCheckResult(
                status = RevocationStatus.UNAVAILABLE,
                method = RevocationCheckMethod.CRL,
                checkedAt = Clock.System.now().toEpochMilliseconds(),
                errorMessage = "CRL check failed: ${expected.message}",
            )
        }
    }

    /**
     * Parses the CRL DER bytes and checks if the serial number is in the revoked list.
     *
     * CRL ASN.1 structure (RFC 5280):
     * CertificateList ::= SEQUENCE {
     *   tbsCertList TBSCertList ::= SEQUENCE {
     *     version, signature, issuer, thisUpdate, nextUpdate,
     *     revokedCertificates SEQUENCE OF SEQUENCE {
     *       userCertificate CertificateSerialNumber (INTEGER),
     *       revocationDate Time,
     *       crlEntryExtensions Extensions OPTIONAL
     *     }
     *   },
     *   signatureAlgorithm, signatureValue
     * }
     */
    private fun checkSerialInCrl(
        crlBytes: ByteArray,
        serialNumber: String,
    ): RevocationCheckResult {
        return try {
            val element = Asn1Element.parse(crlBytes)
            val certList =
                element as? Asn1Sequence
                    ?: return unavailable("CRL is not a valid ASN.1 SEQUENCE")

            val tbsCertList =
                certList.children.firstOrNull() as? Asn1Sequence
                    ?: return unavailable("TBSCertList not found in CRL")

            // revokedCertificates is typically the 6th element (after version, sig, issuer, thisUpdate, nextUpdate)
            // but can vary. We look for a SEQUENCE of SEQUENCEs containing INTEGERs.
            for (child in tbsCertList.children) {
                val seq = child as? Asn1Sequence ?: continue
                // Check if this looks like revokedCertificates (a sequence of sequences)
                if (seq.children.isEmpty()) {
                    continue
                }
                val firstEntry = seq.children.firstOrNull() as? Asn1Sequence ?: continue
                // Each entry should start with an INTEGER (the serial number)
                val firstPrim = firstEntry.children.firstOrNull() as? Asn1Primitive ?: continue
                // Tag 0x02 = INTEGER
                if (firstPrim.tag.tagValue != 2uL) {
                    continue
                }

                // This looks like the revokedCertificates list
                for (entry in seq.children) {
                    val entrySeq = entry as? Asn1Sequence ?: continue
                    val serialPrim = entrySeq.children.firstOrNull() as? Asn1Primitive ?: continue
                    if (serialPrim.tag.tagValue != 2uL) {
                        continue
                    }

                    val revokedSerial =
                        serialPrim.content.joinToString("") {
                            (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
                        }
                    val certSerial = normalizeSerial(serialNumber)

                    if (revokedSerial == certSerial) {
                        return RevocationCheckResult(
                            status = RevocationStatus.REVOKED,
                            method = RevocationCheckMethod.CRL,
                            checkedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                }

                // Found the revokedCertificates list and serial not in it
                return RevocationCheckResult(
                    status = RevocationStatus.GOOD,
                    method = RevocationCheckMethod.CRL,
                    checkedAt = Clock.System.now().toEpochMilliseconds(),
                )
            }

            // No revokedCertificates found — CRL is empty, cert is not revoked
            RevocationCheckResult(
                status = RevocationStatus.GOOD,
                method = RevocationCheckMethod.CRL,
                checkedAt = Clock.System.now().toEpochMilliseconds(),
            )
        } catch (expected: Exception) {
            unavailable("Failed to parse CRL: ${expected.message}")
        }
    }

    /**
     * Normalizes a serial number string to lowercase hex without separators.
     */
    private fun normalizeSerial(serial: String): String =
        serial
            .lowercase()
            .replace(":", "")
            .replace(" ", "")
            .removePrefix("0x")

    private fun unavailable(message: String) =
        RevocationCheckResult(
            status = RevocationStatus.UNAVAILABLE,
            method = RevocationCheckMethod.CRL,
            checkedAt = Clock.System.now().toEpochMilliseconds(),
            errorMessage = message,
        )
}
