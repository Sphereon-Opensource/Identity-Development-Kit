/*
 * Copyright 2025 Sphereon International B.V.
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
import com.sphereon.trust.core.revocation.RevocationCheckOptions
import com.sphereon.trust.core.revocation.RevocationChecker
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class CheckRevocationCommandImpl(
    execution: SessionExecution,
    private val revocationChecker: RevocationChecker
) : TypedServiceCommandAdapter<CheckRevocationArgs, RevocationCheckCommandResult>(
    commandId = CheckRevocationCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CheckRevocationArgs>(),
    outputTypeToken = typeToken<RevocationCheckCommandResult>()
), CheckRevocationCommand {

    override val commandId: String get() = CheckRevocationCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CheckRevocationArgs,
        applyDuring: (CheckRevocationArgs) -> CheckRevocationArgs
    ): IdkResult<RevocationCheckCommandResult, IdkError> {
        val applied = applyDuring(args)

        val options = RevocationCheckOptions(
            checkOCSP = applied.checkOcsp,
            checkCRL = applied.checkCrl,
            preferOCSP = applied.preferOcsp,
            timeoutMs = applied.timeoutMs
        )

        val result = revocationChecker.checkRevocation(
            certificate = applied.certificateDer,
            issuerCertificate = applied.issuerCertificateDer,
            options = options
        )

        return Ok(
            RevocationCheckCommandResult(
                status = result.status.name,
                method = result.method.name,
                checkedAt = result.checkedAt,
                fromCache = result.fromCache,
                revocationTime = result.revocationTime,
                revocationReason = result.revocationReason?.name,
                errorMessage = result.errorMessage
            )
        )
    }

}
