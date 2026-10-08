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

package com.sphereon.jsonld.command

import com.sphereon.jsonld.loader.bundled.BundledSchemas
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * In-memory [JsonLdSchemaRegistry] backed by an immutable map keyed by
 * `credentialType`. Construct directly for tests; production wiring uses the
 * AppScope-bound [DefaultJsonLdSchemaRegistry].
 */
class MapBackedJsonLdSchemaRegistry(
    private val entries: Map<String, JsonLdSchemaEntry>,
) : JsonLdSchemaRegistry {
    override fun get(credentialType: String): JsonLdSchemaEntry? = entries[credentialType]

    override fun listTypes(): Set<String> = entries.keys
}

/**
 * Default app-scoped [JsonLdSchemaRegistry] binding.
 *
 * Ships UNTP 0.7.0 JSON Schemas (`DigitalProductPassport`,
 * `DigitalConformityCredential`, `DigitalTraceabilityEvent`,
 * `DigitalFacilityRecord`, `DigitalIdentityAnchor`, `RegisteredIdentity`)
 * sourced from `https://untp.unece.org/artefacts/schema/v0.7.0/`. Each
 * schema's source bytes are SHA-256-pinned at build time by the
 * `generateBundledSchemas` Gradle task; tampering with a `.json` file in
 * `src/commonMain/resources/schemas/` without updating the manifest fails
 * the build.
 *
 * Custom or tenant-specific schemas layer on through Metro multibinding
 * rather than mutating this registry.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, binding = binding<JsonLdSchemaRegistry>())
class DefaultJsonLdSchemaRegistry : JsonLdSchemaRegistry {
    private val entries: Map<String, JsonLdSchemaEntry> =
        BundledSchemas.ENTRIES.mapValues { (credentialType, entry) ->
            val parsed: JsonElement =
                try {
                    JSON.parseToJsonElement(entry.body)
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException(
                        "Bundled JSON Schema for '$credentialType' (${entry.schemaUri}) is not valid JSON",
                        e,
                    )
                }
            jsonLdSchemaEntry(schemaUri = entry.schemaUri, schema = parsed)
        }

    override fun get(credentialType: String): JsonLdSchemaEntry? = entries[credentialType]

    override fun listTypes(): Set<String> = entries.keys

    private companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
