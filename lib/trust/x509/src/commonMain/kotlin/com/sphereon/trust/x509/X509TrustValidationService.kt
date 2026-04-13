/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.config.TrustConfigProvider
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.validation.AbstractTrustValidationService
import com.sphereon.trust.x509.extractor.X509EntityInfoExtractor
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * X.509 CA bundle trust validation service.
 *
 * Validates certificate chains against the platform trust store and optionally
 * against configured trusted CA certificates.
 *
 * Configuration under `trust.anchors.x509.*`:
 * ```properties
 * trust.anchors.x509.enabled=true
 * trust.anchors.x509.ca-bundle-paths.0=/etc/ssl/certs/ca-certificates.crt
 * trust.anchors.x509.ca-bundle-urls.0=https://example.com/ca-bundle.pem
 * trust.anchors.x509.trusted-fingerprints.0=sha256:AB:CD:EF:...
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(scope = SessionScope::class, binding = binding<TrustValidationService>())
class X509TrustValidationService(
    private val x509VerifyService: X509VerifyService,
    private val trustConfigProvider: TrustConfigProvider,
    private val httpClientFactory: HttpClientFactory,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
    private val entityInfoExtractor: X509EntityInfoExtractor,
) : AbstractTrustValidationService("x509", setOf(TrustContext.TYPE_X509, TrustContext.TYPE_CA_BUNDLE)) {
    private val logger = execution.log.logManager.withTag("X509TrustValidationService")

    private val httpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }

    private val trustedCertsCache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.x509.trusted-certs",
                ttlConfig = CacheTtlConfig(app = 60.minutes),
            ),
        )
    }

    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult {
        logger.debug("Validating X.509 trust for context: ${request.context}")

        return try {
            val identifier = request.identifier
            val x5c =
                when (identifier) {
                    is ExternalIdentifierResult -> identifier.keyInfo?.x5c
                    is ManagedIdentifierResult<*> -> identifier.keyInfo?.x5c
                    else -> null
                }

            if (x5c.isNullOrEmpty()) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNTRUSTED,
                    details = "No X.509 certificate chain (x5c) found in identifier",
                    validatedAt = Clock.System.now(),
                )
            }

            val trustedCerts = loadTrustedCerts()

            val verificationRequest =
                if (trustedCerts.isNotEmpty()) {
                    X509VerificationRequest(
                        chainPEM = x5c,
                        trustedCerts = trustedCerts.toTypedArray(),
                    )
                } else {
                    X509VerificationRequest(chainPEM = x5c)
                }

            val chainResult = x509VerifyService.verifyCertificateChain(verificationRequest)

            if (chainResult.error) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNTRUSTED,
                    details = chainResult.message ?: "Certificate chain is not valid",
                    validatedAt = Clock.System.now(),
                )
            }

            // Check trusted fingerprints if configured
            val x509Config = trustConfigProvider.getTrustConfig().anchors.x509
            if (x509Config.trustedFingerprints.isNotEmpty()) {
                val fingerprintMatch = checkFingerprints(x5c.first(), x509Config.trustedFingerprints)
                if (!fingerprintMatch) {
                    return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "Certificate fingerprint does not match any trusted fingerprint",
                        validatedAt = Clock.System.now(),
                    )
                }
            }

            val result =
                TrustValidationResult(
                    trusted = true,
                    status = TrustStatus.TRUSTED,
                    details = "Certificate chain validated successfully",
                    validatedAt = Clock.System.now(),
                )
            enrichWithX509EntityInfo(result, request, x5c)
        } catch (expected: Exception) {
            logger.error("X.509 trust validation failed", exception = expected)
            TrustValidationResult(
                trusted = false,
                status = TrustStatus.VALIDATION_ERROR,
                details = "X.509 validation error: ${expected.message}",
                validatedAt = Clock.System.now(),
            )
        }
    }

    override suspend fun getTrustAnchors(): List<TrustAnchor> = emptyList()

    private suspend fun loadTrustedCerts(): List<String> {
        val cached = trustedCertsCache.getApp("trusted-certs")
        if (cached != null) {
            return kotlinx.serialization.json.Json
                .decodeFromString<List<String>>(cached)
        }

        val x509Config = trustConfigProvider.getTrustConfig().anchors.x509
        if (!x509Config.enabled) {
            return emptyList()
        }

        val certs = mutableListOf<String>()
        val failedSources = mutableListOf<String>()

        for (path in x509Config.caBundlePaths) {
            try {
                val pemContent = readFileContent(path)
                if (pemContent != null) {
                    val extracted = extractPemCertificates(pemContent)
                    certs.addAll(extracted)
                    logger.debug("Loaded ${extracted.size} certificates from $path")
                } else {
                    failedSources.add(path)
                    logger.error("Configured CA bundle not found or unreadable: $path")
                }
            } catch (expected: Exception) {
                failedSources.add(path)
                logger.error("Failed to load CA bundle from path: $path", exception = expected)
            }
        }

        for (url in x509Config.caBundleUrls) {
            try {
                val response = httpClient.get(url)
                if (response.status.isSuccess()) {
                    val extracted = extractPemCertificates(response.bodyAsText())
                    certs.addAll(extracted)
                    logger.debug("Loaded ${extracted.size} certificates from $url")
                } else {
                    failedSources.add(url)
                    logger.error("Failed to fetch CA bundle from $url: HTTP ${response.status.value}")
                }
            } catch (expected: Exception) {
                failedSources.add(url)
                logger.error("Failed to fetch CA bundle from URL: $url", exception = expected)
            }
        }

        if (failedSources.isNotEmpty()) {
            check(failedSources.size <= x509Config.maxFailedSources) {
                "${failedSources.size} CA bundle source(s) failed to load (threshold: ${x509Config.maxFailedSources}): $failedSources"
            }
            logger.warn("${failedSources.size} CA bundle source(s) failed to load (within threshold ${x509Config.maxFailedSources}): $failedSources")
        }

        if (certs.isNotEmpty()) {
            trustedCertsCache.putApp(
                "trusted-certs",
                kotlinx.serialization.json.Json
                    .encodeToString(certs),
            )
        }

        return certs
    }

    private fun extractPemCertificates(pemContent: String): List<String> {
        val certs = mutableListOf<String>()
        val beginMarker = "-----BEGIN CERTIFICATE-----"
        val endMarker = "-----END CERTIFICATE-----"

        var startIndex = pemContent.indexOf(beginMarker)
        while (startIndex != -1) {
            val endIndex = pemContent.indexOf(endMarker, startIndex)
            if (endIndex == -1) {
                break
            }

            val cert = pemContent.substring(startIndex, endIndex + endMarker.length)
            certs.add(cert)

            startIndex = pemContent.indexOf(beginMarker, endIndex)
        }
        return certs
    }

    /**
     * Checks if a certificate's fingerprint matches any of the trusted fingerprints.
     *
     * Supports matching against:
     * - X.509 certificate SHA-1 fingerprint (uppercase hex, as produced by Certificate.fingerPrint)
     * - JWK thumbprint (Base64url-encoded SHA-256 of the JWK canonical form)
     *
     * Fingerprint values in config are compared case-insensitively.
     * Colon-separated formats (e.g., "AB:CD:EF") are normalized before comparison.
     *
     * @param leafCertPem The leaf certificate in PEM/Base64 format (first entry from x5c)
     * @param trustedFingerprints The configured trusted fingerprint values
     */
    private fun checkFingerprints(
        leafCertPem: String,
        trustedFingerprints: List<String>,
    ): Boolean =
        try {
            val derBytes = leafCertPem.decodeFrom(Encoding.BASE64)
            val certificate = certificateFromDer(derBytes)
            val certFingerprint = certificate.fingerPrint.uppercase().replace(":", "")

            // Also compute the JWK thumbprint from the cert's public key
            val jwkThumbprint =
                try {
                    val jwk = certificate.getPublicKeyJwk()
                    generateJwkThumbprint(jwk)
                } catch (expected: Exception) {
                    logger.debug("Failed to compute JWK thumbprint for fingerprint check: ${expected.message}")
                    null
                }

            trustedFingerprints.any { configured ->
                val normalized = configured.uppercase().replace(":", "")
                normalized == certFingerprint || (jwkThumbprint != null && configured == jwkThumbprint)
            }
        } catch (expected: Exception) {
            logger.debug("Failed to check certificate fingerprints: ${expected.message}")
            false
        }

    /**
     * Builds entity discovery chain directly from the x5c cert chain.
     * Independent from ETSI — just X.509 DN info.
     */
    private fun enrichWithX509EntityInfo(
        result: TrustValidationResult,
        request: TrustValidationRequest,
        x5c: Array<String>,
    ): TrustValidationResult {
        val options = request.entityDiscovery ?: return result
        if (!options.enabled || options.deferred) {
            return result
        }

        val maxDepth =
            if (options.maxDepth == 0) {
                Int.MAX_VALUE
            } else {
                options.maxDepth
            }
        val entities =
            buildList {
                for ((index, certBase64) in x5c.withIndex()) {
                    if (size >= maxDepth) {
                        break
                    }
                    try {
                        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
                        val cert = certificateFromDer(derBytes)
                        val nodeRole =
                            if (index == 0) {
                                TrustChainNodeRole.LEAF
                            } else {
                                TrustChainNodeRole.INTERMEDIATE
                            }

                        add(
                            entityInfoExtractor.extractFromCertificateInfo(
                                subjectDN = cert.subjectDN,
                                issuerDN = cert.issuerDN,
                                depth = index,
                                nodeRole = nodeRole,
                            ),
                        )
                    } catch (expected: Exception) {
                        logger.debug("Failed to parse x5c certificate for entity discovery: ${expected.message}")
                    }
                }

                // If only leaf cert and issuer differs, add issuer DN info
                if (x5c.size == 1 && size < maxDepth) {
                    try {
                        val leafDer = x5c.first().decodeFrom(Encoding.BASE64)
                        val leafCert = certificateFromDer(leafDer)
                        if (leafCert.issuerDN != leafCert.subjectDN) {
                            add(
                                entityInfoExtractor
                                    .extractFromCertificateInfo(
                                        subjectDN = leafCert.issuerDN,
                                        depth = size,
                                        nodeRole = TrustChainNodeRole.TRUST_ANCHOR,
                                    ).copy(trustAnchor = true),
                            )
                        }
                    } catch (expected: Exception) {
                        logger.debug("Failed to parse leaf cert issuer DN for entity discovery: ${expected.message}")
                    }
                }
            }
        return result.copy(discoveredEntities = entities)
    }
}
