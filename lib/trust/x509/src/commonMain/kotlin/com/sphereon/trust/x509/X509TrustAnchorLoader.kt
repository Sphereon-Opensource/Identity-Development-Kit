/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

/**
 * Loads X.509 trust anchors as PEM-encoded certificate strings.
 *
 * Reads `trust.anchors.x509.ca-bundle-paths` (file paths) and
 * `trust.anchors.x509.ca-bundle-urls` (HTTP-fetched bundles) per
 * [com.sphereon.trust.core.config.X509TrustConfig], extracting individual
 * `BEGIN CERTIFICATE`/`END CERTIFICATE` blocks. Failed sources are
 * tolerated up to `maxFailedSources`; beyond that, loading throws.
 *
 * Returns an empty list when the X.509 trust path is disabled
 * (`trust.anchors.x509.enabled=false` or no sources configured).
 *
 * Consumers needing trusted anchors for chain validation outside the
 * generic [com.sphereon.trust.core.TrustValidationService] entry point —
 * notably the mDoc IACA path in OID4VP, which feeds raw PEMs into
 * `MdocValidations.fromDocument(trustedCerts = ...)` — should inject this
 * loader rather than reading config directly.
 */
interface X509TrustAnchorLoader {
    suspend fun loadTrustedCerts(): List<String>

    /**
     * Loads the configured anchors plus request-scoped PEM bundle paths.
     *
     * Protocol configuration supplies these paths through its typed runtime
     * configuration (for example OID4VCI key-attester trust). Implementations
     * that cannot read local files retain their configured anchors.
     */
    suspend fun loadTrustedCerts(additionalCaBundlePaths: List<String>): List<String> = loadTrustedCerts()
}
