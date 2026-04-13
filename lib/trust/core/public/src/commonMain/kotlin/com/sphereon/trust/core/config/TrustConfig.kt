/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.config

import kotlinx.serialization.Serializable

/**
 * Provides trust configuration, typically bound from properties under `trust.*`.
 */
interface TrustConfigProvider {
    fun getTrustConfig(): TrustConfig
}

/**
 * Root trust configuration, bound from properties under `trust.*`.
 */
@Serializable
data class TrustConfig(
    val validation: TrustValidationConfig = TrustValidationConfig(),
    val anchors: TrustAnchorsConfig = TrustAnchorsConfig(),
    val revocation: RevocationConfig = RevocationConfig(),
    val cache: TrustCacheConfig = TrustCacheConfig()
)

@Serializable
data class TrustValidationConfig(
    val enabled: Boolean = true,
    val defaultCheckRevocation: Boolean = true
)

@Serializable
data class TrustAnchorsConfig(
    val x509: X509TrustConfig = X509TrustConfig(),
    val etsi: EtsiTrustConfig = EtsiTrustConfig(),
    val oidfed: OidfTrustConfig = OidfTrustConfig(),
    val did: DidTrustConfig = DidTrustConfig()
)

@Serializable
data class X509TrustConfig(
    val enabled: Boolean = false,
    val caBundlePaths: List<String> = emptyList(),
    val caBundleUrls: List<String> = emptyList(),
    val trustedFingerprints: List<String> = emptyList(),
    /** Max number of CA bundle sources that may fail to load before returning an error. Default 1: a single failure is tolerated, 2+ is an error. Set to 0 to fail on any source failure. */
    val maxFailedSources: Int = 1
)

@Serializable
data class EtsiTrustConfig(
    val enabled: Boolean = false,
    /** The LoTL (List of Trusted Lists) URL — the single authoritative entry point. Member state trust list URLs are discovered by resolving the LoTL. */
    val lotlUrl: String? = null,
    val verifySignatures: Boolean = true,
    /** If non-empty, only resolve member state trust lists for these territories (e.g., ["NL", "DE"]). Empty means all. */
    val territories: List<String> = emptyList()
)

@Serializable
data class OidfTrustConfig(
    val enabled: Boolean = false,
    val trustAnchors: List<String> = emptyList(),
    val maxChainDepth: Int = 5,
    val requiredTrustMarks: List<String> = emptyList()
)

@Serializable
data class DidTrustConfig(
    val enabled: Boolean = false,
    val trustedDids: List<String> = emptyList(),
    /** DID methods allowed for trust evaluation; DIDs with methods not in this list are rejected outright */
    val allowedMethods: List<String> = emptyList()
)

@Serializable
data class RevocationConfig(
    val enabled: Boolean = true,
    val checkOcsp: Boolean = true,
    val checkCrl: Boolean = true,
    val preferOcsp: Boolean = true,
    val timeoutMs: Long = 10000
)

@Serializable
data class TrustCacheConfig(
    val trustListTtlMinutes: Long = 60,
    val revocationTtlMinutes: Long = 15,
    val oidfedEntityTtlMinutes: Long = 30
)
