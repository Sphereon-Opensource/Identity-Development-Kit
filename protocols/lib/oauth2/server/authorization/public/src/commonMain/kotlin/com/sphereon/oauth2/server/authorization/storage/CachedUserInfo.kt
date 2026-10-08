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

package com.sphereon.oauth2.server.authorization.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * Upstream-sourced user claims, cached after federation completes so downstream
 * `getUserInfo` calls do not re-hit the upstream `/userinfo` endpoint within the TTL window.
 *
 * Claims are carried as [JsonElement] rather than `Map<String, Any>` so the cache value
 * survives `kotlinx.serialization` round-trips cleanly. Writers therefore wrap raw primitives
 * in `JsonPrimitive` (and lists/maps in `JsonArray` / `JsonObject`) at insertion time.
 */
@Serializable
data class CachedUserInfo(
    val userId: String,
    val claims: Map<String, JsonElement>,
    val cachedAt: Instant,
)
