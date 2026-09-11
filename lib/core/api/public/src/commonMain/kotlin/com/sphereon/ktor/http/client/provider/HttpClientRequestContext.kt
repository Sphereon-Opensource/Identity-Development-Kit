/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.ktor.http.client.config.CaOpts
import com.sphereon.ktor.http.client.config.ClientSslConfig
import com.sphereon.ktor.http.client.config.KeystoreCertificateOpts
import com.sphereon.ktor.http.client.config.ServerSslConfig
import com.sphereon.ktor.http.client.config.SslConfig
import io.ktor.http.Url
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** Governed clients never permit protocol negotiation below TLS 1.2. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpMinimumTlsVersion", exact = true)
enum class HttpMinimumTlsVersion {
    TLS_1_2,
    TLS_1_3,
}

/** Declares the admitted server-root sources for one governed HTTP context. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpServerTrustMode", exact = true)
enum class HttpServerTrustMode {
    TRUST_DOMAIN_ONLY,
    PLATFORM_DEFAULTS,
    PLATFORM_AND_TRUST_DOMAIN,
}

enum class HttpRedirectPolicy(
    val allowsCallerReresolution: Boolean,
) {
    DISABLED(false),
    RERESOLVE_ALLOWED(true),
}

/**
 * Execution-scoped reference to a client identity already admitted by the tenant KMS policy.
 * This contains only an opaque keystore handle and public certificate metadata.
 */
data class HttpClientTlsIdentityContext(
    val identityReference: String,
    val certificateFingerprint: String,
    val identityRevision: String,
    val certificate: KeystoreCertificateOpts,
    val allowedDestinationHost: String,
) {
    init {
        require(identityReference.isNotBlank()) { "Client TLS identity reference must not be blank" }
        require(certificateFingerprint.isNotBlank()) { "Client TLS certificate fingerprint must not be blank" }
        require(identityRevision.isNotBlank()) { "Client TLS identity revision must not be blank" }
        require(allowedDestinationHost.isNotBlank()) { "Client TLS destination host must not be blank" }
    }
}

/**
 * Immutable security inputs for one outbound request. Resolution is performed before this value is
 * created; it deliberately has no serialization contract and never contains certificate private-key bytes.
 */
data class HttpClientRequestContext(
    val tenantId: String,
    val workloadIdentity: String,
    val connectorId: String,
    val operationId: String,
    val destinationUri: String,
    val endpointId: String,
    val endpointHost: String,
    val egressProfileRevision: String,
    val trustDomainRevision: String,
    val anchorSetDigest: String,
    val anchorSetReference: String,
    val clientTlsIdentity: HttpClientTlsIdentityContext? = null,
    val redirectPolicy: HttpRedirectPolicy = HttpRedirectPolicy.DISABLED,
    val minimumTlsVersion: HttpMinimumTlsVersion = HttpMinimumTlsVersion.TLS_1_2,
    val engine: HttpClientEngineType = HttpClientEngineType.OKHTTP,
    val serverTrustMode: HttpServerTrustMode = HttpServerTrustMode.TRUST_DOMAIN_ONLY,
    val serverTrust: CaOpts,
    val sslClientDefaultCertificate: KeystoreCertificateOpts? = null,
    /** Opaque connector security-profile revision used to bind higher-level execution caches. */
    val connectorProfileRevision: String? = null,
) {
    init {
        listOf(
            "tenantId" to tenantId,
            "workloadIdentity" to workloadIdentity,
            "connectorId" to connectorId,
            "operationId" to operationId,
            "endpointId" to endpointId,
            "egressProfileRevision" to egressProfileRevision,
            "trustDomainRevision" to trustDomainRevision,
            "anchorSetDigest" to anchorSetDigest,
            "anchorSetReference" to anchorSetReference,
        ).forEach { (name, value) -> require(value.isNotBlank()) { "$name must not be blank" } }
        connectorProfileRevision?.let { require(it.isNotBlank()) { "connectorProfileRevision must not be blank" } }

        val destination = runCatching { Url(destinationUri) }
            .getOrElse { throw IllegalArgumentException("Destination URI is invalid", it) }
        val normalizedDestinationHost = destination.host.normalizedHost()
        require(destination.protocol.name.equals("https", ignoreCase = true)) {
            "Governed connector HTTP clients require HTTPS"
        }
        require(endpointHost.normalizedHost() == normalizedDestinationHost) {
            "Connector endpoint host must match the destination URI host"
        }
        require(destination.user == null && destination.password == null) {
            "Governed connector destinations must not contain userinfo"
        }
        require(destination.fragment.isEmpty()) {
            "Governed connector destinations must not contain fragments"
        }
        require(engine == HttpClientEngineType.OKHTTP) {
            "Governed custom trust and client TLS identities require the complete OkHttp engine"
        }
        require(serverTrust.additionalCAs.isEmpty()) {
            "Governed partner connector server trust must not use credential or synthetic keystore references"
        }
        when (serverTrustMode) {
            HttpServerTrustMode.TRUST_DOMAIN_ONLY -> {
                require(!serverTrust.includePlatformDefaults && serverTrust.resolvedCertificates.isNotEmpty()) {
                    "TrustDomain-only server trust requires resolved anchors and excludes platform defaults"
                }
            }

            HttpServerTrustMode.PLATFORM_DEFAULTS -> {
                require(serverTrust.includePlatformDefaults && serverTrust.resolvedCertificates.isEmpty()) {
                    "Platform-default server trust requires platform defaults without TrustDomain anchors"
                }
            }

            HttpServerTrustMode.PLATFORM_AND_TRUST_DOMAIN -> {
                require(serverTrust.includePlatformDefaults && serverTrust.resolvedCertificates.isNotEmpty()) {
                    "Combined server trust requires platform defaults and resolved TrustDomain anchors"
                }
            }
        }
        require(sslClientDefaultCertificate == null) {
            "Governed connector contexts must not configure a default client certificate"
        }
        clientTlsIdentity?.let { identity ->
            require(identity.allowedDestinationHost.normalizedHost() == normalizedDestinationHost) {
                "Client TLS identity is not selected for the destination host"
            }
        }
    }

    fun toHttpClientOptions(): HttpClientOptions {
        val destinationHost = Url(destinationUri).host.normalizedHost()
        val perHostCertificate = clientTlsIdentity?.let { identity ->
            mapOf(destinationHost to identity.certificate)
        } ?: emptyMap()
        return HttpClientOptions(
            engine = engine,
            enableContentNegotiation = true,
            sslConfig = SslConfig(
                client = ClientSslConfig(
                    engine = engine,
                    minimumTlsVersion = minimumTlsVersion,
                    perHostCertificate = perHostCertificate,
                    defaultCertificate = sslClientDefaultCertificate,
                ),
                server = ServerSslConfig(ca = serverTrust),
            ),
            followRedirects = false,
            additionalConfig = { followRedirects = false },
            urlValidation = UrlValidationPolicy.exactTarget(destinationUri),
        )
    }
}

private fun String.normalizedHost(): String = trim().trimEnd('.').lowercase()


