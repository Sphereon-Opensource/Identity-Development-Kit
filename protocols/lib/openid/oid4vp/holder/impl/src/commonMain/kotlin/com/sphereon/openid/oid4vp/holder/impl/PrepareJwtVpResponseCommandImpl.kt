/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.holder.HolderPreparedJwtVpResponse
import com.sphereon.openid.oid4vp.holder.PrepareJwtVpResponseArgs
import com.sphereon.openid.oid4vp.holder.PrepareJwtVpResponseCommand
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class PrepareJwtVpResponseCommandImpl(
    execution: SessionExecution,
    private val responseBuilder: CreateAuthorizationResponseCommandImpl,
) : TypedServiceCommandAdapter<PrepareJwtVpResponseArgs, HolderPreparedJwtVpResponse, IdkError>(
    commandId = PrepareJwtVpResponseCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<PrepareJwtVpResponseArgs>(),
    outputTypeToken = typeToken<HolderPreparedJwtVpResponse>(),
), PrepareJwtVpResponseCommand {
    override val commandId: String get() = PrepareJwtVpResponseCommand.COMMAND_ID
    override suspend fun supports(args: Any): Boolean = args is PrepareJwtVpResponseArgs
    override suspend fun doExecute(args: PrepareJwtVpResponseArgs, applyDuring: (PrepareJwtVpResponseArgs) -> PrepareJwtVpResponseArgs): IdkResult<HolderPreparedJwtVpResponse, IdkError> {
        val args = applyDuring(args)
        return responseBuilder.prepareJwtVpResponse(args.request, args.selectedCredentials.map { it.selectedCredential() })
    }
}
