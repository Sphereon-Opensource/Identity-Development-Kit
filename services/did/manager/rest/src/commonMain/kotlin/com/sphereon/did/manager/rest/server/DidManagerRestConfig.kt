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

/**
 * Resolved runtime configuration for the DID Manager REST API.
 *
 * Bound at AppScope by [DidManagerRestConfigModule] and injected into the adapters,
 * descriptor providers, and endpoint command implementations that consume it. Defaults
 * come from [DidManagerRestPaths]; each field has a matching `…_CONFIG_KEY` so a
 * deployment can override it through any [com.sphereon.core.api.conf.AppConfigService]
 * property source (env, yaml, programmatic).
 *
 * Keep this class small. Add a field only when there's a real per-deployment reason
 * to change it — speculative knobs grow the surface faster than demand and force every
 * test, fixture, and SDK doc to learn one more dial.
 */
data class DidManagerRestConfig(
    /** URL prefix every DID Manager REST adapter mounts under. */
    val adapterBasePath: String = DidManagerRestPaths.BASE_PATH,
    /**
     * Upper bound for the `?size=` query parameter on every list endpoint in the DID
     * Manager REST surface. Requests above this value are rejected with
     * `ILLEGAL_ARGUMENT_ERROR` (400). See the matching field KDoc in [DidManagerRestPaths]
     * for guidance on picking the deployment-appropriate value.
     */
    val maxPageSize: Int = DidManagerRestPaths.MAX_PAGE_SIZE,
)
