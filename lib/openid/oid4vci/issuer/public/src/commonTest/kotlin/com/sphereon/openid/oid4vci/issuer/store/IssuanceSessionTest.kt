/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.store

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuanceSessionTest {
    @Test
    fun lifecycleCorrelationIdDefaultsToNullAndRoundTrips() {
        val s =
            IssuanceSession(
                sessionId = "s1",
                issuerId = "iss",
                credentialConfigurationIds = listOf("PID"),
                status = IssuanceSessionStatus.OFFER_CREATED,
                createdAt = 0,
                expiresAt = 1,
            )
        assertNull(s.lifecycleCorrelationId)
        val linked = s.copy(lifecycleCorrelationId = "corr-1")
        val json = Json.encodeToString(IssuanceSession.serializer(), linked)
        assertTrue("lifecycleCorrelationId" in json)
        assertEquals(linked, Json.decodeFromString(IssuanceSession.serializer(), json))
        val nullJson = Json.encodeToString(IssuanceSession.serializer(), s)
        assertNull(Json.decodeFromString(IssuanceSession.serializer(), nullJson).lifecycleCorrelationId)
    }
}
