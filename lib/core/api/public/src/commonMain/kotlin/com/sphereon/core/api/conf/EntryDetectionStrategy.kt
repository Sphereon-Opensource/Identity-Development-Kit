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
 *
 */

package com.sphereon.core.api.conf

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Strategy for detecting configuration entry IDs from property keys.
 *
 * Entry detection strategies determine how to identify distinct configuration entries
 * within a set of property keys. Different strategies support different configuration
 * patterns (e.g., type discriminators, top-level grouping).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EntryDetectionStrategy", exact = true)
fun interface EntryDetectionStrategy {
    /**
     * Detects entry IDs from a set of property keys.
     *
     * @param propertyKeys All property keys under the config prefix (after stripPrefix)
     * @return Set of detected entry IDs
     */
    fun detectEntryIds(propertyKeys: Set<String>): Set<String>
}

/**
 * Detects entries by looking for a discriminator suffix (e.g., ".type").
 *
 * This is the pattern used by KMS providers where each entry has a `type` field
 * that determines which concrete class to deserialize into.
 *
 * Example: For properties like:
 * - `software-primary.type = software`
 * - `software-primary.keystore.type = pkcs12`
 * - `rest-provider.type = rest`
 *
 * With `discriminatorSuffix = ".type"` and `excludedSuffixes = setOf(".keystore.type")`,
 * this will detect entry IDs: `["software-primary", "rest-provider"]`
 *
 * @param discriminatorSuffix The suffix that identifies the type discriminator (default: ".type")
 * @param excludedSuffixes Suffixes to exclude from detection (e.g., nested type fields like ".keystore.type")
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TypeSuffixEntryDetection", exact = true)
class TypeSuffixEntryDetection(
    private val discriminatorSuffix: String = ".type",
    private val excludedSuffixes: Set<String> = emptySet(),
) : EntryDetectionStrategy {
    override fun detectEntryIds(propertyKeys: Set<String>): Set<String> =
        propertyKeys
            .filter { key ->
                key.endsWith(discriminatorSuffix) &&
                    excludedSuffixes.none { excluded -> key.endsWith(excluded) }
            }.map { key ->
                // Remove the discriminator suffix to get the entry ID
                key.substringBefore(discriminatorSuffix).removePrefix(".")
            }.filter { it.isNotEmpty() }
            .toSet()
}

/**
 * Detects entries by top-level key (standard grouping).
 *
 * This is what ConfigBinder.getConfigMap() currently does - it groups properties
 * by their top-level key segment.
 *
 * Example: For properties like:
 * - `primary.host = localhost`
 * - `primary.port = 5432`
 * - `replica.host = replica.db.com`
 *
 * This will detect entry IDs: `["primary", "replica"]`
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TopLevelKeyEntryDetection", exact = true)
class TopLevelKeyEntryDetection : EntryDetectionStrategy {
    override fun detectEntryIds(propertyKeys: Set<String>): Set<String> =
        propertyKeys
            .map { key -> key.substringBefore('.') }
            .filter { it.isNotEmpty() }
            .toSet()
}
