/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Digest recorded with a stream command. The bytes are calculated by an IDK digest implementation. */
@Serializable
data class EventDigest(
    val algorithm: String,
    val value: String,
) {
    init {
        require(algorithm.isNotBlank()) { "algorithm must not be blank" }
        require(value.isNotBlank()) { "value must not be blank" }
    }
}

/** Optimistic-concurrency and command identity for an event-sourced stream. */
@Serializable
data class EventStreamContext(
    val modelId: String,
    val streamId: String,
    val sequence: Long,
    val expectedPriorVersion: Long,
    val commandId: String,
    val idempotencyKey: String,
    val commandDigest: EventDigest,
    val causationId: String? = null,
) {
    init {
        require(modelId.isNotBlank()) { "modelId must not be blank" }
        require(streamId.isNotBlank()) { "streamId must not be blank" }
        require(sequence > 0) { "sequence must be positive" }
        require(expectedPriorVersion >= 0) { "expectedPriorVersion must not be negative" }
        require(sequence > expectedPriorVersion) { "sequence must follow expectedPriorVersion" }
        require(commandId.count { it == '.' } == 2) { "commandId must use three segments" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(causationId == null || causationId.isNotBlank()) { "causationId must be null or non-blank" }
    }
}

/** Server-resolved authority responsible for a durable stream event. */
@Serializable
data class EventAuthority(
    val actorId: String,
    val actorType: String,
    val authorityRef: String? = null,
) {
    init {
        require(actorId.isNotBlank()) { "actorId must not be blank" }
        require(actorType.isNotBlank()) { "actorType must not be blank" }
        require(authorityRef == null || authorityRef.isNotBlank()) { "authorityRef must be null or non-blank" }
    }
}

/** Bitemporal effective interval. [until] is exclusive when present. */
@Serializable
data class EventEffectiveInterval(
    val from: Instant,
    val until: Instant? = null,
) {
    init {
        require(until == null || until > from) { "until must be after from" }
    }
}

/** Versioned source reference without imposing an enterprise-specific revision scheme on IDK. */
@Serializable
data class EventRevisionRef(
    val id: String,
    val revision: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(revision.isNotBlank()) { "revision must not be blank" }
    }
}

/** Pinned model and source provenance carried by a stream event. */
@Serializable
data class EventSourceProvenance(
    val semanticRefs: List<EventRevisionRef> = emptyList(),
    val templateRefs: List<EventRevisionRef> = emptyList(),
    val sourceRefs: List<EventRevisionRef> = emptyList(),
    val mappingRefs: List<EventRevisionRef> = emptyList(),
    val formRefs: List<EventRevisionRef> = emptyList(),
    val workflowRefs: List<EventRevisionRef> = emptyList(),
    val protectedRefs: List<String> = emptyList(),
) {
    init {
        require(protectedRefs.none { it.isBlank() }) { "protectedRefs must not contain blank values" }
    }
}

/**
 * Event-sourcing specialization of the existing IDK [Event].
 *
 * It preserves the established event, event-hub, signing, encryption, and persistence contracts
 * while adding the metadata needed for optimistic stream append and deterministic replay.
 */
interface StreamEvent : Event {
    val stream: EventStreamContext
    val authority: EventAuthority
    val effectiveInterval: EventEffectiveInterval
    val provenance: EventSourceProvenance
}

/** Default serializable [StreamEvent] implementation used by EDK database event stores. */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DefaultStreamEvent(
    override val id: Uuid,
    override val type: EventType,
    override val version: String,
    override val origin: String,
    override val timestamp: Instant,
    override val context: EventContext,
    override val subsystem: EventSubsystem,
    override val category: EventCategory,
    override val payload: JsonObject,
    override val stream: EventStreamContext,
    override val authority: EventAuthority,
    override val effectiveInterval: EventEffectiveInterval,
    override val provenance: EventSourceProvenance,
    override val signature: EventSignature? = null,
    override val encryption: EventEncryption? = null,
) : StreamEvent {
    init {
        require(version.isNotBlank()) { "version must not be blank" }
        require(origin == stream.commandId) { "origin must equal stream.commandId" }
        require(!context.tenantId.isNullOrBlank()) { "stream events require a tenant context" }
        require(!context.principalId.isNullOrBlank()) { "stream events require a principal context" }
        require(!context.correlationId.isNullOrBlank()) { "stream events require a correlationId" }
        require(context.principalId == authority.actorId) { "event actor must match the resolved principal" }
    }
}
