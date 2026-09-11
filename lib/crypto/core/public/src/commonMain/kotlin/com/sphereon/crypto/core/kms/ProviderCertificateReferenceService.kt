/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.kms

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.x509.Certificate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * The provider-native certificate identity to resolve.
 *
 * [alias] is the provider's logical certificate name. [id], when present, is an
 * immutable provider-native version identifier. The contract deliberately has no
 * credential, token, secret, or provider endpoint fields.
 */
@JsExportCompat
@Serializable
data class
ProviderCertificateLookup
    @JvmOverloads
    constructor(
        val alias: String,
        val id: String? = null,
    )

/**
 * Public material returned by a provider-native certificate read.
 *
 * A provider read yields a single leaf. No cloud KMS exposes the issuers above it as public
 * data, so chains are completed from the tenant's registered trusted certificates rather than
 * from the provider. The provider identity is the configured logical provider id, never a
 * backend URL or other locator.
 */
@JsExportCompat
@Serializable
data class ProviderCertificateReference(
    val providerId: String,
    val alias: String,
    val id: String,
    val certificate: Certificate,
) {
    /** The exact DER bytes of the leaf certificate. */
    val certificateDer: ByteArray
        get() = certificate.der

    /** Alias for persistence layers that call the provider-native id a certificate id. */
    val providerCertificateId: String
        get() = id
}

/**
 * Optional, read-only provider capability for externally managed certificates.
 *
 * Platform-managed certificate mutation remains owned by [CertificateStoreService].
 */
@JsExportCompat
interface ProviderCertificateReferenceService {
    suspend fun getCertificate(
        lookup: ProviderCertificateLookup,
    ): IdkResult<ProviderCertificateReference, IdkError>
}
