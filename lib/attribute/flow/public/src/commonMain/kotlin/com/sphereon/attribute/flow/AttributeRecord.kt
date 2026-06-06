/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.api.service.EidasAssuranceLevel
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * A single attribute contributed to an [AttributeBag], with full provenance.
 *
 * Where the lightweight [AttributeBag] used to store a bare `JsonElement` plus a separate
 * provenance map, the bag now holds [AttributeRecord]s: each datum carries the source that
 * produced it, the phase and time it was contributed, its assurance level, a conflict-resolution
 * priority, its retention policy, and whether it has been verified. `with()` on the bag uses
 * [priority] (then [timestamp]) to decide which record wins for a given [path].
 */
@JsExportCompat
@Serializable
data class AttributeRecord(
    /** Where this attribute lives in the bag. */
    val path: AttributePath,
    /** The typed value. */
    val value: AttributeValue,
    /** Which source produced this record (opaque producer id — an IDV node, a pipeline source, ...). */
    val sourceId: AttributeProvenanceRef,
    /** Source-specific detail (e.g. IDV node id, OIDC issuer, HR-API endpoint). */
    val sourceDetail: String? = null,
    /** The phase during which this record was contributed. */
    val phase: PipelinePhase,
    /** When this record was contributed. */
    val timestamp: Instant,
    /** Assurance level (from IDV evidence, OIDC `acr`, ...). */
    val assurance: EidasAssuranceLevel? = null,
    /** Conflict-resolution priority — higher wins; ties broken by [timestamp]. */
    val priority: Int = 0,
    /** Retention policy for this specific attribute. */
    val retention: AttributeRetentionPolicy = SessionRetention(),
    /** Whether this attribute has been verified by an IDV process. */
    val verified: Boolean = false,
) {
    /**
     * The value as a [JsonElement] when it is plain data, else `null`. Blob / key / evidence
     * values are references and have no direct JSON form here — a downstream assembler handles
     * those explicitly.
     */
    val jsonValue: JsonElement?
        get() = (value as? AttributeData)?.value
}
