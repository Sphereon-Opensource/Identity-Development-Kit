/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueResult
import com.sphereon.wallet.unit.attestation.WalletInstanceAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.WalletInstanceAttestationIssueResult

/**
 * Wallet Provider port (CIR (EU) 2024/2981 Art. 2: "a natural or legal person who
 * provides wallet solutions"). OSS: LocalWalletProvider self-signs attestations with
 * honest custody claims. Commercial: remote provider backed by the wallet-unit
 * lifecycle services. Status-list references in results are OPTIONAL seams for the
 * production WIA/KA status-publishing path; this port's implementations currently leave them unset.
 */
interface WalletProvider {
    suspend fun provisionUnit(request: UnitProvisioningRequest): IdkResult<WalletUnitDescriptor, IdkError>

    suspend fun issueInstanceAttestation(request: WalletInstanceAttestationIssueRequest): IdkResult<WalletInstanceAttestationIssueResult, IdkError>

    suspend fun issueKeyAttestation(request: KeyAttestationIssueRequest): IdkResult<KeyAttestationIssueResult, IdkError>

    suspend fun unitStatus(walletUnitId: String): IdkResult<WalletUnitStatus, IdkError>

    suspend fun revokeUnit(
        walletUnitId: String,
        reason: RevocationReason
    ): IdkResult<Unit, IdkError>
}
