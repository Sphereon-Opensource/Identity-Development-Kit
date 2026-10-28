/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.CertificateResult
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.X509CertificateExtensionSpec
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Typed boundary for issuing certificates after the REST command has authorized its caller. */
interface CertificateIssuanceExecutor {
    suspend fun issue(request: CertificateIssuanceRequest): CertificateResult

    suspend fun issueFromCsr(request: CertificateCsrIssuanceRequest): CertificateResult
}

data class CertificateIssuanceRequest(
    val issuerKeyInfo: KeyInfoType<KeyType>,
    val issuer: X509DistinguishedNameElements,
    val subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
    val subject: X509DistinguishedNameElements,
    val serialNumber: Int,
    val extensions: List<X509CertificateExtensionSpec>,
    val notBefore: LocalDateTimeKMP,
    val notAfter: LocalDateTimeKMP,
)

data class CertificateCsrIssuanceRequest(
    val issuerKeyInfo: KeyInfoType<KeyType>,
    val issuer: X509DistinguishedNameElements,
    val subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
    val csr: CertificateSigningRequest,
    val serialNumber: Int,
    val notBefore: LocalDateTimeKMP,
    val notAfter: LocalDateTimeKMP,
)

/** Default IDK behavior remains local to the current authenticated session. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CertificateIssuanceExecutor>())
class SessionCertificateIssuanceExecutor(
    private val certificateService: CertificateService,
) : CertificateIssuanceExecutor {
    override suspend fun issue(request: CertificateIssuanceRequest): CertificateResult =
        certificateService.createCertificate(
            issuerKeyInfo = request.issuerKeyInfo,
            issuer = request.issuer,
            subjectKeyInfo = request.subjectKeyInfo,
            subject = request.subject,
            serialNumber = request.serialNumber,
            extensions = request.extensions,
            notBefore = request.notBefore,
            notAfter = request.notAfter,
        )

    override suspend fun issueFromCsr(request: CertificateCsrIssuanceRequest): CertificateResult =
        certificateService.createCertificateFromCSR(
            issuerKeyInfo = request.issuerKeyInfo,
            issuer = request.issuer,
            subjectKeyInfo = request.subjectKeyInfo,
            csr = request.csr,
            serialNumber = request.serialNumber,
            notBefore = request.notBefore,
            notAfter = request.notAfter,
        )
}
