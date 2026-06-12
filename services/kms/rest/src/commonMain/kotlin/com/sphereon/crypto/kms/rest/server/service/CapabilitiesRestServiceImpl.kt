/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.kms.rest.api.generated.models.ListCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderQuery
import com.sphereon.crypto.kms.rest.api.generated.models.QueryBestProviderResponse
import com.sphereon.crypto.kms.rest.api.generated.models.QueryProvidersResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toRestBestQueryResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRestCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRestQueryResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRestResponse
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CapabilitiesRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("CapabilitiesRestServiceImpl", exact = true)
class CapabilitiesRestServiceImpl(
    private val kms: KeyManagerService,
) : CapabilitiesRestService {
    override suspend fun listCapabilities(includeDisabled: Boolean): ListCapabilitiesResponse {
        val result = kms.getAllCapabilities(includeDisabled)
        val capabilities =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to list KMS capabilities")
            }
        return capabilities.capabilities.values
            .map { it.toRestResponse() }
            .toTypedArray()
            .toRestCapabilitiesResponse()
    }

    override suspend fun getProviderCapabilities(providerId: String): ProviderCapabilitiesResponse = kms.getProviderById(providerId).getCapabilities().toRestResponse()

    override suspend fun queryProviders(query: ProviderQuery): QueryProvidersResponse {
        val result = kms.queryProviders(query.toSdk())
        val matches =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to query KMS providers")
            }
        return matches.matches.toRestQueryResponse(matches.totalProviders)
    }

    override suspend fun queryBestProvider(query: ProviderQuery): QueryBestProviderResponse {
        val result = kms.queryProvider(query.toSdk())
        val match =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to query best KMS provider")
            }
        return match.match.toRestBestQueryResponse()
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val capabilitiesRestService: CapabilitiesRestService
    }
}
