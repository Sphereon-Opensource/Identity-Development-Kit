/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.kms.rest.api.generated.models.CertificateAliasesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateBytesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateChainResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateResponse
import com.sphereon.crypto.kms.rest.api.generated.models.CertificateSigningRequestResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateCertificateSigningRequestRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateFromCsrRequest
import com.sphereon.crypto.kms.rest.api.generated.models.IssueCertificateRequest
import com.sphereon.crypto.kms.rest.api.generated.models.StoreCertificateChainRequest
import com.sphereon.crypto.kms.rest.api.generated.models.StoreCertificateRequest
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificatesRestService", exact = true)
interface CertificatesRestService {
    suspend fun generateCsr(request: GenerateCertificateSigningRequestRequest): CertificateSigningRequestResponse

    suspend fun issueCertificate(request: IssueCertificateRequest): CertificateResponse

    suspend fun issueCertificateFromCsr(request: IssueCertificateFromCsrRequest): CertificateResponse

    suspend fun listTrustedCertificateAliases(providerId: String? = null): CertificateAliasesResponse

    suspend fun getTrustedCertificate(
        alias: String,
        providerId: String? = null
    ): CertificateBytesResponse

    suspend fun storeTrustedCertificate(
        alias: String,
        request: StoreCertificateRequest,
        providerId: String? = null
    ): CertificateBytesResponse

    suspend fun deleteTrustedCertificate(
        alias: String,
        providerId: String? = null
    ): Boolean

    suspend fun listCertificateChainAliases(providerId: String? = null): CertificateAliasesResponse

    suspend fun getCertificateChain(
        alias: String,
        providerId: String? = null
    ): CertificateChainResponse

    suspend fun storeCertificateChain(
        alias: String,
        request: StoreCertificateChainRequest,
        providerId: String? = null
    ): CertificateChainResponse

    suspend fun deleteCertificateChain(
        alias: String,
        providerId: String? = null
    ): Boolean
}
