/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

/**
 * Session-scoped extension point for runtime-managed X.509 trust anchors.
 *
 * Static CA bundle paths/URLs remain owned by [X509TrustAnchorLoader]; EDK can
 * contribute tenant trust-domain sources here so existing X.509 and mDoc IACA
 * validation paths receive the same PEM certificate list.
 */
interface X509TrustAnchorSource {
    val sourceId: String

    suspend fun loadTrustedCerts(): List<String>
}
