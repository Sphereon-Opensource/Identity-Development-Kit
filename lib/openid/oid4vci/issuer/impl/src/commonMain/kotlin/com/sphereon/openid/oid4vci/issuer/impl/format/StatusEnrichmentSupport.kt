/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.spi.CredentialStatusEnricher
import com.sphereon.statuslist.spi.ReservedStatus
import com.sphereon.statuslist.spi.StatusEnrichmentContext

/**
 * Shared OID4VCI status-list enrichment used by the format handlers. When the issuance carries a
 * [IssuanceContext.statusListBinding], this reserves a status entry (allocating a random-unused
 * index) and returns the [ReservedStatus] whose claim the handler merges into the credential
 * **before signing**. Returns `Ok(null)` only when the issuance carries no binding, leaving
 * issuance unchanged.
 *
 * Fails closed: a credential configuration bound to a status list must never issue without its
 * status claim — a credential issued without one can never be revoked. So a present binding with
 * no [CredentialStatusEnricher] wired (status-list integration missing from the deployment) is an
 * error, as is any reservation failure (list not found, allocation or signing failure).
 */
internal suspend fun reserveCredentialStatus(
    enricher: CredentialStatusEnricher?,
    context: IssuanceContext,
): IdkResult<ReservedStatus?, IdkError> {
    val binding = context.statusListBinding ?: return Ok(null)
    val active = enricher ?: return Err(StatusListErrors.enricherUnavailable(context.credentialConfigurationId))
    return active.reserve(
        StatusEnrichmentContext(
            credentialConfigurationId = context.credentialConfigurationId,
            format = context.credentialConfiguration.format,
            spec = binding.spec,
            purposes = binding.purposes,
            statusListCorrelationId = binding.statusListCorrelationId,
            // Tag the entry with the holder so the issuer can later revoke without tracking the
            // bit index (e.g. RevokeCredentialStatusArgs(EntryRef(entryCorrelationId = <subject>))).
            entryCorrelationId = context.subject,
        ),
    )
}

/**
 * Fail-closed guard for format handlers without status enrichment support. A credential
 * configuration bound to a status list must never issue without its status claim, so a handler
 * that cannot produce one returns the error instead of issuing a credential that could never be
 * revoked. Returns null when the issuance carries no binding.
 */
internal fun unsupportedStatusListBinding(context: IssuanceContext): IdkError? =
    context.statusListBinding?.let {
        StatusListErrors.enrichmentUnsupportedForFormat(
            credentialConfigurationId = context.credentialConfigurationId,
            format = context.credentialConfiguration.format,
        )
    }
