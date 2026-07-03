/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.oidfed

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.trust.core.model.TrustValidationResult
import kotlinx.serialization.Serializable

/**
 * Command for validating trust via OpenID Federation trust chains.
 *
 * The implementation (ValidateOidfTrustCommandImpl) lives in the OpenID Federation repo
 * (openid-federation-trust module) where it has access to ResolveTrustChainCommand
 * and VerifyTrustChainCommand.
 */
interface ValidateOidfTrustCommand : ServiceCommand<ValidateOidfTrustArgs, TrustValidationResult, IdkError> {
    companion object {
        const val COMMAND_ID = "trust.oidfed.validate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class ValidateOidfTrustArgs(
    val entityIdentifier: String,
    /**
     * Candidate OpenID Federation trust anchors. These are not expected to be the entity being
     * validated. Implementations should build and verify the chain upwards from [entityIdentifier]
     * and accept the validation when any supplied anchor appears in the verified path.
     */
    val trustAnchors: List<String> = emptyList(),
    val requiredTrustMarks: List<String> = emptyList(),
    val maxChainDepth: Int = 5,
)
