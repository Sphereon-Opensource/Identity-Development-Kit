/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.certificate.persistence

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError

enum class CertificateReferenceHistoryCapability {
    UNSUPPORTED,
    DURABLE,
}

object CertificateReferenceStoreErrorCodes {
    const val PERSISTENCE_CONFLICT = "KMS_CERTIFICATE_REFERENCE_PERSISTENCE_CONFLICT"
    const val DURABLE_HISTORY_UNSUPPORTED = "KMS_CERTIFICATE_REFERENCE_DURABLE_HISTORY_UNSUPPORTED"
    const val AMBIGUOUS_REFERENCE = "KMS_CERTIFICATE_REFERENCE_AMBIGUOUS"
    const val STORE_UNAVAILABLE = "KMS_CERTIFICATE_REFERENCE_STORE_UNAVAILABLE"
    const val KEY_IDENTITY_MISMATCH = "KMS_EXTERNAL_CERTIFICATE_KEY_IDENTITY_MISMATCH"
    const val REGISTRATION_CONFLICT = "KMS_EXTERNAL_CERTIFICATE_REGISTRATION_CONFLICT"
    const val PROVIDER_DRIFT = "KMS_CERTIFICATE_REFERENCE_PROVIDER_DRIFT"
}

private fun <T> unsupportedHistoryResult(): IdkResult<T, IdkError> =
    Err(
        IdkError.fromString(
            code = CertificateReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
            message = "This certificate reference store does not provide durable ownership history",
        ),
    )

/** Tenant-scoped persistence for certificate references and public material. */
interface CertificateReferenceStore {
    /** False for [NoOpCertificateReferenceStore], which is used when persistence is absent. */
    val isAvailable: Boolean get() = true

    /** Whether active and soft-deleted rows can be used as durable ownership authority. */
    val ownershipHistoryCapability: CertificateReferenceHistoryCapability
        get() = CertificateReferenceHistoryCapability.UNSUPPORTED

    suspend fun save(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError>

    suspend fun upsert(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError>

    suspend fun findById(
        tenantId: String,
        id: String,
    ): IdkResult<CertificateReferenceRecord?, IdkError>

    suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ): IdkResult<CertificateReferenceRecord?, IdkError>

    /** Find every alias match, including soft-deleted rows. */
    suspend fun findAllByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String? = null,
        kind: CertificateReferenceKind,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = unsupportedHistoryResult()

    /** Find the latest active row or, when none is active, the latest soft-deleted owner. */
    suspend fun findLatestByAliasIncludingDeleted(
        tenantId: String,
        alias: String,
        providerId: String? = null,
        kind: CertificateReferenceKind,
    ): IdkResult<CertificateReferenceRecord?, IdkError> = unsupportedHistoryResult()

    suspend fun findByProviderCertificateId(
        tenantId: String,
        providerId: String,
        providerCertificateId: String,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError>

    suspend fun findByLinkedKeyReferenceId(
        tenantId: String,
        linkedKeyReferenceId: String,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError>

    suspend fun findAll(
        tenantId: String,
        providerId: String? = null,
        kind: CertificateReferenceKind? = null,
        source: CertificateReferenceSource? = null,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError>

    suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ): IdkResult<Boolean, IdkError>

    suspend fun deleteById(
        tenantId: String,
        id: String,
    ): IdkResult<Boolean, IdkError>
}
