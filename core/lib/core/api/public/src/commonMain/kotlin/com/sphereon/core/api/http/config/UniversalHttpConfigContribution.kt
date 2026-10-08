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

/**
 * App-scoped configuration owned by one deployment component.
 *
 * [defaults] is nullable so the aggregator can distinguish a component that owns the global
 * defaults from one that only contributes adapter-specific overrides. Exactly one component may
 * own global defaults, and each adapter ID may be owned by exactly one contribution.
 */
data class UniversalHttpConfigContribution(
    val contributorId: String,
    val defaults: UniversalHttpDefaults? = null,
    val overrides: Map<String, UniversalHttpAdapterOverride> = emptyMap(),
) {
    init {
        require(contributorId.isNotBlank()) { "Universal HTTP config contributorId must not be blank" }
        require(overrides.keys.none { it.isBlank() }) {
            "Universal HTTP config contribution '$contributorId' has a blank adapter ID"
        }
    }
}
