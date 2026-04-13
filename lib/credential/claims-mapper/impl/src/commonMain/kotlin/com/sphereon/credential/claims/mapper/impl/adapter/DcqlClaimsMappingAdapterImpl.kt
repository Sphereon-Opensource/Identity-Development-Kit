/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.credential.claims.mapper.impl.adapter

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.adapter.DcqlClaimsMappingAdapter
import com.sphereon.credential.claims.mapper.api.error.ClaimMappingErrors
import com.sphereon.credential.claims.mapper.api.mapper.ClaimsMappingService
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.api.model.MappedClaimsResult
import com.sphereon.credential.claims.mapper.api.store.QueryConfigurationStore
import com.sphereon.credential.claims.mapper.impl.mapper.ClaimsMappingServiceImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [com.sphereon.credential.claims.mapper.api.adapter.DcqlClaimsMappingAdapter] that provides DCQL-specific
 * claim mapping functionality.
 *
 * This adapter uses a [QueryConfigurationStore] to look up configurations by
 * DCQL query ID, then delegates the actual mapping to a [ClaimsMappingService].
 *
 * @param mappingService The low-level mapping service to delegate to
 * @param dcqlStore The DCQL-aware store for looking up configurations by query ID
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DcqlClaimsMappingAdapter>())
class DcqlClaimsMappingAdapterImpl(
    private val mappingService: ClaimsMappingService,
    private val dcqlStore: QueryConfigurationStore,
) : DcqlClaimsMappingAdapter {
    override suspend fun mapClaimsByQueryId(
        credentials: List<CredentialWithId>,
        dcqlQueryId: String,
    ): IdkResult<MappedClaimsResult, IdkError> {
        val configResult = dcqlStore.findByQueryId(dcqlQueryId)
        if (configResult.isErr) {
            return Err(configResult.error).asResult()
        }

        val config =
            configResult.value
                ?: return Err(
                    ClaimMappingErrors.configurationNotFound("DCQL query: $dcqlQueryId"),
                ).asResult()

        return mappingService.mapClaimsWithConfig(credentials, config)
    }

    companion object {
        /**
         * Create a DcqlClaimsMappingAdapterImpl with default configuration.
         *
         * @param dcqlStore The DCQL-aware store for looking up configurations
         * @return A new instance with default resolvers
         */
        fun withDefaults(dcqlStore: QueryConfigurationStore): DcqlClaimsMappingAdapterImpl =
            DcqlClaimsMappingAdapterImpl(
                mappingService = ClaimsMappingServiceImpl.Companion.withDefaults(),
                dcqlStore = dcqlStore,
            )
    }
}
