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

package com.sphereon.crypto.kms.provider.azure

internal const val KEY_NAME_VERSION_SEP = ":"

/**
 * Parses a Key ID (kid) to extract the Azure Key Vault key name and version.
 * Handles both versioned and unversioned key references.
 *
 * @param kid The key identifier to parse
 * @return A Pair containing the key name and version (empty string if no version)
 */
fun kidToKVKeyName(kid: String): Pair<String, String> {
    val versioned = AZURE_VERSIONED_KEY_ID.matchEntire(kid)
    if (versioned != null) {
        return versioned.groupValues[1] to versioned.groupValues[2]
    }
    if (!kid.contains(KEY_NAME_VERSION_SEP)) {
        return Pair(kid, "")
    }

    val parts = kid.split(KEY_NAME_VERSION_SEP)
    val name = parts[0]
    val version = if (parts.size > 1) parts[1] else ""

    if (version.lowercase() == "latest") {
        return Pair(name, "")
    }
    return Pair(name, version)
}

private val AZURE_VERSIONED_KEY_ID =
    Regex("^https://[A-Za-z0-9.-]+(?::[0-9]{1,5})?/keys/([A-Za-z0-9-]{1,127})/([A-Za-z0-9]{1,128})$")
