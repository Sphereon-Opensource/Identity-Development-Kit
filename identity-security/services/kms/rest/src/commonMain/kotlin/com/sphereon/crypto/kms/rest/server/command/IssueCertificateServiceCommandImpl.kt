/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.kms.rest.api.command.IssueCertificateFromCsrServiceCommand
import com.sphereon.crypto.kms.rest.api.command.IssueCertificateServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateResponse
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateFromCsrRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateRequest
import com.sphereon.crypto.kms.rest.server.service.CertificatesRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ExposeImplBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssueCertificateServiceCommand>())
@ExposeImplBinding
class IssueCertificateServiceCommandImpl(
    execution: SessionExecution,
    private val certificatesService: CertificatesRestService,
) : TypedServiceCommandAdapter<IssueCertificateRequest, CertificateResponse, IdkError>(
        commandId = IssueCertificateServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<IssueCertificateRequest>(),
        outputTypeToken = typeToken<CertificateResponse>(),
    ), IssueCertificateServiceCommand {
    override val commandId: String = IssueCertificateServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: IssueCertificateRequest,
        applyDuring: (IssueCertificateRequest) -> IssueCertificateRequest,
    ) = Ok(certificatesService.issueCertificate(applyDuring(args)))
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssueCertificateFromCsrServiceCommand>())
@ExposeImplBinding
class IssueCertificateFromCsrServiceCommandImpl(
    execution: SessionExecution,
    private val certificatesService: CertificatesRestService,
) : TypedServiceCommandAdapter<IssueCertificateFromCsrRequest, CertificateResponse, IdkError>(
        commandId = IssueCertificateFromCsrServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<IssueCertificateFromCsrRequest>(),
        outputTypeToken = typeToken<CertificateResponse>(),
    ), IssueCertificateFromCsrServiceCommand {
    override val commandId: String = IssueCertificateFromCsrServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: IssueCertificateFromCsrRequest,
        applyDuring: (IssueCertificateFromCsrRequest) -> IssueCertificateFromCsrRequest,
    ) = Ok(certificatesService.issueCertificateFromCsr(applyDuring(args)))
}
