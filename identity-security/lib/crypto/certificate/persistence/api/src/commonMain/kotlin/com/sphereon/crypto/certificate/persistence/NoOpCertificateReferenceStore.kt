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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Default binding used when no certificate persistence dialect is present. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CertificateReferenceStore>())
class NoOpCertificateReferenceStore : CertificateReferenceStore {
    override val isAvailable: Boolean = false
    override val ownershipHistoryCapability: CertificateReferenceHistoryCapability =
        CertificateReferenceHistoryCapability.UNSUPPORTED

    override suspend fun save(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError> = Ok(record)

    override suspend fun upsert(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError> = Ok(record)

    override suspend fun findById(tenantId: String, id: String): IdkResult<CertificateReferenceRecord?, IdkError> = Ok(null)

    override suspend fun findByAlias(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ): IdkResult<CertificateReferenceRecord?, IdkError> = Ok(null)

    override suspend fun findByProviderCertificateId(
        tenantId: String,
        providerId: String,
        providerCertificateId: String,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = Ok(emptyList())

    override suspend fun findByLinkedKeyReferenceId(
        tenantId: String,
        linkedKeyReferenceId: String,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = Ok(emptyList())

    override suspend fun findAll(
        tenantId: String,
        providerId: String?,
        kind: CertificateReferenceKind?,
        source: CertificateReferenceSource?,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> = Ok(emptyList())

    override suspend fun delete(
        tenantId: String,
        alias: String,
        providerId: String,
        kind: CertificateReferenceKind,
    ): IdkResult<Boolean, IdkError> = Ok(false)

    override suspend fun deleteById(tenantId: String, id: String): IdkResult<Boolean, IdkError> = Ok(false)
}
