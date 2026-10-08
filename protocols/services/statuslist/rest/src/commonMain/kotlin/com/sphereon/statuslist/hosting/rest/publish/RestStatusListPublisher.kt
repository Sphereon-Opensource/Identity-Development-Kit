/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.hosting.rest.publish

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.hosting.rest.StatusListHostingConfig
import com.sphereon.statuslist.spi.StatusListPublisher
import com.sphereon.statuslist.spi.StatusListPublisherIds
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default hosting driver: this deployment serves the signed token on demand from the public hosting
 * REST surface. [resolveStatusListUri] derives the absolute URI from the configurable hosting base
 * path and the deployment's external base URL — `<externalBaseUrl><basePath>/<correlationId>` — so a
 * list need not hard-code its `uri`. Returns null when no external base URL is configured (then the
 * list must set `uri` explicitly). [publish] is a no-op: the token is pulled, not pushed.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<StatusListPublisher>())
class RestStatusListPublisher(
    private val hostingConfig: StatusListHostingConfig,
) : StatusListPublisher {
    override val id: String = StatusListPublisherIds.REST

    override fun resolveStatusListUri(correlationId: String): String? {
        val base = hostingConfig.externalBaseUrl ?: return null
        return "$base${hostingConfig.basePath}/$correlationId"
    }

    override suspend fun publish(
        ref: StatusListRef,
        token: StatusListToken,
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}
