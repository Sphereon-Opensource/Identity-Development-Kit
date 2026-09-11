/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.wallet.credential

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.time.Instant

/**
 * Persistence root for wallet units and their storage profiles.
 *
 * Credential, issuance, refresh, and sync stores are routed from [StorageProfile], so this
 * registry is the durable policy root for local/blob, remote/vault, and hybrid wallet modes.
 */
interface WalletUnitStore : WalletStorageProfileResolver {
    suspend fun putWalletUnit(
        profile: WalletUnitProfile,
        storageProfile: StorageProfile,
    ): IdkResult<WalletUnitProfile, IdkError>

    suspend fun getWalletUnit(walletUnitId: String): IdkResult<WalletUnitProfile?, IdkError>

    /**
     * Atomically associates a freshly provisioned opaque WSCA key alias with its exact public
     * verification method. Identical re-registration is idempotent; conflicting metadata fails.
     */
    suspend fun registerHolderVerificationMethod(
        walletUnitId: String,
        keyAlias: String,
        method: WalletHolderVerificationMethod,
    ): IdkResult<WalletUnitProfile, IdkError>

    suspend fun getStorageProfile(walletUnitId: String): IdkResult<StorageProfile?, IdkError>

    suspend fun listWalletUnits(includeArchived: Boolean = false): IdkResult<List<WalletUnitProfile>, IdkError>

    suspend fun archiveWalletUnit(
        walletUnitId: String,
        archivedAt: Instant,
    ): IdkResult<WalletUnitProfile?, IdkError>
}
