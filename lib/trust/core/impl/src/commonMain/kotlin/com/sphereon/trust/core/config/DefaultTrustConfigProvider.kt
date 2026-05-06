/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.config

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Provides trust configuration bound from properties under the `trust` prefix.
 *
 * Properties map to TrustConfig structure:
 * ```properties
 * trust.validation.enabled=true
 * trust.validation.default-check-revocation=true
 * trust.anchors.x509.enabled=true
 * trust.anchors.x509.ca-bundle-paths.0=/etc/ssl/certs/ca-certificates.crt
 * trust.anchors.x509.trusted-fingerprints.0=sha256:abc123
 * trust.anchors.etsi.enabled=true
 * trust.anchors.etsi.trust-list-urls.0=https://ec.europa.eu/tools/lotl/eu-lotl.xml
 * trust.anchors.etsi.lotl-url=https://ec.europa.eu/tools/lotl/eu-lotl.xml
 * trust.anchors.did.enabled=true
 * trust.anchors.did.allowed-methods.0=web
 * trust.anchors.did.allowed-methods.1=key
 * trust.anchors.did.trusted-dids.0=did:web:example.com
 * trust.anchors.oidfed.enabled=false
 * trust.revocation.enabled=true
 * trust.cache.trust-list-ttl-minutes=60
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TrustConfigProvider>())
class DefaultTrustConfigProvider(
    private val execution: SessionExecution,
) : TrustConfigProvider {
    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val DEFAULT_TRUST_LIST_TTL_MINUTES = 60L
        const val DEFAULT_REVOCATION_TTL_MINUTES = 15L
        const val DEFAULT_OIDFED_ENTITY_TTL_MINUTES = 30L
        const val DEFAULT_MAX_CHAIN_DEPTH = 5
    }

    private val configService: AppConfigService
        get() = execution.conf.app

    private var cachedConfig: TrustConfig? = null

    override fun getTrustConfig(): TrustConfig {
        cachedConfig?.let { return it }
        val p = "trust"
        val config =
            TrustConfig(
                validation =
                    TrustValidationConfig(
                        enabled = configService.getProperty("$p.validation.enabled", Boolean::class, true) ?: true,
                        defaultCheckRevocation = configService.getProperty("$p.validation.default-check-revocation", Boolean::class, true) ?: true,
                    ),
                anchors =
                    TrustAnchorsConfig(
                        x509 = readX509Config("$p.anchors.x509"),
                        etsi = readEtsiConfig("$p.anchors.etsi"),
                        oidfed = readOidfConfig("$p.anchors.oidfed"),
                        did = readDidConfig("$p.anchors.did"),
                    ),
                revocation =
                    RevocationConfig(
                        enabled = configService.getProperty("$p.revocation.enabled", Boolean::class, true) ?: true,
                        checkOcsp = configService.getProperty("$p.revocation.check-ocsp", Boolean::class, true) ?: true,
                        checkCrl = configService.getProperty("$p.revocation.check-crl", Boolean::class, true) ?: true,
                        preferOcsp = configService.getProperty("$p.revocation.prefer-ocsp", Boolean::class, true) ?: true,
                        timeoutMs = configService.getProperty("$p.revocation.timeout-ms", Long::class, DEFAULT_TIMEOUT_MS) ?: DEFAULT_TIMEOUT_MS,
                    ),
                cache =
                    TrustCacheConfig(
                        trustListTtlMinutes = configService.getProperty("$p.cache.trust-list-ttl-minutes", Long::class, DEFAULT_TRUST_LIST_TTL_MINUTES) ?: DEFAULT_TRUST_LIST_TTL_MINUTES,
                        revocationTtlMinutes = configService.getProperty("$p.cache.revocation-ttl-minutes", Long::class, DEFAULT_REVOCATION_TTL_MINUTES) ?: DEFAULT_REVOCATION_TTL_MINUTES,
                        oidfedEntityTtlMinutes = configService.getProperty("$p.cache.oidfed-entity-ttl-minutes", Long::class, DEFAULT_OIDFED_ENTITY_TTL_MINUTES) ?: DEFAULT_OIDFED_ENTITY_TTL_MINUTES,
                    ),
            )
        cachedConfig = config
        return config
    }

    private fun readX509Config(prefix: String): X509TrustConfig =
        X509TrustConfig(
            enabled = configService.getProperty("$prefix.enabled", Boolean::class, false) ?: false,
            caBundlePaths = readStringList("$prefix.ca-bundle-paths"),
            caBundleUrls = readStringList("$prefix.ca-bundle-urls"),
            trustedFingerprints = readStringList("$prefix.trusted-fingerprints"),
            maxFailedSources = configService.getProperty("$prefix.max-failed-sources", Int::class, 1) ?: 1,
        )

    private fun readEtsiConfig(prefix: String): EtsiTrustConfig =
        EtsiTrustConfig(
            enabled = configService.getProperty("$prefix.enabled", Boolean::class, false) ?: false,
            lotlUrl = configService.getPropertyAsString("$prefix.lotl-url", null),
            verifySignatures = configService.getProperty("$prefix.verify-signatures", Boolean::class, true) ?: true,
            territories = readStringList("$prefix.territories"),
        )

    private fun readOidfConfig(prefix: String): OidfTrustConfig =
        OidfTrustConfig(
            enabled = configService.getProperty("$prefix.enabled", Boolean::class, false) ?: false,
            trustAnchors = readStringList("$prefix.trust-anchors"),
            maxChainDepth = configService.getProperty("$prefix.max-chain-depth", Int::class, DEFAULT_MAX_CHAIN_DEPTH) ?: DEFAULT_MAX_CHAIN_DEPTH,
            requiredTrustMarks = readStringList("$prefix.required-trust-marks"),
        )

    private fun readDidConfig(prefix: String): DidTrustConfig =
        DidTrustConfig(
            enabled = configService.getProperty("$prefix.enabled", Boolean::class, false) ?: false,
            trustedDids = readStringList("$prefix.trusted-dids"),
            allowedMethods = readStringList("$prefix.allowed-methods"),
        )

    /**
     * Read an indexed list-of-strings entry from configuration. Two notations exist in
     * this codebase and both must work:
     *
     *  - **Dot-indexed** (`<prefix>.0`, `<prefix>.1`): produced by hand-authored properties
     *    files and the way unit tests author keys directly.
     *  - **Bracket-indexed** (`<prefix>[0]`, `<prefix>[1]`): produced by the YAML loader
     *    when flattening a YAML list (`YamlPropertySourceImpl.flattenYaml`). The
     *    `PropertyKeyNormalizer` preserves bracket-quoted segments verbatim, so the two
     *    forms hash to distinct keys after normalisation — without this method handling
     *    both, YAML-authored lists silently appear empty (e.g. trust anchors never load).
     *
     * Bracket form is checked first because that's what every YAML config in this repo
     * actually emits; dot form is the fallback for property-file authors.
     */
    private fun readStringList(prefix: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (true) {
            val value =
                configService.getPropertyAsString("$prefix[$i]", null)
                    ?: configService.getPropertyAsString("$prefix.$i", null)
                    ?: break
            result.add(value)
            i++
        }
        return result
    }
}
