/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server

import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.impl.DidCreationDslProcessor
import com.sphereon.did.manager.impl.DidCreationDslProcessorImpl
import com.sphereon.did.manager.impl.DidManagerServiceImpl
import com.sphereon.did.manager.rest.server.adapter.DidManagerHttpAdapter

/**
 * Test fixture bundling the handles every IDK-21 REST test pulls out of the session graph.
 * Encapsulates the otherwise-multi `as` casts on `session.graph` (one cast per impl-private
 * Graph type) so the test setup reads as one value and so a future DI change (a unified
 * test-graph type, multibinding refactor) only has to adjust this file.
 */
internal data class TestSessionGraph(
    val adapter: DidManagerHttpAdapter,
    val dslProcessor: DidCreationDslProcessor,
    val didManager: DidManager,
    val keyManager: KeyManagerService,
) {
    companion object {
        fun fromSession(session: SessionInstance): TestSessionGraph {
            val graph = session.graph
            return TestSessionGraph(
                adapter = (graph as DidManagerHttpAdapter.Graph).didManagerHttpAdapter,
                dslProcessor = (graph as DidCreationDslProcessorImpl.Graph).didCreationDslProcessor,
                didManager = (graph as DidManagerServiceImpl.Graph).didManager,
                keyManager = graph.asKeyManagerServiceGraph().keyManagerService,
            )
        }
    }
}
