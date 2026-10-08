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
 * Single source of truth for the default mount paths and bounded numeric defaults of the
 * DID Manager REST API.
 *
 * Every value here corresponds to a field on [DidManagerRestConfig] and an
 * [com.sphereon.core.api.conf.AppConfigService] property key, so a deployment can change
 * the runtime value (remount the surface, raise/lower the listDids page cap) without
 * code changes.
 */
object DidManagerRestPaths {
    /** Default URL prefix for every DID Manager REST endpoint. */
    const val BASE_PATH: String = "/api/did/v1"
    const val BASE_PATH_CONFIG_KEY: String = "didManager.rest.adapterBasePath"

    /**
     * Default upper bound for the `?size=` query parameter on every list endpoint in
     * the DID Manager REST surface. Mirrors the `size.maximum` values declared in the
     * OpenAPI spec. Larger client-supplied values are rejected with `ILLEGAL_ARGUMENT_ERROR`.
     *
     * The cap is scoped to the whole DID API rather than per-list-endpoint: every list
     * endpoint here returns the same kind of tenant-bounded data, and giving the
     * deployment-operator one knob to tune is simpler than ten knobs they'd have to
     * keep in sync. Per the REST API design guidance, pick the smallest value that
     * doesn't fragment realistic full-tenant reads for the actual deployment — 1000 is
     * the right default for typical admin/operational tenants, consumer-facing
     * deployments may want 100, bulk-export tooling may want 5000. Override via
     * [MAX_PAGE_SIZE_CONFIG_KEY].
     */
    const val MAX_PAGE_SIZE: Int = 1000
    const val MAX_PAGE_SIZE_CONFIG_KEY: String = "didManager.rest.maxPageSize"
}
