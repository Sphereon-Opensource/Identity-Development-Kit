/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.crypto.kms.rest.api.command.GetCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListCertificateReferencesServiceCommand
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateChainResponse
import com.sphereon.crypto.kms.rest.api.generated.models.StoreCertificateChainRequest
import com.sphereon.crypto.kms.rest.server.service.CertificateReferenceResolutionException
import com.sphereon.crypto.kms.rest.server.service.CertificatesRestService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificateChainStoreConflictTest {
    @Test
    fun chainForAnAliasTheProviderAlreadyHoldsIsAConflictNotAServerError() =
        runTest {
            val response = adapter().handleResolvedRequest(request(), route())

            assertEquals(409, response.statusCode)
            assertTrue(response.body.orEmpty().contains("KMS_CERTIFICATE_REFERENCE_MANAGED_STORE_CONFLICT"))
        }

    private fun adapter() =
        CertificatesHttpAdapter(
            certificatesService = conflictingService(),
            registerCommand = unused(RegisterCertificateReferenceServiceCommand::class.java),
            listCertificateReferencesCommand = unused(ListCertificateReferencesServiceCommand::class.java),
            getCertificateReferenceCommand = unused(GetCertificateReferenceServiceCommand::class.java),
        )

    private fun request() =
        GenericHttpRequest.withTextBody(
            method = "POST",
            path = "/certificate-chains/generated-key",
            body = """{"certificates":["MA=="]}""",
        )

    private fun route() =
        HttpAdapterRouteMatch(
            adapterId = CertificatesHttpAdapter.ID,
            method = "POST",
            originalPath = "/certificate-chains/generated-key",
            normalizedPath = "/certificate-chains/generated-key",
            matchedPathPattern = "/certificate-chains/{alias}",
            handlerCommandId = "kms.certificate-chains.store",
            tenantIdFromPath = null,
        )

    /** A Kotlin override, not a proxy method, so the checked exception reaches the adapter unwrapped. */
    private fun conflictingService(): CertificatesRestService =
        object : CertificatesRestService by unused(CertificatesRestService::class.java) {
            override suspend fun storeCertificateChain(
                alias: String,
                request: StoreCertificateChainRequest,
                providerId: String?,
            ): CertificateChainResponse =
                throw CertificateReferenceResolutionException(
                    "KMS_CERTIFICATE_REFERENCE_MANAGED_STORE_CONFLICT",
                    "The certificate alias already exists at the provider",
                )
        }

    private fun <T> unused(type: Class<T>): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                error("${type.simpleName}.${method.name} must not be called")
            },
        )
}
