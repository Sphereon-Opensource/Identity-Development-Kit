/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.signing.AsSigningSelection
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig

/** Local captured result; signing handles and configuration never enter the public observation DTO. */
data class CapturedServerMetadataAssembly(
    val metadata: AuthorizationServerMetadata,
    val signing: AsSigningSelection,
)

/** Reuses the ordinary discovery builder's single captured assembly. No second metadata engine. */
interface CapturedServerMetadataAssembler {
    suspend fun assembleCaptured(
        captured: CapturedAsServerConfig,
        baseUrlOverride: String?,
        includeSignedMetadata: Boolean,
        strictCapabilities: Boolean,
    ): IdkResult<CapturedServerMetadataAssembly, AuthorizationServerError>
}
