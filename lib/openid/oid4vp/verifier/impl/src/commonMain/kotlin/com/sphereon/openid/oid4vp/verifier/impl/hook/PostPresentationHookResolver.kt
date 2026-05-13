/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.verifier.impl.hook

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.service.ServiceCommandRegistry
import com.sphereon.core.api.session.matchesAdvancedPattern

/**
 * Pure function that decides which post-presentation hook command IDs should
 * fire for a single presentation. Mirrors `PostIssuanceHookResolver` from the
 * OID4VCI side so consumers can be configured symmetrically:
 *
 *  1. Resolve explicit IDs from config (`hooks.<hookPointId>.commands`)
 *     and pattern matches against the registry (`hooks.<hookPointId>.patterns`,
 *     default `[hook.post-presentation.**]`).
 *  2. Union the two into the deployment-level resolved set.
 *  3. Intersect with the session-level allow-list when non-null.
 *  4. Return the final set.
 *
 * Pure — no suspensions, no IO. Callers handle resolution + invocation +
 * error isolation.
 */
internal object PostPresentationHookResolver {
    const val DEFAULT_HOOK_PATTERN: String = "hook.post-presentation.**"

    fun resolveHookIds(
        hookPointId: String,
        discovery: ServiceCommandRegistry,
        propertyResolver: PropertyResolver?,
        sessionAllowList: List<String>?,
    ): Set<String> {
        val explicitIds =
            propertyResolver
                ?.getPropertyAsString("hooks.$hookPointId.commands")
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?: emptyList()
        val patterns =
            propertyResolver
                ?.getPropertyAsString("hooks.$hookPointId.patterns")
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?: listOf(DEFAULT_HOOK_PATTERN)

        val allIds = discovery.listCommandIds()
        val deploymentResolved =
            buildSet {
                explicitIds.filterTo(this) { it in allIds }
                patterns.forEach { pattern ->
                    allIds.filterTo(this) { matchesAdvancedPattern(pattern, it) }
                }
            }
        return if (sessionAllowList != null) {
            deploymentResolved.intersect(sessionAllowList.toSet())
        } else {
            deploymentResolved
        }
    }
}
