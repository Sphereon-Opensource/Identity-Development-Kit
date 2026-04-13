/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.rest.impl

import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.IssuanceCallbackConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionEntryConversionTest {
    @Test
    fun roundtripWithoutCallback() {
        val session =
            CredentialOfferSession(
                correlationId = "corr-123",
                offerId = "offer-456",
                issuanceSessionId = "session-789",
                status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                callbackConfig = null,
                state = "caller-state",
                createdAt = 1000000L,
                lastUpdatedAt = 1000000L,
                expiresAt = 1600000L,
            )

        val entry = KvCredentialOfferSessionStore.CredentialOfferSessionEntry.fromPublic(session)
        val restored = entry.toPublic()

        assertEquals(session.correlationId, restored.correlationId)
        assertEquals(session.offerId, restored.offerId)
        assertEquals(session.issuanceSessionId, restored.issuanceSessionId)
        assertEquals(session.status, restored.status)
        assertNull(restored.callbackConfig)
        assertEquals(session.state, restored.state)
        assertEquals(session.createdAt, restored.createdAt)
        assertEquals(session.lastUpdatedAt, restored.lastUpdatedAt)
        assertEquals(session.expiresAt, restored.expiresAt)
    }

    @Test
    fun roundtripWithCallback() {
        val callback =
            IssuanceCallbackConfig(
                url = "https://example.com/webhook",
                statuses =
                    listOf(
                        CredentialOfferSessionStatus.CREDENTIAL_ISSUED,
                        CredentialOfferSessionStatus.ERROR,
                    ),
                includeIssuanceData = true,
            )
        val session =
            CredentialOfferSession(
                correlationId = "corr-abc",
                offerId = "offer-def",
                status = CredentialOfferSessionStatus.TOKEN_REQUESTED,
                callbackConfig = callback,
                createdAt = 2000000L,
                lastUpdatedAt = 2500000L,
            )

        val entry = KvCredentialOfferSessionStore.CredentialOfferSessionEntry.fromPublic(session)
        val restored = entry.toPublic()

        assertEquals(session.correlationId, restored.correlationId)
        assertEquals(session.status, restored.status)
        assertNotNull(restored.callbackConfig)
        assertEquals("https://example.com/webhook", restored.callbackConfig!!.url)
        assertEquals(2, restored.callbackConfig!!.statuses.size)
        assertEquals(CredentialOfferSessionStatus.CREDENTIAL_ISSUED, restored.callbackConfig!!.statuses[0])
        assertEquals(CredentialOfferSessionStatus.ERROR, restored.callbackConfig!!.statuses[1])
        assertEquals(true, restored.callbackConfig!!.includeIssuanceData)
    }

    @Test
    fun unknownStatusFallsBackToError() {
        val entry =
            KvCredentialOfferSessionStore.CredentialOfferSessionEntry(
                correlationId = "corr-1",
                offerId = "offer-1",
                status = "UNKNOWN_STATUS",
                createdAt = 1000L,
                lastUpdatedAt = 1000L,
            )

        val session = entry.toPublic()
        assertEquals(CredentialOfferSessionStatus.ERROR, session.status)
    }

    @Test
    fun unknownCallbackStatusIsFiltered() {
        val entry =
            KvCredentialOfferSessionStore.CredentialOfferSessionEntry(
                correlationId = "corr-2",
                offerId = "offer-2",
                status = "CREDENTIAL_OFFER_CREATED",
                callbackUrl = "https://example.com/hook",
                callbackStatuses = listOf("CREDENTIAL_ISSUED", "NONEXISTENT_STATUS"),
                createdAt = 1000L,
                lastUpdatedAt = 1000L,
            )

        val session = entry.toPublic()
        assertNotNull(session.callbackConfig)
        assertEquals(1, session.callbackConfig!!.statuses.size)
        assertEquals(CredentialOfferSessionStatus.CREDENTIAL_ISSUED, session.callbackConfig!!.statuses[0])
    }

    @Test
    fun nullOptionalFields() {
        val session =
            CredentialOfferSession(
                correlationId = "corr-min",
                offerId = "offer-min",
                status = CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
                createdAt = 0L,
                lastUpdatedAt = 0L,
            )

        val entry = KvCredentialOfferSessionStore.CredentialOfferSessionEntry.fromPublic(session)
        val restored = entry.toPublic()

        assertNull(restored.issuanceSessionId)
        assertNull(restored.callbackConfig)
        assertNull(restored.state)
        assertNull(restored.expiresAt)
    }
}
