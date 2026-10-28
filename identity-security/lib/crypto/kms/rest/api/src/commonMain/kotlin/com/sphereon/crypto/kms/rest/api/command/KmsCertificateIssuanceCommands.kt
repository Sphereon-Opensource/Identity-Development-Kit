/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.api.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateResponse
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateFromCsrRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateRequest

interface IssueCertificateServiceCommand : ServiceCommand<IssueCertificateRequest, CertificateResponse, IdkError>, PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.certificates.issue"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/certificates/issue",
            consumes = setOf(MediaType.ApplicationJson),
            produces = setOf(MediaType.ApplicationJson),
            commandId = COMMAND_ID,
            handlerCommandId = COMMAND_ID,
            tags = setOf("Certificates"),
            summary = "Issue a certificate",
        )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

interface IssueCertificateFromCsrServiceCommand :
    ServiceCommand<IssueCertificateFromCsrRequest, CertificateResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.certificates.issue-from-csr"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/certificates/issue-from-csr",
            consumes = setOf(MediaType.ApplicationJson),
            produces = setOf(MediaType.ApplicationJson),
            commandId = COMMAND_ID,
            handlerCommandId = COMMAND_ID,
            tags = setOf("Certificates"),
            summary = "Issue a certificate from a signing request",
        )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}
