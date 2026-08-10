/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.trust.core.config.TrustConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.minutes

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(scope = SessionScope::class, binding = binding<X509TrustAnchorLoader>())
class X509TrustAnchorLoaderImpl(
    private val trustConfigProvider: TrustConfigProvider,
    private val httpClientFactory: HttpClientFactory,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
    private val additionalSources: Set<X509TrustAnchorSource> = emptySet(),
) : X509TrustAnchorLoader {
    private val logger = execution.log.logManager.withTag("X509TrustAnchorLoader")

    private val httpClient by lazy { httpClientFactory.createClient(HttpClientOptions()) }

    private val trustedCertsCache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.x509.trusted-certs",
                ttlConfig = CacheTtlConfig(app = 60.minutes),
            ),
        )
    }

    override suspend fun loadTrustedCerts(): List<String> = loadTrustedCerts(emptyList())

    override suspend fun loadTrustedCerts(additionalCaBundlePaths: List<String>): List<String> {
        val configuredCerts = loadConfiguredTrustedCerts(additionalCaBundlePaths)
        if (additionalSources.isEmpty()) {
            return configuredCerts
        }

        val additionalCerts = mutableListOf<String>()
        for (source in additionalSources) {
            try {
                val loaded = source.loadTrustedCerts()
                additionalCerts.addAll(loaded)
                logger.debug("Loaded ${loaded.size} certificates from X.509 trust anchor source '${source.sourceId}'")
            } catch (expected: Exception) {
                logger.error("Failed to load certificates from X.509 trust anchor source '${source.sourceId}'", exception = expected)
            }
        }
        return (configuredCerts + additionalCerts).distinct()
    }

    private suspend fun loadConfiguredTrustedCerts(additionalCaBundlePaths: List<String>): List<String> {
        val x509Config = trustConfigProvider.getTrustConfig().anchors.x509
        val caBundlePaths =
            ((if (x509Config.enabled) x509Config.caBundlePaths else emptyList()) + additionalCaBundlePaths)
                .filter(String::isNotBlank)
                .distinct()
        val caBundleUrls = if (x509Config.enabled) x509Config.caBundleUrls else emptyList()
        if (caBundlePaths.isEmpty() && caBundleUrls.isEmpty()) {
            return emptyList()
        }
        val cacheKey =
            buildString {
                append("configured-trusted-certs:")
                append(caBundlePaths.joinToString("\u001f"))
                append('\u001e')
                append(caBundleUrls.joinToString("\u001f"))
            }
        val cached = trustedCertsCache.getTenant(execution.tenantId, cacheKey)
        if (cached != null) {
            return kotlinx.serialization.json.Json
                .decodeFromString<List<String>>(cached)
        }

        val certs = mutableListOf<String>()
        val failedSources = mutableListOf<String>()

        for (path in caBundlePaths) {
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

        for (url in caBundleUrls) {
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
            trustedCertsCache.putTenant(
                execution.tenantId,
                cacheKey,
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
}
