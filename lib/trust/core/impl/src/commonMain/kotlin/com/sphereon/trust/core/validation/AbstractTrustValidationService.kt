/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.validation

import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult

/**
 * Abstract base implementation of TrustValidationService providing common functionality.
 */
abstract class AbstractTrustValidationService(
    private val id: String,
    private val supportedContextTypes: Set<String>,
) : TrustValidationService {
    override fun getId(): String = id

    override fun supports(context: TrustContext): Boolean = context.type in supportedContextTypes

    override suspend fun refresh(): Boolean = true

    /**
     * Enriches a [TrustValidationResult] with discovered entity info if
     * [TrustValidationRequest.entityDiscovery] is enabled.
     */
    protected suspend fun enrichWithEntityInfo(
        result: TrustValidationResult,
        request: TrustValidationRequest,
        extractor: EntityInfoExtractor,
    ): TrustValidationResult {
        val options = request.entityDiscovery ?: return result
        if (!options.enabled || options.deferred) {
            return result
        }

        val entities =
            extractor.extractEntityInfo(
                context = request.context,
                validationPath = result.validationPath,
                options = options,
            )
        return result.copy(discoveredEntities = entities)
    }
}
