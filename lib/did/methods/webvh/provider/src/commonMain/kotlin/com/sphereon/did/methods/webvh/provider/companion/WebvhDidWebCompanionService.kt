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

package com.sphereon.did.methods.webvh.provider.companion

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.companion.WebvhDidWebCompanion
import com.sphereon.did.models.DidDocument
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json

/**
 * Session-scoped service that decides whether to populate the optional
 * `did:web` companion document on `did.webvh.create` / `update` /
 * `deactivate` outputs and produces the companion (DID document + canonical
 * JSON) when enabled.
 *
 * Per spec the companion is `MAY` — useful but optional. We default to
 * always populating because publishing both `did.jsonl` and `did.json` is
 * cheap, lets `did:web`-only resolvers see the latest state, and matches
 * the spec's encouragement. Operators that don't want the companion
 * generated set `did.webvh.publish-did-web-companion=false` in tenant or
 * principal config.
 */
@Inject
@SingleIn(SessionScope::class)
class WebvhDidWebCompanionService(
    private val configService: PrincipalConfigService,
) {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }

    /** Resolve config; default true when the key is absent or unparseable. */
    fun isEnabled(): Boolean =
        configService.getProperty(
            key = CONFIG_KEY_PUBLISH,
            targetType = Boolean::class,
            defaultValue = true,
        ) ?: true

    /**
     * Build the companion document + its canonical JSON serialisation, or
     * `null` when [isEnabled] is false.
     */
    fun buildIfEnabled(
        webvhDid: String,
        webvhDocument: DidDocument,
    ): BuiltDidWebCompanion? {
        if (!isEnabled()) {
            return null
        }
        val result = WebvhDidWebCompanion.build(webvhDid, webvhDocument)
        if (result.isErr) {
            return null
        }
        val doc = result.value
        return BuiltDidWebCompanion(
            didWebDocument = doc,
            didWebJson = json.encodeToString(DidDocument.serializer(), doc),
        )
    }

    data class BuiltDidWebCompanion(
        val didWebDocument: DidDocument,
        val didWebJson: String,
    )

    companion object {
        const val CONFIG_KEY_PUBLISH: String = "did.webvh.publish-did-web-companion"
    }
}
