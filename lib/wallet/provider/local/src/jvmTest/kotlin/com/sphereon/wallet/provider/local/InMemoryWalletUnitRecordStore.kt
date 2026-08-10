/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.provider.local

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError

/** JVM test double. Production local-wallet graphs bind a durable document-backed store. */
class InMemoryWalletUnitRecordStore : WalletUnitRecordStore {
    private val records = mutableMapOf<String, WalletUnitRecord>()

    override suspend fun put(record: WalletUnitRecord): IdkResult<WalletUnitRecord, IdkError> {
        records[record.walletUnitId] = record
        return Ok(record)
    }

    override suspend fun get(walletUnitId: String): IdkResult<WalletUnitRecord?, IdkError> = Ok(records[walletUnitId])
}
