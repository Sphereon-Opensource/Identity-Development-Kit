/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer

import com.sphereon.core.api.events.EventTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class Oid4vciIssuerSessionEventTypesTest {
    @Test
    fun publicSetContainsEveryIssuerHistoryLifecycleType() {
        assertEquals(
            setOf(
                Oid4vciIssuerSessionEventTypes.TOKEN_VALIDATED,
                Oid4vciIssuerSessionEventTypes.CREDENTIAL_REQUESTED,
                EventTypes.OID4VCI_CREDENTIAL_ISSUED,
                EventTypes.OID4VCI_CREDENTIAL_FAILED,
                EventTypes.OID4VCI_CREDENTIAL_DEFERRED,
                EventTypes.OID4VCI_CREDENTIAL_DEFERRED_ISSUED,
                Oid4vciIssuerSessionEventTypes.DEFERRED_RETRY,
                Oid4vciIssuerSessionEventTypes.DEFERRED_FAILED,
                EventTypes.OID4VCI_NOTIFICATION_RECEIVED,
            ),
            Oid4vciIssuerSessionEventTypes.ALL,
        )
    }
}
