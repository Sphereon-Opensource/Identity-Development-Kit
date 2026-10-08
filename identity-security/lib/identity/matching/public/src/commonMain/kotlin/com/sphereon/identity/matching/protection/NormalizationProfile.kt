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

    /**
     * Trim surrounding whitespace and lowercase the whole value; identical transform to [EMAIL] but
     * named for its own purpose: canonicalizing a trust-anchor identifier value before it is blind
     * indexed into an enforcement match token (see `TrustAnchorMatchTokenComputer` in
     * `lib-trust-domain-service`). The token need not remain a resolvable identifier, only be
     * consistent across the anchor side and the enforcer side, so a blanket lowercase is safe here
     * even for identifier types (e.g. `did:key`, `did:jwk`) whose method-specific id is otherwise
     * case-significant.
     *
     * CRITICAL: any enforcer (verifier / wallet) that computes its own match token to compare against
     * one produced with this profile MUST normalize with this exact profile and the same
     * `IdentifierType` - a mismatched profile silently produces a different token and the match
     * always fails.
     */
    TRUST_MATCH_TOKEN,
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

        NormalizationProfile.EMAIL, NormalizationProfile.TRUST_MATCH_TOKEN -> {
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
