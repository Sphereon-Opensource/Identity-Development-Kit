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
import com.sphereon.did.manager.rest.server.adapter.DidLifecycleHttpAdapter
import com.sphereon.did.manager.rest.server.command.CreateDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.DeactivateDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.DeleteDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListDidsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ReplaceDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.ResolveDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.TrackExternalDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.UpdateDidEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [DidLifecycleHttpAdapter]. Provides metadata-only
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
class DidLifecycleHttpAdapterDescriptorProvider(
    config: DidManagerRestConfig,
) : StaticPublicApiDescriptor(
        adapterId = DidLifecycleHttpAdapter.ID,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = config.adapterBasePath),
        endpoints =
            listOf(
                CreateDidEndpointCommand.ENDPOINT,
                ListDidsEndpointCommand.ENDPOINT,
                TrackExternalDidEndpointCommand.ENDPOINT,
                GetDidEndpointCommand.ENDPOINT,
                UpdateDidEndpointCommand.ENDPOINT,
                ReplaceDidEndpointCommand.ENDPOINT,
                DeleteDidEndpointCommand.ENDPOINT,
                DeactivateDidEndpointCommand.ENDPOINT,
                ResolveDidEndpointCommand.ENDPOINT,
            ),
    )
