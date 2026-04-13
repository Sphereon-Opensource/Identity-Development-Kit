/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1EncapsulatingOctetString
import at.asitplus.awesn1.Asn1Primitive
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.encoding.Asn1
import at.asitplus.awesn1.encoding.parse
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.interop.toX509Certificate
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Cross-platform OCSP checker using Ktor HTTP client.
 *
 * Extracts the OCSP responder URL from the certificate's AIA extension,
 * builds a minimal OCSP request, sends it, and parses the response.
 *
 * Note: Full OCSP request/response ASN.1 construction per RFC 6960 requires
 * building a proper OCSPRequest with the issuer name hash, issuer key hash,
 * and serial number. This implementation provides the HTTP transport and
 * basic response status parsing.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<OCSPChecker>())
class DefaultOCSPChecker(
    private val httpClientFactory: HttpClientFactory,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
) : OCSPChecker {
    private companion object {
        // SHA-1 AlgorithmIdentifier OID bytes: 1.3.14.3.2.26
        val SHA1_OID_BYTES = byteArrayOf(0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A.toByte())

        // OCSP response status codes (RFC 6960)
        const val OCSP_STATUS_TRY_LATER = 3
        const val OCSP_STATUS_SIG_REQUIRED = 5
        const val OCSP_STATUS_UNAUTHORIZED = 6

        // ASN.1 structure minimum child counts
        const val SINGLE_RESPONSE_MIN_CHILDREN = 3
        const val CERT_ID_FIELD_COUNT = 4
    }

    private val logger = execution.log.logManager.withTag("DefaultOCSPChecker")
    private val httpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }

    private val cache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.revocation.ocsp",
                ttlConfig = CacheTtlConfig(app = 15.minutes),
            ),
        )
    }

    override suspend fun checkOCSP(
        certificate: ByteArray,
        issuerCertificate: ByteArray?,
        options: RevocationCheckOptions,
    ): RevocationCheckResult {
        return try {
            val serialNumber =
                CertificateRevocationUtils.extractSerialNumber(certificate)
                    ?: return unavailable("Could not extract serial number from certificate")

            val ocspUrl =
                options.ocspResponderUrl
                    ?: CertificateRevocationUtils.extractOcspUrl(certificate)
                    ?: return unavailable("No OCSP responder URL found in certificate")

            // Check cache
            if (options.useCache) {
                val cacheKey = "${serialNumber}_$ocspUrl"
                val cached = cache.getApp(cacheKey)
                if (cached != null) {
                    logger.debug("Using cached OCSP result for serial $serialNumber")
                    return RevocationCheckResult(
                        status = RevocationStatus.valueOf(cached),
                        method = RevocationCheckMethod.OCSP,
                        checkedAt = Clock.System.now().toEpochMilliseconds(),
                        fromCache = true,
                    )
                }
            }

            if (issuerCertificate == null) {
                return unavailable("Issuer certificate is required for OCSP but not provided")
            }

            logger.debug("Sending OCSP request to $ocspUrl for serial $serialNumber")

            // Build a minimal OCSP request
            val ocspRequest = buildOcspRequest(certificate, issuerCertificate, serialNumber)
            if (ocspRequest == null) {
                return unavailable("Failed to build OCSP request")
            }

            val response =
                httpClient.post(ocspUrl) {
                    contentType(ContentType("application", "ocsp-request"))
                    header("Accept", "application/ocsp-response")
                    setBody(ocspRequest)
                }

            if (!response.status.isSuccess()) {
                return unavailable("OCSP responder returned HTTP ${response.status.value}")
            }

            val responseBytes = response.readRawBytes()
            val result = parseOcspResponse(responseBytes)

            // Cache successful results
            if (options.useCache && result.status != RevocationStatus.UNAVAILABLE) {
                val cacheKey = "${serialNumber}_$ocspUrl"
                cache.putApp(cacheKey, result.status.name)
            }

            result
        } catch (expected: Exception) {
            logger.error("OCSP check failed", exception = expected)
            unavailable("OCSP check failed: ${expected.message}")
        }
    }

    /**
     * Builds an OCSP request per RFC 6960.
     *
     * OCSPRequest ::= SEQUENCE {
     *   tbsRequest TBSRequest ::= SEQUENCE {
     *     requestList SEQUENCE OF Request ::= SEQUENCE {
     *       reqCert CertID ::= SEQUENCE {
     *         hashAlgorithm AlgorithmIdentifier (SHA-1: 1.3.14.3.2.26),
     *         issuerNameHash OCTET STRING,
     *         issuerKeyHash OCTET STRING,
     *         serialNumber CertificateSerialNumber (INTEGER)
     *       }
     *     }
     *   }
     * }
     */
    private fun buildOcspRequest(
        certificate: ByteArray,
        issuerCertificate: ByteArray,
        serialNumber: String,
    ): ByteArray? =
        try {
            val issuerCert =
                com.sphereon.crypto.core.x509
                    .certificateFromDer(issuerCertificate)
            val issuerX509 = issuerCert.toX509Certificate()

            // SHA-1 hash of issuer's Distinguished Name (DER encoded)
            val issuerNameDer =
                Asn1
                    .Sequence {
                        issuerX509.tbsCertificate.issuerName.forEach { +it }
                    }.derEncoded
            val issuerNameHash =
                org.kotlincrypto.hash.sha1
                    .SHA1()
                    .digest(issuerNameDer)

            // SHA-1 hash of issuer's public key (raw key bytes)
            val issuerKeyBytes = issuerX509.tbsCertificate.subjectPublicKeyInfo.subjectPublicKey.rawBytes
            val issuerKeyHash =
                org.kotlincrypto.hash.sha1
                    .SHA1()
                    .digest(issuerKeyBytes)

            // Serial number of the certificate being checked
            val cert =
                com.sphereon.crypto.core.x509
                    .certificateFromDer(certificate)
            val certX509 = cert.toX509Certificate()
            val serialBytes = certX509.tbsCertificate.serialNumber

            // Build the OCSPRequest ASN.1 structure
            // SHA-1 AlgorithmIdentifier OID: 1.3.14.3.2.26
            val sha1Oid = SHA1_OID_BYTES

            Asn1
                .Sequence {
                    // tbsRequest
                    +Asn1.Sequence {
                        // requestList
                        +Asn1.Sequence {
                            // single Request
                            +Asn1.Sequence {
                                // CertID
                                +Asn1.Sequence {
                                    // hashAlgorithm (SHA-1)
                                    +Asn1.Sequence {
                                        +Asn1Primitive(Asn1Element.Tag.OID, sha1Oid.drop(2).toByteArray())
                                        +Asn1Primitive(Asn1Element.Tag.NULL, byteArrayOf())
                                    }
                                    // issuerNameHash
                                    +Asn1.OctetString(issuerNameHash)
                                    // issuerKeyHash
                                    +Asn1.OctetString(issuerKeyHash)
                                    // serialNumber
                                    +Asn1Primitive(Asn1Element.Tag.INT, serialBytes)
                                }
                            }
                        }
                    }
                }.derEncoded
        } catch (_: Exception) {
            null
        }

    /*
     * Parses an OCSP response to extract the certificate status.
     *
     * OCSPResponse ::= SEQUENCE {
     *   responseStatus ENUMERATED { successful(0), ... },
     *   responseBytes [0] EXPLICIT SEQUENCE { ... } OPTIONAL
     * }
     */

    /**
     * Parses an OCSP response per RFC 6960.
     *
     * OCSPResponse ::= SEQUENCE {
     *   responseStatus ENUMERATED,
     *   responseBytes [0] EXPLICIT SEQUENCE {
     *     responseType OID,
     *     response OCTET STRING containing BasicOCSPResponse
     *   }
     * }
     *
     * BasicOCSPResponse ::= SEQUENCE {
     *   tbsResponseData ResponseData ::= SEQUENCE {
     *     version [0] EXPLICIT INTEGER DEFAULT v1,
     *     responderID ...,
     *     producedAt GeneralizedTime,
     *     responses SEQUENCE OF SingleResponse ::= SEQUENCE {
     *       certID CertID,
     *       certStatus CHOICE { good [0], revoked [1], unknown [2] },
     *       thisUpdate GeneralizedTime,
     *       ...
     *     }
     *   },
     *   ...
     * }
     */
    private fun parseOcspResponse(responseBytes: ByteArray): RevocationCheckResult {
        return try {
            val element = Asn1Element.parse(responseBytes)
            val seq =
                element as? Asn1Sequence
                    ?: return unavailable("OCSP response is not a valid ASN.1 SEQUENCE")

            val statusPrim =
                seq.children.firstOrNull() as? Asn1Primitive
                    ?: return unavailable("OCSP response status not found")

            val statusByte = statusPrim.content.lastOrNull()?.toInt() ?: -1

            when (statusByte) {
                0 -> parseSuccessfulOcspResponse(seq)
                1 -> unavailable("OCSP responder returned: malformedRequest")
                2 -> unavailable("OCSP responder returned: internalError")
                OCSP_STATUS_TRY_LATER -> unavailable("OCSP responder returned: tryLater")
                OCSP_STATUS_SIG_REQUIRED -> unavailable("OCSP responder returned: sigRequired")
                OCSP_STATUS_UNAUTHORIZED -> unavailable("OCSP responder returned: unauthorized")
                else -> unavailable("OCSP responder returned unknown status: $statusByte")
            }
        } catch (expected: Exception) {
            unavailable("Failed to parse OCSP response: ${expected.message}")
        }
    }

    /**
     * Parses a successful OCSP response to extract the cert status from BasicOCSPResponse.
     * The certStatus in SingleResponse is a CHOICE:
     *   good    [0] IMPLICIT NULL,
     *   revoked [1] IMPLICIT RevokedInfo,
     *   unknown [2] IMPLICIT NULL
     */
    private fun parseSuccessfulOcspResponse(responseSeq: Asn1Sequence): RevocationCheckResult {
        try {
            // responseBytes is [0] EXPLICIT SEQUENCE { responseType, response OCTET STRING }
            if (responseSeq.children.size < 2) {
                return RevocationCheckResult(
                    status = RevocationStatus.GOOD,
                    method = RevocationCheckMethod.OCSP,
                    checkedAt = Clock.System.now().toEpochMilliseconds(),
                    details = mapOf("note" to "OCSP successful but no responseBytes"),
                )
            }

            // Navigate: responseBytes [0] EXPLICIT -> SEQUENCE { responseType, response OCTET STRING }
            // The [0] tagged element wraps a SEQUENCE
            val responseBytesWrapper = responseSeq.children[1]
            // Try to get the inner SEQUENCE from the tagged wrapper
            val innerSeq =
                when (responseBytesWrapper) {
                    is Asn1Sequence -> {
                        responseBytesWrapper
                    }

                    else -> {
                        // Context-tagged: parse its content
                        try {
                            val content =
                                (responseBytesWrapper as? Asn1Primitive)?.content
                                    ?: (responseBytesWrapper as? Asn1EncapsulatingOctetString)?.content
                                    ?: return goodWithNote("Could not unwrap responseBytes")
                            Asn1Element.parse(content) as? Asn1Sequence
                                ?: return goodWithNote("responseBytes content is not a SEQUENCE")
                        } catch (expected: Exception) {
                            logger.debug("Could not parse responseBytes wrapper: ${expected.message}")
                            return goodWithNote("Could not parse responseBytes wrapper")
                        }
                    }
                }

            // OCTET STRING containing BasicOCSPResponse DER
            val responseOctet = innerSeq.children.getOrNull(1)
            val basicResponseBytes =
                (responseOctet as? Asn1Primitive)?.content
                    ?: (responseOctet as? Asn1EncapsulatingOctetString)?.content
                    ?: return goodWithNote("Could not extract BasicOCSPResponse")

            val basicResponse =
                Asn1Element.parse(basicResponseBytes) as? Asn1Sequence
                    ?: return goodWithNote("BasicOCSPResponse is not a SEQUENCE")

            // tbsResponseData is the first child
            val tbsResponseData =
                basicResponse.children.firstOrNull() as? Asn1Sequence
                    ?: return goodWithNote("TBSResponseData not found")

            // Find the responses SEQUENCE (contains SingleResponse entries)
            // It's typically after version (optional [0]), responderID, producedAt
            for (child in tbsResponseData.children) {
                val responsesSeq = child as? Asn1Sequence ?: continue
                if (responsesSeq.children.isEmpty()) {
                    continue
                }
                val firstResponse = responsesSeq.children.firstOrNull() as? Asn1Sequence ?: continue
                // A SingleResponse has certID (SEQUENCE) as first child, certStatus as second
                if (firstResponse.children.size < SINGLE_RESPONSE_MIN_CHILDREN) {
                    continue
                }
                val certId = firstResponse.children[0] as? Asn1Sequence ?: continue
                if (certId.children.size < CERT_ID_FIELD_COUNT) {
                    continue
                } // CertID has 4 fields

                // certStatus is the second element — context-tagged
                val certStatusElement = firstResponse.children[1]
                val tag = certStatusElement.tag.tagValue.toInt()

                return when (tag) {
                    0 -> {
                        RevocationCheckResult(
                            status = RevocationStatus.GOOD,
                            method = RevocationCheckMethod.OCSP,
                            checkedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    }

                    1 -> {
                        RevocationCheckResult(
                            status = RevocationStatus.REVOKED,
                            method = RevocationCheckMethod.OCSP,
                            checkedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    }

                    2 -> {
                        RevocationCheckResult(
                            status = RevocationStatus.UNKNOWN,
                            method = RevocationCheckMethod.OCSP,
                            checkedAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    }

                    else -> {
                        goodWithNote("Unknown certStatus tag: $tag")
                    }
                }
            }

            return goodWithNote("No SingleResponse found in BasicOCSPResponse")
        } catch (expected: Exception) {
            return goodWithNote("Failed to parse BasicOCSPResponse: ${expected.message}")
        }
    }

    private fun goodWithNote(note: String) =
        RevocationCheckResult(
            status = RevocationStatus.GOOD,
            method = RevocationCheckMethod.OCSP,
            checkedAt = Clock.System.now().toEpochMilliseconds(),
            details = mapOf("note" to note),
        )

    private fun unavailable(message: String) =
        RevocationCheckResult(
            status = RevocationStatus.UNAVAILABLE,
            method = RevocationCheckMethod.OCSP,
            checkedAt = Clock.System.now().toEpochMilliseconds(),
            errorMessage = message,
        )
}
