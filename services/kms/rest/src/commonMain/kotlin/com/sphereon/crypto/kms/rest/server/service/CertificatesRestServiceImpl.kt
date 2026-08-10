/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:Suppress("UNCHECKED_CAST")

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.CertificateStoreService
import com.sphereon.crypto.core.kms.HasKeyStoreService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.certificateChainFromDer
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
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
import com.sphereon.crypto.kms.rest.api.generated.models.X509DistinguishedName
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CertificatesRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificatesRestServiceImpl", exact = true)
class CertificatesRestServiceImpl(
    private val kms: KeyManagerService,
    private val certificateService: CertificateService,
) : CertificatesRestService {
    override suspend fun generateCsr(request: GenerateCertificateSigningRequestRequest): CertificateSigningRequestResponse {
        val csr =
            certificateService.generateCSR(
                subjectKeyInfo = request.subjectKeyInfo.toSdk(),
                distinguishedNameElements = request.subject.toSdk(),
                serialNumber = request.serialNumber ?: 1,
            )
        return csr.toRestResponse()
    }

    override suspend fun issueCertificate(request: IssueCertificateRequest): CertificateResponse {
        val result =
            certificateService.createCertificate(
                issuerKeyInfo = request.issuerKeyInfo.toSdk() as KeyInfoType<KeyType>,
                issuer = request.issuer.toSdk(),
                subjectKeyInfo = request.subjectKeyInfo.toSdk() as ResolvedKeyInfoType<KeyType>,
                subject = request.subject.toSdk(),
                serialNumber = request.serialNumber,
                notBefore = request.notBefore?.let { LocalDateTimeKMP.fromString(it) } ?: LocalDateTimeKMP.now(),
                notAfter = request.notAfter?.let { LocalDateTimeKMP.fromString(it) } ?: defaultNotAfter(),
            )
        return CertificateResponse(
            certificate = Base64ByteArray(result.certificate.der),
            keyInfo = result.keyInfo.toRest(),
        )
    }

    override suspend fun issueCertificateFromCsr(request: IssueCertificateFromCsrRequest): CertificateResponse {
        val csr = request.csr.toSdk()
        val result =
            certificateService.createCertificateFromCSR(
                issuerKeyInfo = request.issuerKeyInfo.toSdk() as KeyInfoType<KeyType>,
                issuer = request.issuer.toSdk(),
                subjectKeyInfo = request.subjectKeyInfo.toSdk() as ResolvedKeyInfoType<KeyType>,
                csr = csr,
                serialNumber = request.serialNumber ?: csr.serialNumber,
                notBefore = request.notBefore?.let { LocalDateTimeKMP.fromString(it) } ?: LocalDateTimeKMP.now(),
                notAfter = request.notAfter?.let { LocalDateTimeKMP.fromString(it) } ?: defaultNotAfter(),
            )
        return CertificateResponse(
            certificate = Base64ByteArray(result.certificate.der),
            keyInfo = result.keyInfo.toRest(),
        )
    }

    override suspend fun listTrustedCertificateAliases(providerId: String?): CertificateAliasesResponse = CertificateAliasesResponse(aliases = certificateStore(providerId).listCertificateAliases())

    override suspend fun getTrustedCertificate(
        alias: String,
        providerId: String?
    ): CertificateBytesResponse = CertificateBytesResponse(certificate = Base64ByteArray(certificateStore(providerId).getCertificate(alias).der))

    override suspend fun storeTrustedCertificate(
        alias: String,
        request: StoreCertificateRequest,
        providerId: String?,
    ): CertificateBytesResponse {
        val cert = certificateFromDer(request.certificate.value)
        certificateStore(providerId).storeTrustedCertificate(alias, cert)
        return CertificateBytesResponse(certificate = Base64ByteArray(cert.der))
    }

    override suspend fun deleteTrustedCertificate(
        alias: String,
        providerId: String?
    ): Boolean = certificateStore(providerId).deleteCertificate(alias)

    override suspend fun listCertificateChainAliases(providerId: String?): CertificateAliasesResponse = CertificateAliasesResponse(aliases = certificateStore(providerId).listCertificateChainAliases())

    override suspend fun getCertificateChain(
        alias: String,
        providerId: String?
    ): CertificateChainResponse =
        CertificateChainResponse(
            certificates = certificateStore(providerId).getCertificateChain(alias).map { Base64ByteArray(it.der) }.toTypedArray(),
        )

    override suspend fun storeCertificateChain(
        alias: String,
        request: StoreCertificateChainRequest,
        providerId: String?,
    ): CertificateChainResponse {
        val chain = certificateChainFromDer(request.certificates.map { it.value }.toTypedArray())
        certificateStore(providerId).storeCertificateChain(alias, chain, request.keyInfo?.toSdk())
        return CertificateChainResponse(certificates = chain.map { Base64ByteArray(it.der) }.toTypedArray())
    }

    override suspend fun deleteCertificateChain(
        alias: String,
        providerId: String?
    ): Boolean = certificateStore(providerId).deleteCertificateChain(alias)

    private suspend fun certificateStore(providerId: String?): CertificateStoreService {
        val target: Any =
            if (providerId == null) {
                kms.keyStore
            } else {
                kms.getProviderById(providerId)
            }
        return target as? CertificateStoreService
            ?: (target as? HasKeyStoreService)?.keyStore as? CertificateStoreService
            ?: throw IllegalArgumentException(
                "Unsupported operation: ${providerId?.let { "provider '$it'" } ?: "default key store"} does not support certificate store operations.",
            )
    }

    private fun X509DistinguishedName.toSdk(): X509DistinguishedNameElements =
        X509DistinguishedNameElements(
            commonName = commonName ?: throw IllegalArgumentException("commonName is required"),
            country = country,
            state = state,
            locality = locality,
            organizationName = organizationName,
            organizationUnit = organizationUnit,
            email = email,
        )

    private fun CertificateSigningRequest.toRestResponse(): CertificateSigningRequestResponse =
        CertificateSigningRequestResponse(
            der = Base64ByteArray(der),
            commonName = commonName,
            organization = organization,
            organizationalUnit = organizationalUnit,
            locality = locality,
            state = state,
            country = country,
            email = email,
            serialNumber = serialNumber,
        )

    private fun com.sphereon.crypto.kms.rest.api.generated.models.CertificateSigningRequest.toSdk(): CertificateSigningRequest =
        CertificateSigningRequest(
            commonName = commonName,
            organization = organization,
            organizationalUnit = organizationalUnit,
            locality = locality,
            state = state,
            country = country,
            email = email,
            serialNumber = serialNumber,
            der = der.value,
        )

    private fun defaultNotAfter(): LocalDateTimeKMP =
        LocalDateTimeKMP.fromString(
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.UTC)
                .date
                .plus(365, DateTimeUnit.DAY)
                .atTime(0, 0)
                .toString(),
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val certificatesRestService: CertificatesRestService
    }
}
