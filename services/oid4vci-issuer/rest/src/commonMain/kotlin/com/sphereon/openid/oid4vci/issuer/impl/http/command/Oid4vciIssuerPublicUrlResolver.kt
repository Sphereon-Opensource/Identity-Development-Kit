/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerProtocolConfig
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Public URLs advertised by OID4VCI issuer metadata.
 *
 * [issuerIdentifier] becomes `credential_issuer` and is the issuer instance's external BASE: the
 * scheme + host (which may be a tenant/instance subdomain) plus whatever path prefix the instance
 * registered, or none. It carries no protocol or `/api` mount. Both the wallet-facing protocol
 * surface and the OAuth2-authenticated backend admin API (`/api/oid4vci/v1/backend/...`) are formed
 * by appending their respective mounts to this base — the tenant is never a URL path parameter
 * (authenticated calls resolve it from the bearer token).
 *
 * [endpointBaseUrl] is the base used for wallet-facing protocol endpoints such as
 * `/credential` or `/oid4vci/credential` when an explicit protocol mount is configured.
 *
 * NOTE (multi-instance): in deployments that run several issuer instances per tenant (VDX), a
 * per-instance resolver must populate these from the SPECIFIC instance's registration, not a
 * one-per-tenant lookup.
 */
data class Oid4vciIssuerPublicUrls(
    val issuerIdentifier: String,
    val endpointBaseUrl: String,
    val authorizationServerBaseUrl: String? = null,
)

/**
 * Resolves the externally visible URLs for a metadata request.
 *
 * The default IDK implementation preserves config-driven behavior. EDK runtimes
 * can bind a tenant-aware implementation that resolves the active tenant's
 * public endpoint binding from the APP-scope tenant registry.
 */
interface Oid4vciIssuerPublicUrlResolver {
    suspend fun resolve(
        request: GenericHttpRequest,
        issuerConfigProvider: Oid4vciIssuerConfigProvider,
        restConfigProvider: Oid4vciRestConfigProvider,
    ): IdkResult<Oid4vciIssuerPublicUrls, IdkError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultOid4vciIssuerPublicUrlResolver(
    private val appConfig: AppConfigService,
) : Oid4vciIssuerPublicUrlResolver {
    override suspend fun resolve(
        request: GenericHttpRequest,
        issuerConfigProvider: Oid4vciIssuerConfigProvider,
        restConfigProvider: Oid4vciRestConfigProvider,
    ): IdkResult<Oid4vciIssuerPublicUrls, IdkError> {
        val issuerIdentifier = issuerConfigProvider.issuerIdentifier.trimEnd('/')
        val endpointBaseUrl =
            Oid4vciIssuerProtocolConfig.appendBasePath(
                baseUrl = restConfigProvider.getConfig().externalBaseUrl ?: issuerIdentifier,
                basePath = Oid4vciIssuerProtocolConfig.resolveBasePath(appConfig),
            )
        return Ok(
            Oid4vciIssuerPublicUrls(
                issuerIdentifier = issuerIdentifier,
                endpointBaseUrl = endpointBaseUrl,
            ),
        )
    }
}
