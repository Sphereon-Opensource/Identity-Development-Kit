/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.kms.rest.api.command.CertificateReferenceResponse
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceInput
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.server.service.CertificateReferenceResolutionException
import com.sphereon.crypto.kms.rest.server.service.CertificatesRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RegisterCertificateReferenceServiceCommand>())
class RegisterCertificateReferenceServiceCommandImpl(
    execution: SessionExecution,
    private val certificatesService: CertificatesRestService,
) : TypedServiceCommandAdapter<RegisterCertificateReferenceInput, CertificateReferenceResponse, IdkError>(
        commandId = RegisterCertificateReferenceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RegisterCertificateReferenceInput>(),
        outputTypeToken = typeToken<CertificateReferenceResponse>(),
    ), RegisterCertificateReferenceServiceCommand {
    override val commandId: String = RegisterCertificateReferenceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RegisterCertificateReferenceInput,
        applyDuring: (RegisterCertificateReferenceInput) -> RegisterCertificateReferenceInput,
    ): IdkResult<CertificateReferenceResponse, IdkError> {
        val input = applyDuring(args)
        if (input.providerId.isBlank() || input.alias.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId and alias must not be blank"))
        }
        return try {
            Ok(certificatesService.registerCertificateReference(input))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: CertificateReferenceResolutionException) {
            Err(IdkError.fromString(code = expected.code, message = expected.message ?: "Certificate reference registration failed"))
        } catch (_: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Certificate reference registration failed"))
        }
    }
}
