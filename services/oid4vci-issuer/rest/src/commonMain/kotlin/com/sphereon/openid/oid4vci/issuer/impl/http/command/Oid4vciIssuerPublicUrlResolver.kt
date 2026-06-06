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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Public URLs advertised by OID4VCI issuer metadata.
 *
 * [issuerIdentifier] becomes `credential_issuer`. [endpointBaseUrl] is the base
 * used for protocol endpoints such as `/oid4vci/credential`.
 */
data class Oid4vciIssuerPublicUrls(
    val issuerIdentifier: String,
    val endpointBaseUrl: String,
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
class DefaultOid4vciIssuerPublicUrlResolver : Oid4vciIssuerPublicUrlResolver {
    override suspend fun resolve(
        request: GenericHttpRequest,
        issuerConfigProvider: Oid4vciIssuerConfigProvider,
        restConfigProvider: Oid4vciRestConfigProvider,
    ): IdkResult<Oid4vciIssuerPublicUrls, IdkError> {
        val issuerIdentifier = issuerConfigProvider.issuerIdentifier.trimEnd('/')
        val endpointBaseUrl = (restConfigProvider.getConfig().externalBaseUrl ?: issuerIdentifier).trimEnd('/')
        return Ok(
            Oid4vciIssuerPublicUrls(
                issuerIdentifier = issuerIdentifier,
                endpointBaseUrl = endpointBaseUrl,
            ),
        )
    }
}
