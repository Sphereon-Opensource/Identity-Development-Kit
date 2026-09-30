/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.client.CatalogRemoteSignatureEvidence
import com.sphereon.catalog.client.CatalogRemoteSignatureVerifier
import com.sphereon.catalog.client.CatalogRemoteTrustScope
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Fail-closed default: without a trust-domain aware verifier nothing signed is trusted and nothing
 * unsigned is accepted.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultCatalogRemoteSignatureVerifier : CatalogRemoteSignatureVerifier {
    override suspend fun verifySigned(
        scope: CatalogRemoteTrustScope,
        compactJws: String,
    ): IdkResult<CatalogRemoteSignatureEvidence, IdkError> =
        Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "No trust-domain catalog signature verifier is bound"))

    override suspend fun allowsUnsigned(scope: CatalogRemoteTrustScope): IdkResult<Boolean, IdkError> = Ok(false)
}
