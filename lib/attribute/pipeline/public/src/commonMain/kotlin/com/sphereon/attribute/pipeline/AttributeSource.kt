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
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * A pluggable component that actively *produces* attributes (and lookup keys) when the pipeline
 * engine invokes it for a phase.
 *
 * This is the active producer SPI — distinct from the passive
 * [com.sphereon.attribute.flow.AttributeOrigin] binding descriptor, which only says *where* an
 * already-resolved value comes from. A source declares which lookup keys it consumes and
 * produces so the engine can topologically order sources within a phase.
 */
interface AttributeSource {
    /** Stable identifier for this source — also the [AttributeProvenanceRef] stamped on its output. */
    val sourceId: AttributeProvenanceRef

    /** The phases in which this source may run. */
    val supportedPhases: Set<PipelinePhase>

    /** Lookup keys this source REQUIRES to run. If any are missing the source is skipped or fails per its binding. */
    val consumedLookupKeys: Set<String> get() = emptySet()

    /** Lookup keys this source PRODUCES — the engine uses this for topological ordering. */
    val producedLookupKeys: Set<String> get() = emptySet()

    /** Attribute paths this source MAY produce — for validation and required-set checks. */
    val producedAttributePaths: Set<AttributePath> get() = emptySet()

    /**
     * Run the source for [phase], reading from [context]. Returns the attributes and lookup keys
     * it produced (and optionally a [DeferralSignal] if its answer is not fully synchronous).
     */
    suspend fun contribute(
        context: PipelineExecutionContext,
        phase: PipelinePhase,
    ): IdkResult<SourceContribution, IdkError>
}
