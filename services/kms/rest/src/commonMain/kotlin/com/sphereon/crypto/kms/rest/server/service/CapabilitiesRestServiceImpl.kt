/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.ProviderMatch
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
    private val exposure: KmsProviderExposure,
) : CapabilitiesRestService {
    override suspend fun listCapabilities(includeDisabled: Boolean): ListCapabilitiesResponse {
        val result = kms.getAllCapabilities(includeDisabled)
        val capabilities =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to list KMS capabilities")
            }
        return capabilities.capabilities
            .filterKeys(exposure::isPublic)
            .values
            .map { it.toRestResponse() }
            .toTypedArray()
            .toRestCapabilitiesResponse()
    }

    override suspend fun getProviderCapabilities(providerId: String): ProviderCapabilitiesResponse =
        runCatching {
            if (!exposure.isPublic(providerId) || providerId !in kms.getProviderIds()) {
                throw providerNotFound(providerId)
            }
            kms.getProviderById(providerId).getCapabilities().toRestResponse()
        }.getOrElse { exception ->
            when (exception) {
                is PKIException -> throw providerNotFound(providerId)
                else -> throw exception
            }
        }

    override suspend fun queryProviders(query: ProviderQuery): QueryProvidersResponse {
        val result = kms.queryProviders(query.toSdk())
        val matches =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to query KMS providers")
            }
        val visibleMatches = matches.matches.filter { exposure.isPublic(it.providerId) }.toTypedArray()
        return visibleMatches.toRestQueryResponse(exposure.visibleProviderCount(kms.getProviderIds()))
    }

    override suspend fun queryBestProvider(query: ProviderQuery): QueryBestProviderResponse {
        val result = kms.queryProvider(query.toSdk())
        val match =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to query best KMS provider")
            }
        val visibleMatch = match.match?.takeIf { exposure.isPublic(it.providerId) } ?: bestVisibleMatch(query)
        return visibleMatch.toRestBestQueryResponse()
    }

    private suspend fun bestVisibleMatch(query: ProviderQuery): ProviderMatch? {
        val result = kms.queryProviders(query.toSdk())
        val matches =
            result.getOrElse { error ->
                throw IllegalArgumentException(error.message.defaultMessage ?: "Unable to query KMS providers")
            }
        return matches.matches.firstOrNull { exposure.isPublic(it.providerId) }
    }

    private fun providerNotFound(providerId: String): NotFoundException =
        NotFoundException(
            resource = providerId,
            message = "Provider with id '$providerId' not found.",
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val capabilitiesRestService: CapabilitiesRestService
    }
}
