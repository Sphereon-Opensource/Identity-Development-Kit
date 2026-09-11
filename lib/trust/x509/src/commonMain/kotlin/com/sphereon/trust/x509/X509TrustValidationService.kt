/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

import com.sphereon.core.api.Encoding
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
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.config.TrustConfigProvider
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustChain
import com.sphereon.trust.core.model.TrustChainLinks
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.validation.AbstractTrustValidationService
import com.sphereon.trust.x509.extractor.X509EntityInfoExtractor
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

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
    private val trustAnchorLoader: X509TrustAnchorLoader,
    private val execution: SessionExecution,
    private val entityInfoExtractor: X509EntityInfoExtractor,
) : AbstractTrustValidationService("x509", setOf(TrustContext.TYPE_X509, TrustContext.TYPE_CA_BUNDLE)) {
    private val logger = execution.log.logManager.withTag("X509TrustValidationService")

    override suspend fun doValidate(request: TrustValidationRequest): TrustValidationResult {
        logger.debug("Validating X.509 trust for context: ${request.context}")

        return try {
            val identifier = request.identifier
            val x5c =
                request.context.parameters[PARAM_X5C]
                    ?.decodeStringList()
                    ?.toTypedArray()
                    ?: when (identifier) {
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

            val trustedCerts =
                request.context.parameters[PARAM_TRUSTED_CERTS]?.decodeStringList()
                    ?: trustAnchorLoader.loadTrustedCerts()

            val verificationRequest =
                verificationRequestFor(
                    x5c = x5c,
                    trustedCerts = trustedCerts,
                )

            val chainResult = x509VerifyService.verifyCertificateChain(verificationRequest)

            if (chainResult.error) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNTRUSTED,
                    details = chainResult.message ?: "Certificate chain is not valid",
                    validatedAt = Clock.System.now(),
                    trustChain = x509TrustChain(x5c, TrustChainLinks.BROKEN),
                )
            }

            // Check trusted fingerprints if configured
            val x509Config = trustConfigProvider.getTrustConfig().anchors.x509
            val trustedFingerprints =
                request.context.parameters[PARAM_TRUSTED_FINGERPRINTS]?.decodeStringList()
                    ?: x509Config.trustedFingerprints
            if (trustedFingerprints.isNotEmpty()) {
                val fingerprintMatch = checkFingerprints(x5c.first(), trustedFingerprints)
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
                    trustChain = x509TrustChain(x5c, TrustChainLinks.VERIFIED),
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

    override suspend fun doGetTrustAnchors(): List<TrustAnchor> = emptyList()

    /**
     * JOSE/COSE x5c values are base64-encoded DER certificates, while older callers of this
     * generic trust service also supplied PEM. Passing DER x5c as `chainPEM` makes valid mdoc and
     * JWT credentials fail before path validation. Preserve PEM compatibility, but use the typed
     * DER request whenever the whole chain is an x5c-style base64 chain.
     */
    private fun verificationRequestFor(
        x5c: Array<String>,
        trustedCerts: List<String>,
    ): X509VerificationRequest {
        val trusted = trustedCerts.takeIf { it.isNotEmpty() }?.toTypedArray()
        val derChain =
            x5c.takeUnless { values -> values.any { it.contains("BEGIN CERTIFICATE") } }
                ?.map { value ->
                    runCatching { value.decodeFrom(Encoding.BASE64) }.getOrNull()
                }
                ?.takeIf { values -> values.all { it != null && it.isNotEmpty() } }
                ?.mapNotNull { it }
                ?.toTypedArray()
        return if (derChain != null) {
            X509VerificationRequest(
                chainDER = derChain,
                trustedCerts = trusted,
            )
        } else {
            X509VerificationRequest(
                chainPEM = x5c,
                trustedCerts = trusted,
            )
        }
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

    private fun x509TrustChain(
        x5c: Array<String>,
        links: TrustChainLinks,
    ): TrustChain? {
        if (x5c.isEmpty()) return null
        val subjects =
            x5c.map { pem ->
                runCatching {
                    val der = pem.decodeFrom(Encoding.BASE64)
                    certificateFromDer(der).subjectDN.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        if (subjects.any { it == null }) return null
        return TrustChain.fromOrderedIdentifiers(subjects.filterNotNull(), links)
    }

    private companion object {
        const val PARAM_X5C = "x5c"
        const val PARAM_TRUSTED_CERTS = "trustedCerts"
        const val PARAM_TRUSTED_FINGERPRINTS = "trustedFingerprints"

        val PARAM_JSON = Json { ignoreUnknownKeys = true }

        fun String.decodeStringList(): List<String> =
            try {
                PARAM_JSON.decodeFromString<List<String>>(this)
            } catch (_: SerializationException) {
                split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } catch (_: IllegalArgumentException) {
                split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
    }
}
