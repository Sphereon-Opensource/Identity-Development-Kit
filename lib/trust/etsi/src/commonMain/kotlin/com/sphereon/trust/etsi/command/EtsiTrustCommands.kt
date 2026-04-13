/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.command

import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.trust.core.model.TrustValidationResult
import kotlinx.serialization.Serializable

/**
 * Command for validating trust against ETSI TS 119 612 trust lists.
 */
interface ValidateEtsiTrustCommand : ServiceCommand<ValidateEtsiTrustArgs, TrustValidationResult> {
    companion object {
        const val COMMAND_ID = "trust.etsi.validate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

@Serializable
data class ValidateEtsiTrustArgs(
    val trustListUri: String,
    val identifierJson: String,
    val checkRevocation: Boolean = true,
    val territory: String? = null
)
