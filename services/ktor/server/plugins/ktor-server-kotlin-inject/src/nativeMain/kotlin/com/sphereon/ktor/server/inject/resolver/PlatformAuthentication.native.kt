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

package com.sphereon.ktor.server.inject.resolver

import io.ktor.server.application.ApplicationCall

/**
 * Native implementation of platform-specific authentication resolution.
 * Currently returns null as ktor-server-auth is not available on Native.
 * External developers can extend this with Native-specific auth mechanisms.
 */
internal actual fun resolvePlatformAuthentication(call: ApplicationCall): String? {
    // No platform-specific authentication available on Native
    // Users should rely on header-based authentication
    return null
}
