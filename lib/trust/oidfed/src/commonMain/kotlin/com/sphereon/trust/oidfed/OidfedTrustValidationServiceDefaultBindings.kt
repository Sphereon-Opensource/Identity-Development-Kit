/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.oidfed

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlin.time.Clock

/**
 * Ensures [Set]<[TrustValidationService]> is present on graphs that bind the OIDFed command
 * even when the OpenID Federation trust module is not on the classpath.
 */
@ContributesTo(SessionScope::class)
interface OidfedTrustValidationServiceDefaultBindings {
    @Provides
    @IntoSet
    fun noOpOidfedValidator(): TrustValidationService = NoOpOidfedTrustValidationService
}

internal object NoOpOidfedTrustValidationService : TrustValidationService {
    override fun getId(): String = "oidfed-unbound"

    override fun supports(context: TrustContext): Boolean = false

    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult =
        TrustValidationResult(
            trusted = false,
            status = com.sphereon.trust.core.model.TrustStatus.VALIDATION_ERROR,
            details = "OID Federation validation is unavailable",
            validatedAt = Clock.System.now(),
        )

    override suspend fun getTrustAnchors(): List<TrustAnchor> = emptyList()

    override suspend fun refresh(): Boolean = true
}
