/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.response.createdResponse
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.http.response.noContentResponse
import com.sphereon.core.api.http.response.ResponseBuilder
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreErrorCodes
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.kms.rest.api.command.GetCertificateReferenceInput
import com.sphereon.crypto.kms.rest.api.command.GetCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListCertificateReferencesInput
import com.sphereon.crypto.kms.rest.api.command.ListCertificateReferencesServiceCommand
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceInput
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateBytesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateChainResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateSigningRequestResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateCertificateSigningRequestRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateFromCsrRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateRequest
import com.sphereon.crypto.kms.rest.api.generated.models.StoreCertificateChainRequest
import com.sphereon.crypto.kms.rest.api.generated.models.StoreCertificateRequest
import com.sphereon.crypto.kms.rest.server.service.CertificatesRestService
import com.sphereon.crypto.kms.rest.server.service.CertificateReferenceResolutionException
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(CertificatesHttpAdapter.ID)
class CertificatesHttpAdapter(
    private val certificatesService: CertificatesRestService,
    private val registerCommand: RegisterCertificateReferenceServiceCommand,
    private val listCertificateReferencesCommand: ListCertificateReferencesServiceCommand,
    private val getCertificateReferenceCommand: GetCertificateReferenceServiceCommand,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-CERTIFICATES"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "")

    override val routes =
        httpRoutes {
            post("/certificates/csr") {
                operationId("generateCertificateSigningRequest")
                handlerCommandId("kms.certificates.csr")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleGenerateCsr(req) }
            }
            post("/certificates/issue") {
                operationId("issueCertificate")
                handlerCommandId("kms.certificates.issue")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleIssueCertificate(req) }
            }
            post("/certificates/issue-from-csr") {
                operationId("issueCertificateFromCsr")
                handlerCommandId("kms.certificates.issue-from-csr")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleIssueCertificateFromCsr(req) }
            }
            post("/certificates/register") {
                operationId("registerCertificateReference")
                handlerCommandId(RegisterCertificateReferenceServiceCommand.COMMAND_ID)
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleRegisterCertificateReference(req) }
            }
            get("/certificate-references") {
                operationId("listCertificateReferences")
                handlerCommandId(ListCertificateReferencesServiceCommand.COMMAND_ID)
                produces(MediaType.ApplicationJson)
                handle { req -> handleListCertificateReferences(req) }
            }
            get("/certificate-references/{id}") {
                operationId("getCertificateReference")
                handlerCommandId(GetCertificateReferenceServiceCommand.COMMAND_ID)
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetCertificateReference(req) }
            }
            get("/certificates") {
                operationId("listTrustedCertificateAliases")
                handlerCommandId("kms.certificates.list")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListTrustedCertificateAliases(req) }
            }
            get("/certificates/{alias}") {
                operationId("getTrustedCertificate")
                handlerCommandId("kms.certificates.get")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetTrustedCertificate(req) }
            }
            post("/certificates/{alias}") {
                operationId("storeTrustedCertificate")
                handlerCommandId("kms.certificates.store")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleStoreTrustedCertificate(req) }
            }
            delete("/certificates/{alias}") {
                operationId("deleteTrustedCertificate")
                handlerCommandId("kms.certificates.delete")
                handle { req -> handleDeleteTrustedCertificate(req) }
            }
            get("/certificate-chains") {
                operationId("listCertificateChainAliases")
                handlerCommandId("kms.certificate-chains.list")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListCertificateChainAliases(req) }
            }
            get("/certificate-chains/{alias}") {
                operationId("getCertificateChain")
                handlerCommandId("kms.certificate-chains.get")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetCertificateChain(req) }
            }
            post("/certificate-chains/{alias}") {
                operationId("storeCertificateChain")
                handlerCommandId("kms.certificate-chains.store")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleStoreCertificateChain(req) }
            }
            delete("/certificate-chains/{alias}") {
                operationId("deleteCertificateChain")
                handlerCommandId("kms.certificate-chains.delete")
                handle { req -> handleDeleteCertificateChain(req) }
            }
        }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val certificatesHttpAdapter: CertificatesHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleGenerateCsr(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<GenerateCertificateSigningRequestRequest, CertificateSigningRequestResponse>(request) { certificatesService.generateCsr(it) }

    private suspend fun handleIssueCertificate(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<IssueCertificateRequest, CertificateResponse>(request) { certificatesService.issueCertificate(it) }

    private suspend fun handleIssueCertificateFromCsr(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<IssueCertificateFromCsrRequest, CertificateResponse>(request) { certificatesService.issueCertificateFromCsr(it) }

    private suspend fun handleRegisterCertificateReference(request: GenericHttpRequest): GenericHttpResponse {
        val body = request.body ?: return errorResponse(400, "Missing request body")
        val input = try {
            json.decodeFromString<RegisterCertificateReferenceInput>(body)
        } catch (_: Exception) {
            return errorResponse(400, "Invalid request body")
        }
        val response = registerCommand.execute(input).getOrElse { error ->
            return errorResponse(certificateReferenceRegistrationHttpStatus(error), "Certificate reference registration failed")
        }
        return createdResponse("/certificates/${response.alias}", json.encodeToString(response))
    }

    private suspend fun handleListCertificateReferences(request: GenericHttpRequest): GenericHttpResponse {
        val input = try {
            ListCertificateReferencesInput(
                providerId = request.queryParams["providerId"],
                kind = request.queryParams["kind"]?.let(CertificateReferenceKind::fromStorageValue),
                source = request.queryParams["source"]?.let(CertificateReferenceSource::fromStorageValue),
            )
        } catch (_: IllegalStateException) {
            return errorResponse(400, "Invalid certificate reference filter")
        }
        val response = listCertificateReferencesCommand.execute(input).getOrElse { error ->
            return errorResponse(certificateReferenceRegistrationHttpStatus(error), "Certificate reference query failed")
        }
        return jsonResponse(200, json.encodeToString(response))
    }

    private suspend fun handleGetCertificateReference(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificate-references/{id}")
        val id = req.pathParams["id"] ?: return errorResponse(400, "Missing path parameter: id")
        val response = getCertificateReferenceCommand.execute(GetCertificateReferenceInput(id)).getOrElse { error ->
            return errorResponse(certificateReferenceRegistrationHttpStatus(error), "Certificate reference query failed")
        }
        return jsonResponse(200, json.encodeToString(response))
    }

    private suspend fun handleListTrustedCertificateAliases(request: GenericHttpRequest): GenericHttpResponse =
        try {
            jsonResponse(200, json.encodeToString(certificatesService.listTrustedCertificateAliases(request.queryParams["providerId"])))
        } catch (expected: Exception) {
            certificateReferenceErrorResponse(expected)
        }

    private suspend fun handleGetTrustedCertificate(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificates/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return try {
            jsonResponse(200, json.encodeToString(certificatesService.getTrustedCertificate(alias, req.queryParams["providerId"])))
        } catch (expected: Exception) {
            certificateReferenceErrorResponse(expected)
        }
    }

    private suspend fun handleStoreTrustedCertificate(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificates/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return handleBody<StoreCertificateRequest, CertificateBytesResponse>(req) {
            certificatesService.storeTrustedCertificate(alias, it, req.queryParams["providerId"])
        }.withCreatedLocation("/certificates/$alias")
    }

    private suspend fun handleDeleteTrustedCertificate(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificates/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return try {
            certificatesService.deleteTrustedCertificate(alias, req.queryParams["providerId"])
            noContentResponse()
        } catch (expected: Exception) {
            certificateReferenceErrorResponse(expected)
        }
    }

    private suspend fun handleListCertificateChainAliases(request: GenericHttpRequest): GenericHttpResponse =
        try {
            jsonResponse(200, json.encodeToString(certificatesService.listCertificateChainAliases(request.queryParams["providerId"])))
        } catch (expected: Exception) {
            certificateReferenceErrorResponse(expected)
        }

    private suspend fun handleGetCertificateChain(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificate-chains/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return try {
            jsonResponse(200, json.encodeToString(certificatesService.getCertificateChain(alias, req.queryParams["providerId"])))
        } catch (expected: Exception) {
            certificateReferenceErrorResponse(expected)
        }
    }

    private suspend fun handleStoreCertificateChain(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificate-chains/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return handleBody<StoreCertificateChainRequest, CertificateChainResponse>(req) {
            certificatesService.storeCertificateChain(alias, it, req.queryParams["providerId"])
        }.withCreatedLocation("/certificate-chains/$alias")
    }

    private suspend fun handleDeleteCertificateChain(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificate-chains/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return try {
            certificatesService.deleteCertificateChain(alias, req.queryParams["providerId"])
            noContentResponse()
        } catch (expected: Exception) {
            certificateReferenceErrorResponse(expected)
        }
    }

    private suspend inline fun <reified T, reified O> handleBody(
        request: GenericHttpRequest,
        crossinline handler: suspend (T) -> O,
    ): GenericHttpResponse {
        val body = request.body ?: return errorResponse(400, "Missing request body")
        val input =
            try {
                json.decodeFromString<T>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }
        return try {
            jsonResponse(200, json.encodeToString(handler(input)))
        } catch (expected: Exception) {
            errorResponse(expected)
        }
    }

    private fun GenericHttpResponse.withCreatedLocation(location: String): GenericHttpResponse {
        if (statusCode != 200) return this
        return createdResponse(location, body ?: "")
    }
}

internal fun certificateReferenceRegistrationHttpStatus(error: IdkError): Int =
    when (error.code) {
        "ILLEGAL_ARGUMENT_ERROR" -> 400
        "NOT_FOUND_ERROR", "KMS_PROVIDER_NOT_FOUND", "KMS_EXTERNAL_KEY_NOT_FOUND" -> 404
        CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
        CertificateReferenceStoreErrorCodes.REGISTRATION_CONFLICT,
        CertificateReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
        CertificateReferenceStoreErrorCodes.STORE_UNAVAILABLE,
        "KMS_PROVIDER_CERTIFICATE_REFERENCE_UNSUPPORTED",
        "KMS_PROVIDER_CERTIFICATE_IDENTITY_MISMATCH",
        -> 409
        else -> 500
    }

internal fun certificateReferenceErrorResponse(error: Throwable): GenericHttpResponse =
    if (error is CertificateReferenceResolutionException) {
        ResponseBuilder.error(
            statusCode = certificateReferenceRegistrationHttpStatus(IdkError.fromString(code = error.code, message = error.message ?: "")),
            code = error.code,
            message = error.message ?: "Certificate reference operation failed",
        )
    } else {
        errorResponse(error)
    }
