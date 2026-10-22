/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.error.ClaimMappingErrors
import com.sphereon.credential.claims.mapper.api.mapper.ClaimsMappingService
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialValidityState
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/**
 * Projects typed, holder-visible claim values from a wallet record through the existing
 * format-specific claim resolver set. Keys use the resolver's dotted claim-path representation;
 * mdoc keys therefore retain both namespace and element name.
 */
class WalletCredentialClaimValuesResolver(
    private val claimsMappingService: ClaimsMappingService,
    private val clock: Clock = Clock.System,
) {
    /**
     * Resolve values from the record's currently presentable active instance, or from the exact
     * [credentialInstanceId] when supplied. An explicit instance must still be active and valid.
     */
    suspend fun resolve(
        record: CredentialRecord,
        credentialInstanceId: String? = null,
    ): IdkResult<Map<String, JsonElement>, IdkError> {
        val now = clock.now()
        val instance =
            if (credentialInstanceId == null) {
                record.presentableInstance(now)
            } else {
                record.instances.firstOrNull { it.id == credentialInstanceId }
            } ?: return extractionError(record.id, "No presentable credential instance was found")

        if (record.deletedAt != null || !instance.isPresentableAt(now)) {
            return extractionError(record.id, "The selected credential instance is not active and valid")
        }
        val raw = instance.raw ?: return extractionError(record.id, "The credential body is not loaded")

        return claimsMappingService.extractAllClaims(
            CredentialWithId(
                credentialId = instance.id,
                format = instance.format,
                payload = raw,
            ),
        )
    }

    private fun CredentialInstance.isPresentableAt(now: kotlin.time.Instant): Boolean =
        lifecycleState == CredentialLifecycleState.ACTIVE &&
            validity.stateAt(now) != CredentialValidityState.NOT_YET_VALID &&
            validity.stateAt(now) != CredentialValidityState.EXPIRED

    private fun extractionError(
        credentialId: String,
        reason: String,
    ): IdkResult<Map<String, JsonElement>, IdkError> =
        Err(ClaimMappingErrors.claimExtractionFailed(credentialId, reason))
}
