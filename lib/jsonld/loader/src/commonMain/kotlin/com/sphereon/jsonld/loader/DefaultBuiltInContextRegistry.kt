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

package com.sphereon.jsonld.loader

import com.sphereon.jsonld.loader.bundled.BundledContexts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * In-memory implementation of [BuiltInContextRegistry] backed by an immutable
 * map.
 *
 * The [entries] map is consumed verbatim. Callers SHOULD use the canonical
 * `@context` IRI as the key (matching the upstream specification's `id`
 * exactly), and the value SHOULD be the parsed JSON-LD document body.
 *
 * Construct directly for tests; for the AppScope DI binding use
 * [DefaultBuiltInContextRegistry], which exposes the W3C VCDM 2.0, W3C VC
 * Data Integrity v1/v2, and UN/CEFACT UNTP 0.7.0 vocabulary bundles.
 */
class MapBackedBuiltInContextRegistry(
    private val entries: Map<String, JsonObject>,
) : BuiltInContextRegistry {
    override fun get(iri: String): JsonObject? = entries[iri]

    override fun listIris(): Set<String> = entries.keys
}

/**
 * Default app-scoped [BuiltInContextRegistry] binding.
 *
 * Ships the canonical bundle defined by `built-in-contexts.json` in this
 * module's resources directory. Each entry's source bytes are SHA-256-pinned
 * at build time by the `generateBundledContexts` Gradle task; tampering with
 * a `.jsonld` file in the resources directory without updating the manifest
 * fails the build.
 *
 * Bundled IRIs (UNTP 0.7.0 critical path):
 *
 * - `https://www.w3.org/ns/credentials/v2` — W3C VCDM 2.0
 * - `https://w3id.org/security/data-integrity/v1` — VC Data Integrity 1.0
 * - `https://w3id.org/security/data-integrity/v2` — VC Data Integrity 1.1+
 * - `https://vocabulary.uncefact.org/untp/` — UN/CEFACT UNTP 0.7.0
 *   (shared `@context` across DPP, DCC, DTE, DFR, DIA)
 *
 * Tenant-specific or otherwise non-canonical contexts layer on through Metro
 * multibinding rather than mutating this registry.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, binding = binding<BuiltInContextRegistry>())
class DefaultBuiltInContextRegistry : BuiltInContextRegistry {
    private val entries: Map<String, JsonObject> =
        BundledContexts.ENTRIES.mapValues { (iri, raw) ->
            try {
                JSON.parseToJsonElement(raw) as JsonObject
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(
                    "Bundled JSON-LD context for <$iri> is not a JSON object",
                    e,
                )
            }
        }

    override fun get(iri: String): JsonObject? = entries[iri]

    override fun listIris(): Set<String> = entries.keys

    private companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
