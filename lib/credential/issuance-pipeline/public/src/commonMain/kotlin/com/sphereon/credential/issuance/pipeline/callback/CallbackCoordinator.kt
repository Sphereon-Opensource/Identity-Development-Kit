/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline.callback

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding

/**
 * Synchronisation seam between an inbound async-callback contribution and a `/credential`
 * request that is holding its response open within an `AttributeSourceBinding.syncWaitWindow`.
 *
 * The credential handler calls [awaitContribution] for each pending async-callback source it
 * is willing to wait for; the implementation returns a token that resolves when either the
 * callback endpoint reports a contribution via [notifyContribution] for the same
 * `(correlationId, sourceId)` pair, or the wait window expires.
 *
 * Implementations are expected to live at session or app scope and use lightweight in-process
 * primitives ([kotlinx.coroutines.CompletableDeferred], a `Channel`, ...) to bridge the two
 * coroutines. Multi-instance deployments that need cross-pod coordination ship their own
 * implementation backed by a shared bus.
 */
interface CallbackCoordinator {
    /**
     * Signal that an inbound contribution has been recorded for `(correlationId, sourceId)`.
     *
     * Idempotent and side-effect-free when no waiter is registered: the credential handler
     * may not be in its wait window (e.g. the wait window already elapsed, or no
     * `/credential` was in flight when the callback landed). In both cases the contribution
     * is already persisted by the time this method is called.
     */
    suspend fun notifyContribution(
        correlationId: String,
        sourceId: String,
    )

    /**
     * Suspend until a contribution for `(correlationId, sourceId)` is notified via
     * [notifyContribution], or until cancellation. The credential handler is expected to wrap
     * the call in `withTimeout(syncWaitWindow)` to bound the wait; the coordinator itself
     * does NOT enforce a timeout.
     */
    suspend fun awaitContribution(
        correlationId: String,
        sourceId: String,
    )
}

/**
 * Exposes [CallbackCoordinator] as an optional graph accessor so that consumers declaring
 * `CallbackCoordinator? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. Suppliers (the EDK
 * `com.sphereon.credential.issuance.pipeline.impl.callback.InProcessCallbackCoordinator` and any
 * cross-pod implementation) add a second
 * `@ContributesBinding(AppScope::class, binding = binding<CallbackCoordinator?>())` so the default
 * `null` body here is overridden whenever a real binding is present in the graph.
 */
@ContributesTo(AppScope::class)
interface CallbackCoordinatorOptionalProvider {
    @OptionalBinding
    val optionalCallbackCoordinator: CallbackCoordinator? get() = null
}
