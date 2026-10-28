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

package com.sphereon.crypto.key.persistence

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ManagedKeyReferencePublicationTest {
    @Test
    fun platformTenantCannotOwnTenantManagedPublication() {
        assertFailsWith<IllegalArgumentException> {
            ManagedKeyReferencePublication(
                tenantId = "platform",
                logicalResourceId = "resource",
                resourceHandle = "handle",
                logicalAlias = "alias",
                providerId = "provider",
                keyReferenceId = "00000000-0000-0000-0000-000000000001",
                publicKeyThumbprint = "A".repeat(43),
            )
        }
    }
}
