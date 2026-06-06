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

import com.sphereon.attribute.flow.AttributeBag

/**
 * The read-only view an [AttributeSource] receives when the engine invokes it.
 *
 * Deliberately an interface, not a data class: the EDK pipeline engine implements it, and later
 * phases (deferred issuance, interactive authorization) can widen what a source may read without
 * breaking the SPI. Sources read from it; they never mutate it — their output is the returned
 * [SourceContribution].
 */
interface PipelineExecutionContext {
    /** External correlation handle for this issuance session. */
    val correlationId: String

    /** The tenant the session belongs to. */
    val tenantId: String

    /** The phase currently executing. */
    val phase: com.sphereon.attribute.flow.PipelinePhase

    /** Attributes accumulated so far across all phases. */
    val bag: AttributeBag

    /** Lookup keys accumulated so far. */
    val lookupKeys: LookupKeySet

    /** Attributes pushed in for the current phase by the caller or an inbound transport. */
    val phaseInput: AttributeBag
}
