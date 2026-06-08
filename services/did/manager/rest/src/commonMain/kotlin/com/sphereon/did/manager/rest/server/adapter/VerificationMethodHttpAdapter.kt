/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server.adapter

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.rest.server.DidManagerRestConfig
import com.sphereon.did.manager.rest.server.command.AddVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListVerificationMethodsEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.UpdateVerificationMethodEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP adapter for the Verification methods sub-surface of the IDK-21 DID Manager REST API.
 * Mounted at `/api/did/v1`; routes are relative to that prefix and live in
 * the per-endpoint [HttpEndpointCommand] descriptors.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class VerificationMethodHttpAdapter(
    execution: SessionExecution,
    config: DidManagerRestConfig,
    private val listVerificationMethods: ListVerificationMethodsEndpointCommand,
    private val addVerificationMethod: AddVerificationMethodEndpointCommand,
    private val getVerificationMethod: GetVerificationMethodEndpointCommand,
    private val updateVerificationMethod: UpdateVerificationMethodEndpointCommand,
    private val removeVerificationMethod: RemoveVerificationMethodEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = config.adapterBasePath),
    ) {
    companion object {
        const val ID: String = "DID-MANAGER-VERIFICATION-METHOD"
    }

    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            listVerificationMethods,
            addVerificationMethod,
            getVerificationMethod,
            updateVerificationMethod,
            removeVerificationMethod,
        )
}
