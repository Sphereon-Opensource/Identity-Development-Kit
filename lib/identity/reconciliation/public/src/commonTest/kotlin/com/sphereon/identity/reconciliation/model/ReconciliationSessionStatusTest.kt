/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ReconciliationSessionStatusTest {

    @Test
    fun allStatusValuesExist() {
        val statuses = ReconciliationSessionStatus.entries
        assertEquals(7, statuses.size)
        assertEquals(
            setOf("CREATED", "REDIRECTED", "CALLBACK_RECEIVED", "COMPLETED", "EXPIRED", "CANCELLED", "ERROR"),
            statuses.map { it.name }.toSet()
        )
    }

    @Test
    fun serializationRoundTrip() {
        val json = Json
        ReconciliationSessionStatus.entries.forEach { status ->
            val serialized = json.encodeToString(ReconciliationSessionStatus.serializer(), status)
            val deserialized = json.decodeFromString(ReconciliationSessionStatus.serializer(), serialized)
            assertEquals(status, deserialized)
        }
    }
}
