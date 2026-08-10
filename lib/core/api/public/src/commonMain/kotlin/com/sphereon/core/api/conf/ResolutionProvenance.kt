/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Security-relevant facts that follow a configuration value through resolution.
 *
 * Taints are monotonic: interpolation can add facts, but may never remove facts inherited from
 * the source value or a recursively referenced child.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionTaint", exact = true)
enum class ResolutionTaint {
    ENVIRONMENT,
    INTERPOLATED,
    UNKNOWN,
    SENSITIVE,
}

/**
 * Durable provenance attached to resolved configuration values.
 *
 * [sourceScope] is the least-privileged source scope involved in producing the value. A null
 * source is deliberately treated as unknown and therefore unsafe to persist in a cache.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionProvenance", exact = true)
@CoverageExcludedDataClass
data class ResolutionProvenance(
    val sourceScope: ConfigLevel?,
    val taints: List<ResolutionTaint> = listOf(ResolutionTaint.UNKNOWN),
) {
    fun hasTaint(taint: ResolutionTaint): Boolean = taint in taints

    fun withTaint(taint: ResolutionTaint): ResolutionProvenance =
        if (hasTaint(taint)) {
            this
        } else {
            copy(taints = (taints + taint).distinct())
        }

    fun merge(other: ResolutionProvenance): ResolutionProvenance =
        ResolutionProvenance(
            sourceScope = leastPrivilegedScope(sourceScope, other.sourceScope),
            taints =
                (
                    taints +
                        other.taints +
                        if (sourceScope == null || other.sourceScope == null) listOf(ResolutionTaint.UNKNOWN) else emptyList()
                ).distinct(),
        )

    /**
     * Materialized values are cacheable only when their origin is known and they are neither
     * environment-derived nor sensitive. Interpolation alone is cache-safe when every source is
     * known and non-sensitive.
     */
    fun isCacheSafe(isSecret: Boolean = false): Boolean =
        !isSecret &&
            sourceScope != null &&
            !hasTaint(ResolutionTaint.UNKNOWN) &&
            !hasTaint(ResolutionTaint.ENVIRONMENT) &&
            !hasTaint(ResolutionTaint.SENSITIVE)

    companion object {
        fun known(
            sourceScope: ConfigLevel,
            sensitive: Boolean = false,
        ): ResolutionProvenance =
            ResolutionProvenance(
                sourceScope = sourceScope,
                taints = if (sensitive) listOf(ResolutionTaint.SENSITIVE) else emptyList(),
            )

        fun unknown(): ResolutionProvenance =
            ResolutionProvenance(
                sourceScope = null,
                taints = listOf(ResolutionTaint.UNKNOWN),
            )
    }
}

private fun leastPrivilegedScope(
    first: ConfigLevel?,
    second: ConfigLevel?,
): ConfigLevel? =
    when {
        first == null || second == null -> null
        first.level >= second.level -> first
        else -> second
    }
