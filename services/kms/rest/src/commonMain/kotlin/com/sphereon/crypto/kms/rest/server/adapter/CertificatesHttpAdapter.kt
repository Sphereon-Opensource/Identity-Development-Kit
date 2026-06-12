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
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class CertificatesHttpAdapter(
    private val certificatesService: CertificatesRestService,
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
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleGenerateCsr(req) }
            }
            post("/certificates/issue") {
                operationId("issueCertificate")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleIssueCertificate(req) }
            }
            post("/certificates/issue-from-csr") {
                operationId("issueCertificateFromCsr")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleIssueCertificateFromCsr(req) }
            }
            get("/certificates") {
                operationId("listTrustedCertificateAliases")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListTrustedCertificateAliases(req) }
            }
            get("/certificates/{alias}") {
                operationId("getTrustedCertificate")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetTrustedCertificate(req) }
            }
            post("/certificates/{alias}") {
                operationId("storeTrustedCertificate")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleStoreTrustedCertificate(req) }
            }
            delete("/certificates/{alias}") {
                operationId("deleteTrustedCertificate")
                handle { req -> handleDeleteTrustedCertificate(req) }
            }
            get("/certificate-chains") {
                operationId("listCertificateChainAliases")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListCertificateChainAliases(req) }
            }
            get("/certificate-chains/{alias}") {
                operationId("getCertificateChain")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetCertificateChain(req) }
            }
            post("/certificate-chains/{alias}") {
                operationId("storeCertificateChain")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleStoreCertificateChain(req) }
            }
            delete("/certificate-chains/{alias}") {
                operationId("deleteCertificateChain")
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

    private suspend fun handleListTrustedCertificateAliases(request: GenericHttpRequest): GenericHttpResponse =
        try {
            jsonResponse(200, json.encodeToString(certificatesService.listTrustedCertificateAliases(request.queryParams["providerId"])))
        } catch (expected: Exception) {
            errorResponse(expected)
        }

    private suspend fun handleGetTrustedCertificate(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificates/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return try {
            jsonResponse(200, json.encodeToString(certificatesService.getTrustedCertificate(alias, req.queryParams["providerId"])))
        } catch (expected: Exception) {
            errorResponse(expected)
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
            errorResponse(expected)
        }
    }

    private suspend fun handleListCertificateChainAliases(request: GenericHttpRequest): GenericHttpResponse =
        try {
            jsonResponse(200, json.encodeToString(certificatesService.listCertificateChainAliases(request.queryParams["providerId"])))
        } catch (expected: Exception) {
            errorResponse(expected)
        }

    private suspend fun handleGetCertificateChain(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/certificate-chains/{alias}")
        val alias = req.pathParams["alias"] ?: return errorResponse(400, "Missing path parameter: alias")
        return try {
            jsonResponse(200, json.encodeToString(certificatesService.getCertificateChain(alias, req.queryParams["providerId"])))
        } catch (expected: Exception) {
            errorResponse(expected)
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
            errorResponse(expected)
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
