/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.catalog.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * When a catalog type is served. Both bounds are dates, not a boolean, so
 * appearance and withdrawal can be scheduled.
 *
 * Inclusive [start], exclusive [end]. A null [end] means the window is open.
 * An empty window ([end] equal to [start]) means the type is not served.
 */
@JsExportCompat
@Serializable
data class CatalogListingWindow
    @JvmOverloads
    constructor(
        val start: Instant,
        val end: Instant? = null,
    ) {
        fun includes(at: Instant): Boolean = at >= start && (end == null || at < end)

        fun isEmpty(): Boolean = end != null && end <= start

        fun startEpochMillis(): Long = start.toEpochMilliseconds()

        fun endEpochMillis(): Long? = end?.toEpochMilliseconds()

        companion object {
            /** Served from the beginning of time until withdrawn. */
            val ALWAYS: CatalogListingWindow = CatalogListingWindow(start = Instant.DISTANT_PAST, end = null)

            fun open(start: Instant): CatalogListingWindow = CatalogListingWindow(start = start, end = null)

            /** Not served: a closed empty window at [at]. */
            fun never(at: Instant): CatalogListingWindow = CatalogListingWindow(start = at, end = at)

            fun fromEpochMillis(
                startMillis: Long,
                endMillis: Long?
            ): CatalogListingWindow =
                CatalogListingWindow(
                    start = Instant.fromEpochMilliseconds(startMillis),
                    end = endMillis?.let(Instant::fromEpochMilliseconds),
                )
        }
    }
