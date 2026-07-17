/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer

import com.sphereon.core.api.events.EventType
import com.sphereon.core.api.events.EventTypes

/** Safe protocol-session lifecycle events emitted by the OID4VCI issuer runtime. */
object Oid4vciIssuerSessionEventTypes {
    val TOKEN_VALIDATED = EventType("oid4vci.token.validated")
    val CREDENTIAL_REQUESTED = EventType("oid4vci.credential.requested")
    val DEFERRED_RETRY = EventType("oid4vci.deferred.retry")
    val DEFERRED_RETURNED = EventTypes.OID4VCI_CREDENTIAL_DEFERRED_ISSUED
    val DEFERRED_FAILED = EventType("oid4vci.deferred.failed")

    val ALL: Set<EventType> = setOf(
        TOKEN_VALIDATED,
        CREDENTIAL_REQUESTED,
        EventTypes.OID4VCI_CREDENTIAL_ISSUED,
        EventTypes.OID4VCI_CREDENTIAL_FAILED,
        EventTypes.OID4VCI_CREDENTIAL_DEFERRED,
        DEFERRED_RETRY,
        DEFERRED_RETURNED,
        DEFERRED_FAILED,
        EventTypes.OID4VCI_NOTIFICATION_RECEIVED,
    )
}
