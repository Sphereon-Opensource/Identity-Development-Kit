/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustValidationService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class RefreshTrustAnchorsCommandImpl(
    execution: SessionExecution,
    private val validators: Set<TrustValidationService>,
) : TypedServiceCommandAdapter<RefreshTrustArgs, RefreshTrustResult>(
        commandId = RefreshTrustAnchorsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RefreshTrustArgs>(),
        outputTypeToken = typeToken<RefreshTrustResult>(),
    ),
    RefreshTrustAnchorsCommand {
    override val commandId: String get() = RefreshTrustAnchorsCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RefreshTrustArgs,
        applyDuring: (RefreshTrustArgs) -> RefreshTrustArgs,
    ): IdkResult<RefreshTrustResult, IdkError> {
        val applied = applyDuring(args)

        val refreshedTypes = mutableListOf<String>()
        var allSuccess = true

        for (validator in validators) {
            val success = validator.refresh()
            if (success) {
                refreshedTypes.add(validator.getId())
            } else {
                allSuccess = false
            }
        }

        return Ok(
            RefreshTrustResult(
                refreshed = allSuccess,
                anchorTypesRefreshed = refreshedTypes,
                details =
                    if (allSuccess) {
                        "All trust anchors refreshed"
                    } else {
                        "Some trust anchors failed to refresh"
                    },
            ),
        )
    }
}
