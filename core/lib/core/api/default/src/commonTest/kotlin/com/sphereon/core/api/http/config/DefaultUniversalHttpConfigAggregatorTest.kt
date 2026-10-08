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

package com.sphereon.core.api.http.config

import com.sphereon.core.api.http.describe.TenantPathMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultUniversalHttpConfigAggregatorTest {
    @Test
    fun emptyContributionsProduceCanonicalDefaults() {
        assertEquals(
            UniversalHttpConfig.DEFAULT,
            DefaultUniversalHttpConfigAggregator.aggregate(emptySet()),
        )
    }

    @Test
    fun disjointContributionsAreCombinedDeterministically() {
        val defaults = UniversalHttpDefaults(serverPrefix = "/api", tenantPathMode = TenantPathMode.BOTH)
        val config =
            DefaultUniversalHttpConfigAggregator.aggregate(
                setOf(
                    UniversalHttpConfigContribution(
                        contributorId = "platform",
                        overrides = mapOf("SETUP" to UniversalHttpAdapterOverride(enabled = false)),
                    ),
                    UniversalHttpConfigContribution(
                        contributorId = "defaults",
                        defaults = defaults,
                        overrides = mapOf("TRUST" to UniversalHttpAdapterOverride(adapterBasePath = "/trust")),
                    ),
                ),
            )

        assertEquals(defaults, config.defaults)
        assertEquals(setOf("SETUP", "TRUST"), config.overrides.keys)
        assertEquals(false, config.overrides.getValue("SETUP").enabled)
        assertEquals("/trust", config.overrides.getValue("TRUST").adapterBasePath)
    }

    @Test
    fun duplicateAdapterOverrideOwnershipFailsClosed() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                DefaultUniversalHttpConfigAggregator.aggregate(
                    setOf(
                        UniversalHttpConfigContribution(
                            contributorId = "first",
                            overrides = mapOf("TRUST" to UniversalHttpAdapterOverride(enabled = false)),
                        ),
                        UniversalHttpConfigContribution(
                            contributorId = "second",
                            overrides = mapOf("TRUST" to UniversalHttpAdapterOverride(enabled = true)),
                        ),
                    ),
                )
            }

        assertEquals(
            "Multiple universal HTTP config contributors own adapter overrides: TRUST=[first, second]",
            error.message,
        )
    }

    @Test
    fun multipleDefaultsOwnersFailClosed() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                DefaultUniversalHttpConfigAggregator.aggregate(
                    setOf(
                        UniversalHttpConfigContribution("first", defaults = UniversalHttpDefaults()),
                        UniversalHttpConfigContribution(
                            "second",
                            defaults = UniversalHttpDefaults(serverPrefix = "/api"),
                        ),
                    ),
                )
            }

        assertEquals(
            "Multiple universal HTTP config contributors own global defaults: first, second",
            error.message,
        )
    }
}
