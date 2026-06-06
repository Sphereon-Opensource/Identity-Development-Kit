/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest

/**
 * Paths, command-id constants, and the OpenAPI tag for the PUBLIC, unauthenticated status-list
 * hosting surface. Both endpoints delegate to the IDK `statuslist.token.get` service command and
 * return the RAW signed token with its own media type and a cache hint.
 *
 * No hyphens in URL path segments (`feedback_no_hyphens_api`).
 */
object StatusListHostingApiConstants {
    /**
     * Adapter mount; the hosting endpoints are rooted here. Deliberately UNVERSIONED: this is the
     * stable, externally referenced `statusListUri` that issued credentials embed, so the URL must
     * not change across platform upgrades.
     */
    const val BASE_PATH = "/statuslists"

    /**
     * Default `Cache-Control max-age` (seconds) used when the token carries no TTL hint. Status
     * lists update infrequently; a short cache keeps verifiers reasonably fresh while still
     * absorbing load.
     */
    const val DEFAULT_CACHE_MAX_AGE_SECONDS: Long = 300

    /** REST endpoint paths, relative to [BASE_PATH]. */
    object Paths {
        const val TOKEN_BY_ID = "/{id}"
        const val TOKEN_BY_CORRELATION_ID = "/by/{correlationId}"
    }

    /**
     * SIMPLE, by-index admin endpoint paths, relative to [BASE_PATH]. This is the open-core admin
     * surface (status read / revoke / clear by numeric index, no business keys); the EDK
     * `lib-statuslist-management-rest` provides the durable, business-key management API.
     */
    object AdminPaths {
        const val ENTRY_STATUS = "/{id}/entries/{index}"
        const val ENTRY_REVOKE = "/{id}/entries/{index}/revoke"
        const val CLEAR = "/{id}/clear"
    }

    object Tags {
        const val STATUS_LIST_HOSTING = "Status List Hosting"
        const val STATUS_LIST_ADMIN = "Status List Admin"
    }

    /** Three-part command ids (`module.service.command`) for the HTTP endpoint commands. */
    object CommandIds {
        const val HTTP_GET_TOKEN_BY_ID = "statuslist.token.get-endpoint"
        const val HTTP_GET_TOKEN_BY_CORRELATION_ID = "statuslist.token.getbycorrelationid-endpoint"
    }

    /** Command ids for the simple by-index admin endpoints. */
    object AdminCommandIds {
        const val ENTRY_GET = "statuslist.admin.entry.get"
        const val ENTRY_REVOKE = "statuslist.admin.entry.revoke"
        const val CLEAR = "statuslist.admin.clear"
    }
}
