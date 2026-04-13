/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.validation

import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.model.TrustContext

/**
 * Abstract base implementation of TrustValidationService providing common functionality.
 */
abstract class AbstractTrustValidationService(
    private val id: String,
    private val supportedContextTypes: Set<String>
) : TrustValidationService {

    override fun getId(): String = id

    override fun supports(context: TrustContext): Boolean {
        return context.type in supportedContextTypes
    }

    override suspend fun refresh(): Boolean {
        return true
    }
}
