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
import com.sphereon.did.manager.rest.server.adapter.VerificationMethodHttpAdapter
import com.sphereon.did.manager.rest.server.command.AddVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListVerificationMethodsEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.UpdateVerificationMethodEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for [VerificationMethodHttpAdapter]. Provides metadata-only
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
class VerificationMethodHttpAdapterDescriptorProvider(
    config: DidManagerRestConfig,
) : StaticPublicApiDescriptor(
        adapterId = VerificationMethodHttpAdapter.ID,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = config.adapterBasePath),
        endpoints =
            listOf(
                ListVerificationMethodsEndpointCommand.ENDPOINT,
                AddVerificationMethodEndpointCommand.ENDPOINT,
                GetVerificationMethodEndpointCommand.ENDPOINT,
                UpdateVerificationMethodEndpointCommand.ENDPOINT,
                RemoveVerificationMethodEndpointCommand.ENDPOINT,
            ),
    )
