/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server.adapter.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.StaticPublicApiDescriptor
import com.sphereon.did.manager.rest.server.DidManagerRestConfig
import com.sphereon.did.manager.rest.server.adapter.KeyMappingHttpAdapter
import com.sphereon.did.manager.rest.server.command.AddKeyMappingEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListKeyMappingsEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveKeyMappingEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [KeyMappingHttpAdapter]. Provides metadata-only
 * information about the adapter's endpoints so the `HttpAdapterCatalog` can be
 * assembled at startup without instantiating SessionScope adapters.
 *
 * Extends [StaticPublicApiDescriptor] so endpoint descriptors automatically have
 * the adapter's `adapterBasePath` prepended in [describe] — the catalog needs full
 * paths to route incoming requests, mirroring what the runtime adapter exposes
 * through [com.sphereon.core.api.http.command.CommandBackedHttpAdapter.describe].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class KeyMappingHttpAdapterDescriptorProvider(
    config: DidManagerRestConfig,
) : StaticPublicApiDescriptor(
        adapterId = KeyMappingHttpAdapter.ID,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = config.adapterBasePath),
        endpoints =
            listOf(
                ListKeyMappingsEndpointCommand.ENDPOINT,
                AddKeyMappingEndpointCommand.ENDPOINT,
                RemoveKeyMappingEndpointCommand.ENDPOINT,
            ),
    )
