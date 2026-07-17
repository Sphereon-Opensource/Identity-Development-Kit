/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.events.EventTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class Oid4vpVerifierSessionEventTypesTest {
    @Test
    fun publicSetContainsEveryVerifierHistoryLifecycleType() {
        assertEquals(
            setOf(
                EventTypes.OID4VP_REQUEST_CREATED,
                EventTypes.OID4VP_RESPONSE_RECEIVED,
                EventTypes.OID4VP_RESPONSE_VERIFIED,
                EventTypes.OID4VP_RESPONSE_FAILED,
                Oid4vpVerifierSessionEventTypes.STATUS_CHANGED,
                Oid4vpVerifierSessionEventTypes.CALLBACK_ATTEMPTED,
                Oid4vpVerifierSessionEventTypes.CALLBACK_SUCCEEDED,
                Oid4vpVerifierSessionEventTypes.CALLBACK_FAILED,
            ),
            Oid4vpVerifierSessionEventTypes.ALL,
        )
    }
}
