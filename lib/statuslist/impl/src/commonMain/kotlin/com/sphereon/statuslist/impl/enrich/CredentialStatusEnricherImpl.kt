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

package com.sphereon.statuslist.impl.enrich

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.StatusListDefinitionsProvider
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.spi.CredentialStatusEnricher
import com.sphereon.statuslist.spi.ReservedStatus
import com.sphereon.statuslist.spi.StatusClaimMergeTarget
import com.sphereon.statuslist.spi.StatusEnrichmentContext
import com.sphereon.statuslist.spi.StatusListDriver
import com.sphereon.statuslist.spi.StatusReservationHandle
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Default [CredentialStatusEnricher]: pre-sign, allocates an entry (random-unused) in the
 * configured list and returns the format-correct status claim; post-sign, binds the entry to the
 * issued credential. Token Status List → `status.status_list`; W3C → `credentialStatus`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialStatusEnricher>())
class CredentialStatusEnricherImpl(
    private val driver: StatusListDriver,
    private val definitionsProvider: Provider<StatusListDefinitionsProvider>? = null,
) : CredentialStatusEnricher {
    override suspend fun reserve(context: StatusEnrichmentContext): IdkResult<ReservedStatus, IdkError> {
        val listRef = StatusListRef(correlationId = context.statusListCorrelationId)
        definitionsProvider
            ?.invoke()
            ?.byId(context.statusListCorrelationId)
            ?.let { definition -> driver.refreshStatusListDefinition(definition).getOrElse { return Err(it) } }
        val list =
            driver.getStatusList(listRef).getOrElse { return Err(it) }
                ?: return Err(StatusListErrors.listNotFound(context.statusListCorrelationId))

        val entry =
            driver
                .allocateEntry(
                    AllocateEntryArgs(
                        statusList = listRef,
                        purpose = context.purposes.firstOrNull() ?: StatusPurpose.REVOCATION,
                        entryCorrelationId = context.entryCorrelationId,
                        credentialId = context.credentialId,
                    ),
                ).getOrElse { return Err(it) }

        val uri = list.statusListUri
        val index = entry.statusListIndex
        val purpose = (context.purposes.firstOrNull() ?: StatusPurpose.REVOCATION).value

        val (claim, mergeTarget) =
            when (context.spec) {
                StatusListSpec.TOKEN_STATUS_LIST -> {
                    val claim =
                        buildJsonObject {
                            putJsonObject("status_list") {
                                put("idx", index)
                                put("uri", uri)
                            }
                        }
                    val target =
                        if (context.format.contains("mdoc", ignoreCase = true)) {
                            StatusClaimMergeTarget.MDOC_STATUS
                        } else {
                            StatusClaimMergeTarget.TOP_LEVEL_STATUS
                        }
                    claim to target
                }

                StatusListSpec.BITSTRING_STATUS_LIST -> {
                    val claim =
                        buildJsonObject {
                            put("id", "$uri#$index")
                            put("type", "BitstringStatusListEntry")
                            put("statusPurpose", purpose)
                            put("statusListIndex", index.toString())
                            put("statusListCredential", uri)
                        }
                    claim to StatusClaimMergeTarget.VC_CREDENTIAL_STATUS
                }
            }

        return Ok(
            ReservedStatus(
                handle = StatusReservationHandle(statusListId = entry.statusListId, statusListIndex = index),
                claim = claim,
                mergeTarget = mergeTarget,
            ),
        )
    }

    override suspend fun bind(
        handle: StatusReservationHandle,
        credentialId: String?,
        credentialHash: String?,
    ): IdkResult<Unit, IdkError> {
        val ref = EntryRef(statusListId = handle.statusListId, statusListIndex = handle.statusListIndex)
        driver.bindCredential(ref, credentialId, credentialHash).getOrElse { return Err(it) }
        return Ok(Unit)
    }
}
