/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.events.AppEventService
import com.sphereon.core.events.EventBuilder
import com.sphereon.core.events.EventEncryptionService
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventSigningService
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.EventPersistenceSink
import com.sphereon.core.events.EventStore
import com.sphereon.core.events.SessionStatusChange
import com.sphereon.core.events.SessionStatusEventPayload
import com.sphereon.core.events.SessionStatusEventTypes
import com.sphereon.core.events.UserEventService
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventHubSessionStatusEventPublisherTest {
    @Test
    fun storageFailurePreventsBroadcast() = runTest {
        val order = mutableListOf<String>()
        val probe = PersistenceProbe(order = order, storeFailure = true)
        val hub = RecordingEventHub(order)
        val publisher = currentPublisher(probe, hub)

        assertFailsWith<Throwable> { publisher.publish(statusChange()) }

        assertEquals(listOf("store"), order)
        assertTrue(hub.events.replayCache.isEmpty())
        assertEquals(0, probe.sinkCalls)
    }

    @Test
    fun sinkFailurePreventsBroadcast() = runTest {
        val order = mutableListOf<String>()
        val probe = PersistenceProbe(order = order, sinkFailure = true)
        val hub = RecordingEventHub(order)
        val publisher = currentPublisher(probe, hub)

        assertFailsWith<Throwable> { publisher.publish(statusChange()) }

        assertEquals(listOf("store", "sink"), order)
        assertTrue(hub.events.replayCache.isEmpty())
        assertEquals(1, probe.storeCalls)
    }

    @Test
    fun persistenceAndSinksPrecedeExactlyOneBroadcast() = runTest {
        val order = mutableListOf<String>()
        val probe = PersistenceProbe(order = order)
        val hub = RecordingEventHub(order)
        val publisher = currentPublisher(probe, hub)
        val change = statusChange()

        publisher.publish(change)

        assertEquals(listOf("store", "sink", "broadcast"), order)
        assertEquals(1, probe.storeCalls)
        assertEquals(1, probe.sinkCalls)
        assertEquals(1, hub.events.replayCache.size)

        val event = hub.events.replayCache.single()
        assertEquals(probe.storedEvent, event)
        assertEquals(probe.sinkEvent, event)
        assertEquals(change.type, event.type)
        assertEquals("oid4vci-session-store", event.origin)
        assertEquals(change.subsystem, event.subsystem)
        assertEquals(SessionStatusEventPayload.category, event.category)
        assertEquals(SessionStatusEventPayload.encode(change), event.payload)
        assertEquals("session-42", event.context.sessionId)
        assertEquals("tenant-42", event.context.tenantId)
        assertEquals("principal-42", event.context.principalId)
        assertEquals("correlation-42", event.context.correlationId)
    }

    @Test
    fun crossTenantStatusChangeIsRejectedBeforePersistenceOrBroadcast() = runTest {
        val order = mutableListOf<String>()
        val probe = PersistenceProbe(order = order)
        val hub = RecordingEventHub(order)
        val publisher = currentPublisher(probe, hub)

        val outcome = runCatching {
            publisher.publish(statusChange().copy(tenantId = "tenant-other"))
        }

        assertTrue(order.isEmpty(), "Tenant mismatch must be rejected before store, sink, or broadcast: $order")
        assertEquals(0, probe.storeCalls)
        assertEquals(0, probe.sinkCalls)
        assertEquals(null, probe.storedEvent)
        assertEquals(null, probe.sinkEvent)
        assertTrue(hub.events.replayCache.isEmpty())
        assertTrue(outcome.isFailure, "A cross-tenant change must be rejected, not silently dropped or rewritten")
    }

    /**
     * Uses the production SessionEventServiceImpl while keeping the test contract and fake
     * persistence probes unchanged.
     */
    private fun currentPublisher(
        probe: PersistenceProbe,
        hub: EventHub,
    ): EventHubSessionStatusEventPublisher =
        EventHubSessionStatusEventPublisher(
            SessionEventServiceImpl(
                parent = object : UserEventService {
                    override val parent: AppEventService = object : AppEventService {
                        override val eventHub: EventHub = hub
                        override suspend fun emit(event: Event) = error("not used")
                        override suspend fun emit(event: Event, sign: Boolean, encrypt: Boolean, keyAlias: String?, encryptionKeyAlias: String?, encryptParts: Set<com.sphereon.core.events.EncryptedPart>) = error("not used")
                        override fun eventBuilder(): EventBuilder = error("not used")
                    }
                    override val userContext: UserContext = testSessionContext.context
                    override val eventHub: EventHub = hub
                    override suspend fun emit(event: Event) = error("not used")
                    override suspend fun emit(event: Event, sign: Boolean, encrypt: Boolean, keyAlias: String?, encryptionKeyAlias: String?, encryptParts: Set<com.sphereon.core.events.EncryptedPart>) = error("not used")
                    override fun eventBuilder(): EventBuilder = error("not used")
                },
                sessionContext = testSessionContext,
                eventHub = hub,
                eventStore = probe.eventStore,
                signingService = TestEventSigningService,
                encryptionService = TestEventEncryptionService,
                persistenceSinks = setOf(EventPersistenceSink { probe.persist(it) }),
            ),
        )

    private fun statusChange(): SessionStatusChange =
        SessionStatusChange(
            type = SessionStatusEventTypes.OID4VCI_ISSUANCE_SESSION_STATUS_CHANGED,
            subsystem = EventSubsystems.OID4VCI,
            sessionId = "session-42",
            correlationId = "correlation-42",
            instanceId = "instance-42",
            tenantId = "tenant-42",
            previousStatus = "pending",
            status = "completed",
            data = buildJsonObject {
                put("credentialId", "credential-42")
                put("attempt", 2)
            },
        )

    private companion object {
        val testSessionContext: SessionContext =
            object : SessionContext {
                override val context: UserContext =
                    object : UserContext {
                        override val id: String = "user-42"
                        override val tenant: TenantContextData =
                            object : TenantContextData {
                                override val tenantId: String = "tenant-42"
                            }
                        override val principal: Any? = "principal-42"
                        override val secureDetails: SecuredTenantContextDetails? = null
                        override val principalType: PrincipalType = PrincipalType.USER
                    }
                override val sessionId: String = "session-42"
                override val correlationId: String = "session-correlation-42"
            }
    }
}

private class PersistenceProbe(
    private val order: MutableList<String>,
    private val storeFailure: Boolean = false,
    private val sinkFailure: Boolean = false,
) {
    val eventStore: RecordingEventStore = RecordingEventStore(order, storeFailure)
    var storeCalls: Int = 0
        private set
    var sinkCalls: Int = 0
        private set
    var sinkEvent: Event? = null
        private set
    val storedEvent: Event?
        get() = eventStore.storedEvent

    suspend fun persist(event: Event) {
        sinkCalls++
        order += "sink"
        sinkEvent = event
        if (sinkFailure) {
            error("event persistence sink unavailable")
        }
    }

    init {
        eventStore.onStored = { storeCalls++ }
    }
}

private class RecordingEventStore(
    private val order: MutableList<String>,
    private val failure: Boolean,
    private val delegate: EventStore = RingBufferEventStore(),
) : EventStore by delegate {
    var storedEvent: Event? = null
        private set
    var onStored: (() -> Unit)? = null

    override suspend fun store(event: Event): IdkResult<Event, IdkError> {
        order += "store"
        if (failure) {
            return Err(IdkError.UNKNOWN_ERROR(message = "event store unavailable"))
        }
        storedEvent = event
        onStored?.invoke()
        return delegate.store(event)
    }
}

private class RecordingEventHub(
    private val order: MutableList<String>,
    private val delegate: EventHub = EventHubImpl(),
) : EventHub by delegate {
    override suspend fun publish(event: Event) {
        order += "broadcast"
        delegate.publish(event)
    }
}

private object TestEventSigningService : EventSigningService {
    override suspend fun sign(event: Event, keyAlias: String?): IdkResult<Event, IdkError> = Ok(event)
    override suspend fun verify(event: Event): IdkResult<Boolean, IdkError> = Ok(true)
    override fun getDefaultKeyAlias(): String? = null
}

private object TestEventEncryptionService : EventEncryptionService {
    override suspend fun encrypt(event: Event, keyAlias: String?, parts: Set<com.sphereon.core.events.EncryptedPart>): IdkResult<Event, IdkError> = Ok(event)
    override suspend fun decrypt(event: Event, keyAlias: String?): IdkResult<Event, IdkError> = Ok(event)
    override fun getDefaultKeyAlias(): String? = null
}
