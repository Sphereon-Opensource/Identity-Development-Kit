/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.holder.FinalizeJwtVpResponseArgs
import com.sphereon.openid.oid4vp.holder.FinalizeJwtVpResponseCommand
import com.sphereon.openid.oid4vp.holder.HolderPreparedJwtVpResponse
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class FinalizeJwtVpResponseCommandImpl(
    execution: SessionExecution,
    private val responseBuilder: CreateAuthorizationResponseCommandImpl,
) : TypedServiceCommandAdapter<FinalizeJwtVpResponseArgs, AuthorizationResponse, IdkError>(
    commandId = FinalizeJwtVpResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<FinalizeJwtVpResponseArgs>(),
    outputTypeToken = typeToken<AuthorizationResponse>(),
), FinalizeJwtVpResponseCommand {
    override val commandId: String get() = FinalizeJwtVpResponseCommand.COMMAND_ID
    override suspend fun supports(args: Any): Boolean = args is FinalizeJwtVpResponseArgs
    override suspend fun doExecute(args: FinalizeJwtVpResponseArgs, applyDuring: (FinalizeJwtVpResponseArgs) -> FinalizeJwtVpResponseArgs): IdkResult<AuthorizationResponse, IdkError> {
        val args = applyDuring(args)
        return responseBuilder.finalizeJwtVpResponse(args.prepared, args.signatures)
    }
}
