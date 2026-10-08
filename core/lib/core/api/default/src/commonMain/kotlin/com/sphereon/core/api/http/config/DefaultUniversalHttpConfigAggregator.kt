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
 * Combines deployment-authored HTTP configuration without graph replacement or merge order.
 * Conflicting ownership fails at AppScope construction instead of making routing classpath-order
 * dependent.
 */
object DefaultUniversalHttpConfigAggregator {
    fun aggregate(contributions: Set<UniversalHttpConfigContribution>): UniversalHttpConfig {
        val duplicateContributorIds =
            contributions
                .groupBy(UniversalHttpConfigContribution::contributorId)
                .filterValues { it.size > 1 }
                .keys
                .sorted()
        require(duplicateContributorIds.isEmpty()) {
            "Duplicate universal HTTP config contributor IDs: ${duplicateContributorIds.joinToString()}"
        }

        val defaultsOwners = contributions.filter { it.defaults != null }.sortedBy { it.contributorId }
        require(defaultsOwners.size <= 1) {
            "Multiple universal HTTP config contributors own global defaults: " +
                defaultsOwners.joinToString { it.contributorId }
        }

        val overrideOwners =
            contributions
                .flatMap { contribution ->
                    contribution.overrides.keys.map { adapterId -> adapterId to contribution.contributorId }
                }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
        val duplicateOverrideOwners =
            overrideOwners
                .entries
                .filter { (_, owners) -> owners.size > 1 }
                .sortedBy { (adapterId, _) -> adapterId }
        require(duplicateOverrideOwners.isEmpty()) {
            "Multiple universal HTTP config contributors own adapter overrides: " +
                duplicateOverrideOwners.joinToString { (adapterId, owners) ->
                    "$adapterId=[${owners.sorted().joinToString()}]"
                }
        }

        return UniversalHttpConfig(
            defaults = defaultsOwners.singleOrNull()?.defaults ?: UniversalHttpDefaults(),
            overrides =
                contributions
                    .sortedBy { it.contributorId }
                    .flatMap { it.overrides.entries }
                    .associate { it.toPair() },
        )
    }
}
