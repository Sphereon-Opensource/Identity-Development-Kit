/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider.local

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.data.store.party.model.PartyRef
import com.sphereon.wallet.provider.RevocationReason
import com.sphereon.wallet.provider.WalletUnitStatus
import com.sphereon.wallet.wscd.WscdProfile
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Instant

/**
 * Minimal wallet-unit lifecycle record for [LocalWalletProvider].
 *
 * [com.sphereon.wallet.credential.WalletUnitStore] (`lib-wallet-public`/`lib-wallet-impl`) was
 * evaluated first and rejected as the persistence root here: it is a storage-profile REGISTRY
 * (`id`/`ownerSubjectRef`/`label`/`purpose`/`storageProfileId`/`defaultHolderKeyPolicyId`), not a
 * unit-lifecycle record - it has no `status` and no `walletInstanceId`, so it cannot represent
 * PROVISIONING/ACTIVE/SUSPENDED/REVOKED or the authoritative (unit, instance) pair
 * [LocalWalletProvider.issueInstanceAttestation] resolves the WIA `sub` against. This module
 * defines its own minimal record + store instead.
 */
data class WalletUnitRecord(
    val walletUnitId: String,
    val walletInstanceId: String,
    val profileId: String,
    val organizationUnitRef: PartyRef,
    val wscdProfile: WscdProfile,
    val status: WalletUnitStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revocationReason: RevocationReason? = null,
)

/** Session-scoped persistence root for [WalletUnitRecord]s provisioned by [LocalWalletProvider]. */
interface WalletUnitRecordStore {
    suspend fun put(record: WalletUnitRecord): IdkResult<WalletUnitRecord, IdkError>

    suspend fun get(walletUnitId: String): IdkResult<WalletUnitRecord?, IdkError>
}

/**
 * In-memory [WalletUnitRecordStore]: the OSS-local default, mirroring the precedent already set
 * by EDK's `InMemoryWalletUnitAttestationStore`
 * (`com.sphereon.wallet.unit.attestation.StoredWalletUnitAttestationServices`, EDK
 * `lib-wallet-unit`) - a plain session-scoped map, no external persistence dependency, no
 * `Mutex` (that existing precedent has none either). A durable (e.g. blob-backed) store can be
 * contributed later as a distinct [WalletUnitRecordStore] binding without touching
 * [LocalWalletProvider] itself.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletUnitRecordStore>())
class InMemoryWalletUnitRecordStore : WalletUnitRecordStore {
    private val records = mutableMapOf<String, WalletUnitRecord>()

    override suspend fun put(record: WalletUnitRecord): IdkResult<WalletUnitRecord, IdkError> {
        records[record.walletUnitId] = record
        return Ok(record)
    }

    override suspend fun get(walletUnitId: String): IdkResult<WalletUnitRecord?, IdkError> = Ok(records[walletUnitId])
}
