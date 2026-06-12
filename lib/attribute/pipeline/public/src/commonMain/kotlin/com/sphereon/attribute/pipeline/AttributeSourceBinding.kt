/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.pipeline

import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.attribute.mapping.AttributeMapping
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/** How a source's reply is expected to arrive. */
@JsExportCompat
@Serializable
enum class CallbackStyle {
    /** The source answers (or fails) within its `contribute()` call. */
    SYNCHRONOUS,

    /** The source dispatches an outbound call and the real answer arrives later via callback. */
    ASYNC_CALLBACK,
}

/**
 * Configuration binding an [AttributeSource] into a pipeline: which phases it runs in, whether
 * it is required, how long the engine may hold a request open waiting for an async reply, and
 * an optional source-native → semantic attribute mapping.
 */
@JsExportCompat
@Serializable
data class AttributeSourceBinding(
    /** Which source this binds — matches [AttributeSource.sourceId]. */
    val sourceId: AttributeProvenanceRef,
    /** The phases this source runs in for this pipeline. */
    @JsExportIgnoreCompat
    val phases: Set<PipelinePhase>,
    /**
     * Optional persisted source instance/configuration id. Commercial EDK/VDX deployments use this
     * to distinguish tenant-registered source instances that share one source implementation.
     */
    val sourceInstanceId: String? = null,
    /** When true, a missing required lookup key or a source failure fails the phase. */
    val required: Boolean = true,
    /**
     * How long `/credential` (or `/deferred_credential`) holds the request open waiting for this
     * source's async callback before falling back to a deferred response. `ZERO` = defer
     * immediately. Hard-capped at config-load time.
     */
    val syncWaitWindow: Duration = Duration.ZERO,
    /** Optional source-native field → semantic attribute mapping. */
    @JsExportIgnoreCompat
    val attributeMapping: List<AttributeMapping> = emptyList(),
    /** Whether this source replies synchronously or via async callback. */
    val callbackStyle: CallbackStyle = CallbackStyle.SYNCHRONOUS,
)
