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

package com.sphereon.crypto.kms.rest.server.adapter

import kotlinx.serialization.json.Json

/**
 * Singleton configuration for JSON serialization.
 *
 * This provides a shared Json instance used by all HttpAdapters.
 * Using a singleton avoids the need to inject Json via DI, which simplifies
 * the kotlin-inject setup (no need to provide Json in graph).
 *
 * Configuration:
 * - ignoreUnknownKeys: true - Tolerant of extra fields in JSON
 * - prettyPrint: false - Compact JSON for network efficiency
 */
object JsonConfig {
    val instance: Json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
}
