/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * Provides a no-op TrustValidationService to ensure the multibinding Set is always available,
 * even when no actual trust validator modules are on the classpath.
 */
@ContributesTo(SessionScope::class)
interface TrustValidationDefaultBindings {
    @Provides @IntoSet
    fun noOpValidator(): TrustValidationService = NoOpTrustValidationService
}

private object NoOpTrustValidationService : TrustValidationService {
    override fun getId(): String = "noop"

    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult = throw UnsupportedOperationException("No trust validator configured for type: ${request.context.type}")

    override fun supports(context: TrustContext): Boolean = false

    override suspend fun getTrustAnchors(): List<TrustAnchor> = emptyList()

    override suspend fun refresh(): Boolean = true
}
