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

import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A correlation token a pipeline source uses to look attributes up — an `email`, `employee_id`,
 * `student_nr`, `business_key`, etc.
 *
 * A lookup key is **not** an attribute. It is a key other sources fetch *with*; it may or may
 * not be promoted into the final credential. It lives alongside the [com.sphereon.attribute.flow.AttributeBag]
 * and is encrypted the same way.
 */
@JsExportCompat
@Serializable
data class LookupKey(
    /** Unique name within the session — e.g. `email`, `employee_id`. */
    val name: String,
    /** The key value. PII — encrypted at rest. */
    val value: String,
    /** Optional well-known classification of the key. */
    val type: LookupKeyType? = null,
    /**
     * PROVENANCE only: the single source that PRODUCED this lookup key — one producer, one
     * phase, one timestamp. This is *not* the source(s) that consume it (consumption is 1→N and
     * declared on the consuming source's `consumedLookupKeys`).
     */
    val producedBy: AttributeProvenanceRef,
    /** Source-specific detail (e.g. IDV node id, HR-API endpoint). */
    val sourceDetail: String? = null,
    /** The phase during which this key was contributed. */
    val phase: PipelinePhase,
    /** When this key was contributed. */
    val timestamp: Instant,
    /**
     * If non-null, the lookup key is ALSO emitted as an attribute at this path before credential
     * assembly, so claim mapping can pick it up. Leave null when the key is purely operational
     * (a business key) and should not appear in the credential.
     */
    val promotedToAttributePath: AttributePath? = null,
    /** Free-form metadata; e.g. verification status, source TTL. */
    val metadata: Map<String, String> = emptyMap(),
)
