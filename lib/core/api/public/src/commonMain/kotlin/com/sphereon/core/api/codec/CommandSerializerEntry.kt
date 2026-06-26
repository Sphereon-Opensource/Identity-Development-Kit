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

package com.sphereon.core.api.codec

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * A compile-time carrier that pairs a `@Serializable` command input/output [kClass]
 * with its compile-time-resolved [serializer].
 *
 * These entries are aggregated via a Metro multibinding (`@ContributesIntoSet`) into a
 * single `Set<CommandSerializerEntry>` that the JSON codec turns into a
 * `Map<KClass<*>, KSerializer<*>>` for **zero-reflection** serializer lookup. This is
 * what makes command dispatch work under GraalVM native-image, where reflective
 * serializer resolution (`value::class.serializer()` / `serializer(kType)`) fails for
 * `@Serializable` types absent from the native serialization metadata.
 *
 * Entries are obtained at compile time via the generated `X.serializer()` companion,
 * never reflectively. See the IDK native command-serializer-registry design.
 *
 * @property kClass The runtime class of the serializable type.
 * @property serializer The compile-time `KSerializer` for that type.
 */
class CommandSerializerEntry(
    val kClass: KClass<*>,
    val serializer: KSerializer<*>,
)

/**
 * Declares that the aggregated `Set<CommandSerializerEntry>` may be empty.
 *
 * Metro requires `@Multibinds(allowEmpty = true)` for sets that can have zero
 * contributors on a given classpath. This makes it safe to inject the set into the
 * codec on every graph — graphs that contribute no entries simply get an empty set,
 * and the codec falls back to the existing reflective path.
 */
@ContributesTo(AppScope::class)
interface CommandSerializerEntryMultibinds {
    @Multibinds(allowEmpty = true)
    fun commandSerializerEntries(): Set<CommandSerializerEntry>
}
