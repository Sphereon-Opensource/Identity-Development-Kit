package com.sphereon.credential.claims.mapper.impl.adapter

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.error.ClaimMappingErrors
import com.sphereon.credential.claims.mapper.api.mapper.ClaimsMappingService
import com.sphereon.credential.claims.mapper.api.store.QueryConfigurationStore
import com.sphereon.credential.claims.mapper.api.adapter.DcqlClaimsMappingAdapter
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.api.model.MappedClaimsResult
import com.sphereon.credential.claims.mapper.impl.mapper.ClaimsMappingServiceImpl
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

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
    private val dcqlStore: QueryConfigurationStore
) : DcqlClaimsMappingAdapter {

    override suspend fun mapClaimsByQueryId(
        credentials: List<CredentialWithId>,
        dcqlQueryId: String
    ): IdkResult<MappedClaimsResult, IdkError> {
        val configResult = dcqlStore.findByQueryId(dcqlQueryId)
        if (configResult.isErr) {
            return Err(configResult.error).asResult()
        }

        val config = configResult.value
            ?: return Err(
                ClaimMappingErrors.configurationNotFound("DCQL query: $dcqlQueryId")
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
        fun withDefaults(dcqlStore: QueryConfigurationStore): DcqlClaimsMappingAdapterImpl {
            return DcqlClaimsMappingAdapterImpl(
                mappingService = ClaimsMappingServiceImpl.Companion.withDefaults(),
                dcqlStore = dcqlStore
            )
        }
    }
}
