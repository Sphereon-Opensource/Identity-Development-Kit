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

package com.sphereon.identity.matching.protection

import com.sphereon.core.compat.JsExportCompat

/**
 * Canonicalization profile applied to an identifier value before it is protected.
 *
 * Normalization makes blind indexing deterministic across equivalent representations of
 * the same logical identifier (for example "Foo@Example.com" and "foo@example.com" must
 * yield the same blind index). The profile is chosen per identifier type by the
 * [IdentifierProtectionPolicy].
 */
@JsExportCompat
enum class NormalizationProfile {
    /** No transformation; the value is used verbatim. */
    NONE,

    /** Trim surrounding whitespace and lowercase the whole address. */
    EMAIL,

    /** Strip every character except digits, preserving a single leading "+". */
    PHONE_E164,

    /** Lowercase the value and remove a single trailing slash. */
    URL_HOST,

    /** Decentralized identifiers are case-sensitive; used verbatim. */
    DID,
}

/**
 * Pure, multiplatform-safe canonicalization of an identifier [value] according to [profile].
 *
 * Contains no platform APIs so it is identical on every Kotlin target. The transformation is
 * deliberately conservative: it only removes representational noise (case, whitespace, a
 * trailing slash, phone formatting), never the semantic content of the identifier.
 */
fun normalizeIdentifier(
    value: String,
    profile: NormalizationProfile,
): String =
    when (profile) {
        NormalizationProfile.NONE -> {
            value
        }

        NormalizationProfile.DID -> {
            value
        }

        NormalizationProfile.EMAIL -> {
            value.trim().lowercase()
        }

        NormalizationProfile.URL_HOST -> {
            val lowered = value.lowercase()
            if (lowered.endsWith("/")) lowered.dropLast(1) else lowered
        }

        NormalizationProfile.PHONE_E164 -> {
            val hasPlus = value.trimStart().startsWith("+")
            val digits = value.filter { it.isDigit() }
            if (hasPlus) "+$digits" else digits
        }
    }
