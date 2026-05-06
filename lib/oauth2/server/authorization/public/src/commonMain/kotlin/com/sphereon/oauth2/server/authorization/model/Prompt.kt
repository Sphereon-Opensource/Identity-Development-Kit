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

package com.sphereon.oauth2.server.authorization.model

import kotlinx.serialization.Serializable

/**
 * OpenID Connect Core 1.0 §3.1.2.1 `prompt` parameter values. The wire format is a
 * space-separated list of these tokens; [parseSpaceSeparated] handles that decoding so callers
 * deal in a typed [Set] instead of raw strings. Unknown tokens are ignored at the parsing layer
 * because the OIDC spec leaves extension tokens to deployments and the server is not obliged to
 * reject them.
 */
@Serializable
enum class Prompt(
    val wireValue: String
) {
    NONE("none"),
    LOGIN("login"),
    CONSENT("consent"),
    SELECT_ACCOUNT("select_account"),
    ;

    companion object {
        /**
         * Decode the OIDC space-separated `prompt` value into a typed set. Returns an empty set
         * for `null`, blank input, or input that contains only unrecognised tokens.
         */
        fun parseSpaceSeparated(value: String?): Set<Prompt> {
            if (value.isNullOrBlank()) return emptySet()
            return value
                .split(' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token -> entries.firstOrNull { it.wireValue == token } }
                .toSet()
        }
    }
}
