package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

/**
 * Explicit provider capability. The caller supplies previously protected, server-generated code
 * material and an immutable registration snapshot. Implementations must never overwrite a used
 * or conflicting code; other stores do not gain this capability by implementing the base SPI.
 */
interface PreAuthorizedCodeIdempotentStorage : PreAuthorizedCodeStorage {
    suspend fun registerOnce(code: String, data: PreAuthorizedCodeData): IdkResult<PreAuthorizedCodeRegistrationStatus, AuthorizationServerError.StorageError>
}

enum class PreAuthorizedCodeRegistrationStatus { CREATED, REPLAYED, CONFLICT, USED, EXPIRED, INDETERMINATE }
