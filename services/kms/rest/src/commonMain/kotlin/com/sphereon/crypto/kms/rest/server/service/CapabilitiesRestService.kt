/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.kms.rest.api.generated.models.ListCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderQuery
import com.sphereon.crypto.kms.rest.api.generated.models.QueryBestProviderResponse
import com.sphereon.crypto.kms.rest.api.generated.models.QueryProvidersResponse
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("CapabilitiesRestService", exact = true)
interface CapabilitiesRestService {
    suspend fun listCapabilities(includeDisabled: Boolean = false): ListCapabilitiesResponse

    suspend fun getProviderCapabilities(providerId: String): ProviderCapabilitiesResponse

    suspend fun queryProviders(query: ProviderQuery): QueryProvidersResponse

    suspend fun queryBestProvider(query: ProviderQuery): QueryBestProviderResponse
}
