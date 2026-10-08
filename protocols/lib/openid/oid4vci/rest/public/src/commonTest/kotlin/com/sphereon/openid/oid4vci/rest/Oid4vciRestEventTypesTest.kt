/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.rest

import kotlin.test.Test
import kotlin.test.assertTrue

class Oid4vciRestEventTypesTest {
    @Test
    fun callbackAttemptIsPartOfThePublicEventSet() {
        assertTrue(Oid4vciRestEventTypes.CALLBACK_ATTEMPTED in Oid4vciRestEventTypes.ALL)
    }
}
