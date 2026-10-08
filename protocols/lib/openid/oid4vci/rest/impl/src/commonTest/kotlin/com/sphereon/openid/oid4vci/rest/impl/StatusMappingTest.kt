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

import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.impl.command.GetCredentialOfferStatusServiceCommandImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusMappingTest {
    @Test
    fun offerCreatedMapsToCredentialOfferCreated() {
        assertEquals(
            CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.OFFER_CREATED),
        )
    }

    @Test
    fun offerReceivedMapsToCredentialOfferRetrieved() {
        assertEquals(
            CredentialOfferSessionStatus.CREDENTIAL_OFFER_RETRIEVED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.OFFER_RECEIVED),
        )
    }

    @Test
    fun tokenRequestedMapsToTokenRequested() {
        assertEquals(
            CredentialOfferSessionStatus.TOKEN_REQUESTED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.TOKEN_REQUESTED),
        )
    }

    @Test
    fun credentialRequestedMapsToCredentialRequested() {
        assertEquals(
            CredentialOfferSessionStatus.CREDENTIAL_REQUESTED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.CREDENTIAL_REQUESTED),
        )
    }

    @Test
    fun credentialIssuedMapsToCredentialIssued() {
        assertEquals(
            CredentialOfferSessionStatus.CREDENTIAL_ISSUED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.CREDENTIAL_ISSUED),
        )
    }

    @Test
    fun completedMapsToCredentialIssued() {
        assertEquals(
            CredentialOfferSessionStatus.CREDENTIAL_ISSUED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.COMPLETED),
        )
    }

    @Test
    fun deferredMapsToCredentialRequested() {
        assertEquals(
            CredentialOfferSessionStatus.CREDENTIAL_REQUESTED,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.DEFERRED),
        )
    }

    @Test
    fun expiredMapsToError() {
        assertEquals(
            CredentialOfferSessionStatus.ERROR,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.EXPIRED),
        )
    }

    @Test
    fun failedMapsToError() {
        assertEquals(
            CredentialOfferSessionStatus.ERROR,
            GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(IssuanceSessionStatus.FAILED),
        )
    }

    @Test
    fun allInternalStatusesMapped() {
        for (status in IssuanceSessionStatus.entries) {
            val mapped = GetCredentialOfferStatusServiceCommandImpl.mapInternalStatus(status)
            // Just verify it doesn't throw — all values should be handled
            assertTrue(mapped in CredentialOfferSessionStatus.entries)
        }
    }
}
