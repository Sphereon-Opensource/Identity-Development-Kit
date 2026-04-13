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
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides the default [StreamingCodecRegistry] with [JsonStreamingCodec] as the default codec,
 * and the default [MediaTypeNegotiator] implementation.
 *
 * Additional codecs (Protobuf, CBOR) can be registered by injecting
 * `Set<StreamingCodec>` from multibinding.
 */
@ContributesTo(AppScope::class)
interface DefaultStreamingCodecRegistryGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideStreamingCodecRegistry(
        jsonCodec: JsonStreamingCodec,
        additionalCodecs: Set<StreamingCodec>,
    ): StreamingCodecRegistry {
        val registry = DefaultStreamingCodecRegistry(jsonCodec)
        // Register additional codecs (excluding the default JSON codec to avoid duplicates)
        additionalCodecs
            .filter { it !== jsonCodec }
            .forEach { registry.register(it) }
        return registry
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaTypeNegotiator(codecRegistry: StreamingCodecRegistry): MediaTypeNegotiator = DefaultMediaTypeNegotiator(codecRegistry)
}
