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

package com.sphereon.did.persistence

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Produces unique identifiers for persistence records.
 *
 * Injected so tests can fix the sequence for deterministic golden-file assertions.
 * Production code uses [Uuid4Generator]. The plan specifies UUIDv7 for sortability, but
 * Kotlin Multiplatform does not yet expose v7 via `kotlin.uuid`; v4 is acceptable here
 * because sortability is a nice-to-have, not load-bearing.
 */
fun interface IdGenerator {
    fun next(): String
}

/**
 * Default generator — `kotlin.uuid.Uuid.random()` rendered as a lower-case, hyphenated UUIDv4 string.
 */
@OptIn(ExperimentalUuidApi::class)
object Uuid4Generator : IdGenerator {
    override fun next(): String = Uuid.random().toString()
}
