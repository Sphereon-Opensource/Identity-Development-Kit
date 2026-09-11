/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ManagedKeyInfoType

/**
 * Resolves an already registered provider key for external-reference onboarding.
 *
 * This is deliberately a provider-facing seam. Its default implementation is session-scoped
 * and Task 4 replaces that binding with the provider-specific implementation.
 */
interface ProviderKeyReferenceInspector {
    suspend fun inspect(
        providerId: String,
        alias: String,
        kid: String? = null,
    ): IdkResult<ManagedKeyInfoType<*>, IdkError>
}
