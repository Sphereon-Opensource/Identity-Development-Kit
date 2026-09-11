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
import com.sphereon.crypto.kms.rest.api.command.CertificateReferenceMetadataResponse
import com.sphereon.crypto.kms.rest.api.command.CertificateReferencesResponse
import com.sphereon.crypto.kms.rest.api.command.GetCertificateReferenceInput
import com.sphereon.crypto.kms.rest.api.command.GetCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListCertificateReferencesInput
import com.sphereon.crypto.kms.rest.api.command.ListCertificateReferencesServiceCommand
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
@ContributesBinding(SessionScope::class, binding = binding<ListCertificateReferencesServiceCommand>())
class ListCertificateReferencesServiceCommandImpl(
    execution: SessionExecution,
    private val certificatesService: CertificatesRestService,
) : TypedServiceCommandAdapter<ListCertificateReferencesInput, CertificateReferencesResponse, IdkError>(
        commandId = ListCertificateReferencesServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListCertificateReferencesInput>(),
        outputTypeToken = typeToken<CertificateReferencesResponse>(),
    ), ListCertificateReferencesServiceCommand {
    override val commandId: String = ListCertificateReferencesServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListCertificateReferencesInput,
        applyDuring: (ListCertificateReferencesInput) -> ListCertificateReferencesInput,
    ): IdkResult<CertificateReferencesResponse, IdkError> {
        val input = applyDuring(args)
        if (input.providerId?.isBlank() == true) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId must not be blank"))
        }
        return projectionResult {
            certificatesService.listCertificateReferences(input.providerId, input.kind, input.source)
        }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCertificateReferenceServiceCommand>())
class GetCertificateReferenceServiceCommandImpl(
    execution: SessionExecution,
    private val certificatesService: CertificatesRestService,
) : TypedServiceCommandAdapter<GetCertificateReferenceInput, CertificateReferenceMetadataResponse, IdkError>(
        commandId = GetCertificateReferenceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetCertificateReferenceInput>(),
        outputTypeToken = typeToken<CertificateReferenceMetadataResponse>(),
    ), GetCertificateReferenceServiceCommand {
    override val commandId: String = GetCertificateReferenceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetCertificateReferenceInput,
        applyDuring: (GetCertificateReferenceInput) -> GetCertificateReferenceInput,
    ): IdkResult<CertificateReferenceMetadataResponse, IdkError> {
        val input = applyDuring(args)
        if (input.id.isBlank()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "id must not be blank"))
        return projectionResult { certificatesService.getCertificateReference(input.id) }
    }
}

private suspend fun <T> projectionResult(block: suspend () -> T): IdkResult<T, IdkError> =
    try {
        Ok(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (expected: CertificateReferenceResolutionException) {
        Err(IdkError.fromString(code = expected.code, message = expected.message ?: "Certificate reference query failed"))
    } catch (_: Exception) {
        Err(IdkError.UNKNOWN_ERROR(message = "Certificate reference query failed"))
    }
