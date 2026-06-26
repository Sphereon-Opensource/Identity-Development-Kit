/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.pipeline

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.attribute.flow.PipelinePhase
import com.sphereon.attribute.mapping.AttributeMapping
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.data.integration.AcknowledgementMode
import com.sphereon.data.integration.DeliveryMode
import com.sphereon.data.integration.MaterializationPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * A pluggable component that consumes resolved attributes after a pipeline phase.
 *
 * Destinations are deliberately separate from sources. A destination may be an internal semantic
 * Party value store, a blob/vault writer, a REST/OpenAPI writeback, a database upsert, or a
 * workflow handoff. Connector-backed deployments can use [AttributeDestinationBinding.connectorInstanceId]
 * to select tenant-specific settings without making the IDK pipeline depend on EDK connector types.
 */
interface AttributeDestination {
    /** Stable destination id, stamped on destination run/audit metadata. */
    val destinationId: AttributeProvenanceRef

    /** The phases in which this destination may run. */
    val supportedPhases: Set<PipelinePhase>

    /**
     * Write [payload] for [phase], reading immutable session state from [context].
     */
    suspend fun write(
        context: PipelineDestinationContext,
        phase: PipelinePhase,
        payload: DestinationPayload,
    ): IdkResult<DestinationWriteResult, IdkError>
}

/**
 * Configuration binding an [AttributeDestination] into a pipeline phase.
 */
@JsExportCompat
@Serializable
data class AttributeDestinationBinding(
    /** Which destination this binds, matching [AttributeDestination.destinationId]. */
    @SerialName("destinationId")
    val destinationId: AttributeProvenanceRef,
    /** The phases this destination runs in for this pipeline. */
    @JsExportIgnoreCompat
    @SerialName("phases")
    val phases: Set<PipelinePhase>,
    /**
     * Optional tenant-scoped connector instance id. In the greenfield connector model this is the
     * durable id used to resolve Party-backed settings and secret refs.
     */
    @SerialName("connectorInstanceId")
    val connectorInstanceId: String? = null,
    /** Optional connector operation binding id for route/run correlation. */
    @SerialName("operationBindingId")
    val operationBindingId: String? = null,
    /** When true, a destination failure fails the phase or route. */
    @SerialName("required")
    val required: Boolean = true,
    /** Default is non-materializing unless a destination explicitly opts in. */
    @SerialName("materializationPolicy")
    val materializationPolicy: MaterializationPolicy = MaterializationPolicy(),
    @SerialName("deliveryMode")
    val deliveryMode: DeliveryMode = DeliveryMode.SYNC,
    @SerialName("acknowledgementMode")
    val acknowledgementMode: AcknowledgementMode = AcknowledgementMode.ACCEPTED,
    /**
     * How long a synchronous pipeline call may wait for an async destination acknowledgement before
     * returning a deferred result.
     */
    @SerialName("syncWaitWindow")
    val syncWaitWindow: Duration = Duration.ZERO,
    /** Optional semantic/native mapping to apply before writing. */
    @JsExportIgnoreCompat
    @SerialName("destinationMapping")
    val destinationMapping: List<AttributeMapping> = emptyList(),
    @SerialName("callbackStyle")
    val callbackStyle: CallbackStyle = CallbackStyle.SYNCHRONOUS,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

interface PipelineDestinationContext {
    /** External correlation handle for this pipeline session. */
    val correlationId: String

    /** The tenant the session belongs to. */
    val tenantId: String

    /** The phase currently executing. */
    val phase: PipelinePhase

    /** Attributes accumulated so far across all phases. */
    val bag: AttributeBag

    /** Lookup keys accumulated so far. */
    val lookupKeys: LookupKeySet

    /** Attributes pushed in for the current phase by the caller or inbound transport. */
    val phaseInput: AttributeBag

    /** The destination binding currently being invoked. */
    val destinationBinding: AttributeDestinationBinding? get() = null
}

@JsExportCompat
@Serializable
data class DestinationPayload(
    @JsExportIgnoreCompat
    @SerialName("attributes")
    val attributes: AttributeBag,
    @JsExportIgnoreCompat
    @SerialName("lookupKeys")
    val lookupKeys: List<LookupKey> = emptyList(),
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

@JsExportCompat
@Serializable
data class DestinationWriteResult(
    @SerialName("status")
    val status: DestinationWriteStatus,
    @SerialName("recordsAccepted")
    val recordsAccepted: Long = 0,
    @SerialName("recordsCommitted")
    val recordsCommitted: Long = 0,
    @SerialName("acknowledgementRef")
    val acknowledgementRef: String? = null,
    @SerialName("externalRunRef")
    val externalRunRef: String? = null,
    @SerialName("deferred")
    val deferred: Boolean = false,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

@JsExportCompat
@Serializable
enum class DestinationWriteStatus {
    ACCEPTED,
    COMMITTED,
    DEFERRED,
    PARTIAL,
    FAILED,
}
