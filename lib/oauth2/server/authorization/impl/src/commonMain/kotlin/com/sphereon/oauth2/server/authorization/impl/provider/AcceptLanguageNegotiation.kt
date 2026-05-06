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

package com.sphereon.oauth2.server.authorization.impl.provider

/**
 * RFC 7231 §5.3.5 Accept-Language negotiation.
 *
 * Parses an Accept-Language header into an ordered list of language tags by descending
 * q-value, drops `q=0` rejections, and lower-cases tags. Tags are language-tag prefixes
 * suitable for matching against a supported-locales set (`nl-NL` matches `nl`,
 * `en-US` matches `en`).
 *
 * Negotiation picks the first supported tag that prefixes one of the parsed entries.
 * When nothing matches (or the header is null/blank), [defaultLocale] is returned.
 */
object AcceptLanguageNegotiation {
    private const val DEFAULT_Q_VALUE: Double = 1.0
    private const val Q_PARAM_PREFIX: String = "q="

    /**
     * Parse [header] into language-tag preferences ordered by descending q-value. Tags with
     * `q=0` are dropped (RFC 7231: explicit rejection). Order on ties preserves the original
     * appearance, matching the RFC's "preserve the order in which they were sent" guidance
     * for tied weights.
     */
    fun parse(header: String?): List<String> {
        if (header.isNullOrBlank()) return emptyList()
        val tokens = header.split(',')
        val parsed =
            tokens.mapIndexedNotNull { index, raw ->
                val parts = raw.split(';')
                val tag =
                    parts
                        .firstOrNull()
                        ?.trim()
                        ?.lowercase()
                        .orEmpty()
                if (tag.isEmpty()) return@mapIndexedNotNull null
                val q =
                    parts
                        .drop(1)
                        .map { it.trim() }
                        .firstOrNull { it.startsWith(Q_PARAM_PREFIX, ignoreCase = true) }
                        ?.removePrefix(Q_PARAM_PREFIX)
                        ?.removePrefix("Q=")
                        ?.toDoubleOrNull()
                        ?: DEFAULT_Q_VALUE
                if (q <= 0.0) null else Triple(tag, q, index)
            }
        return parsed
            .sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    /**
     * Match the parsed [preferences] against [supportedLocales] by language-tag prefix
     * (`nl-NL` matches `nl`). The first preference with a matching supported entry wins.
     * When no preference matches, [defaultLocale] is returned.
     */
    fun negotiate(
        header: String?,
        supportedLocales: Set<String>,
        defaultLocale: String,
    ): String {
        val preferences = parse(header)
        for (pref in preferences) {
            val primary = pref.substringBefore('-')
            if (primary in supportedLocales) return primary
            if (pref in supportedLocales) return pref
        }
        return defaultLocale
    }
}
