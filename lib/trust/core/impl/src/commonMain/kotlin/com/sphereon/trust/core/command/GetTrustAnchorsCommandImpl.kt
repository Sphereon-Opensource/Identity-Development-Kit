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
import com.sphereon.trust.core.model.TrustAnchor
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class GetTrustAnchorsCommandImpl(
    execution: SessionExecution,
    private val validators: Set<TrustValidationService>,
) : TypedServiceCommandAdapter<GetTrustAnchorsArgs, TrustAnchorListResult, IdkError>(
        commandId = GetTrustAnchorsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetTrustAnchorsArgs>(),
        outputTypeToken = typeToken<TrustAnchorListResult>(),
    ),
    GetTrustAnchorsCommand {
    override val commandId: String get() = GetTrustAnchorsCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetTrustAnchorsArgs,
        applyDuring: (GetTrustAnchorsArgs) -> GetTrustAnchorsArgs,
    ): IdkResult<TrustAnchorListResult, IdkError> {
        val applied = applyDuring(args)

        val allAnchors = mutableListOf<TrustAnchor>()
        for (validator in validators) {
            val contextType = applied.contextType
            if (contextType != null &&
                !validator.supports(
                    com.sphereon.trust.core.model
                        .TrustContext(type = contextType),
                )
            ) {
                continue
            }

            allAnchors.addAll(validator.getTrustAnchors())
        }

        return Ok(TrustAnchorListResult(anchors = allAnchors, totalCount = allAnchors.size))
    }
}
