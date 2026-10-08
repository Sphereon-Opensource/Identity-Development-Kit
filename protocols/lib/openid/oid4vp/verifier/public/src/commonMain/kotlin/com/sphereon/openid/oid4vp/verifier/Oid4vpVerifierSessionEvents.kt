/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.events.EventType
import com.sphereon.core.api.events.EventTypes

/** Verifier lifecycle events shared by the core store and Universal OID4VP facade. */
object Oid4vpVerifierSessionEventTypes {
    val STATUS_CHANGED = EventType("oid4vp.universal.session.status_changed")
    val CALLBACK_ATTEMPTED = EventType("oid4vp.universal.callback.attempted")
    val CALLBACK_SUCCEEDED = EventType("oid4vp.universal.callback.dispatched")
    val CALLBACK_FAILED = EventType("oid4vp.universal.callback.failed")

    val ALL: Set<EventType> = setOf(
        EventTypes.OID4VP_REQUEST_CREATED,
        EventTypes.OID4VP_RESPONSE_RECEIVED,
        EventTypes.OID4VP_RESPONSE_VERIFIED,
        EventTypes.OID4VP_RESPONSE_FAILED,
        STATUS_CHANGED,
        CALLBACK_ATTEMPTED,
        CALLBACK_SUCCEEDED,
        CALLBACK_FAILED,
    )
}
