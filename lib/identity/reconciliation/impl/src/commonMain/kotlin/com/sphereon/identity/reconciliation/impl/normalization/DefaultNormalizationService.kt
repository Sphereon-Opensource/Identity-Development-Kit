/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.identity.reconciliation.impl.normalization
import com.sphereon.identity.reconciliation.api.NormalizationService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NormalizationService>())
class DefaultNormalizationService : NormalizationService {
    override fun normalize(value: String, profileName: String): String = when (profileName) {
        "lowercase-trim" -> value.trim().lowercase()
        "exact" -> value
        "human-name-birth-v1" -> stripDiacritics(value.trim().lowercase())
        "email-lowercase-v1" -> value.trim().lowercase()
        else -> throw IllegalArgumentException("Unknown normalization profile: $profileName")
    }
    /**
     * Best-effort diacritic stripping in commonMain.
     * Replaces common accented characters with their ASCII equivalents.
     * A JVM-specific implementation could use java.text.Normalizer for full Unicode coverage.
     */
    private fun stripDiacritics(value: String): String {
        val replacements = mapOf(
            'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a",
            'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e",
            'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i",
            'ò' to "o", 'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o", 'ø' to "o",
            'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u",
            'ñ' to "n", 'ç' to "c", 'ý' to "y", 'ÿ' to "y",
            'ð' to "d", 'ß' to "ss",
        )
        return buildString {
            for (ch in value) {
                append(replacements[ch] ?: ch)
            }
        }
    }

    override fun supportedProfiles(): Set<String> =
        setOf("lowercase-trim", "exact", "human-name-birth-v1", "email-lowercase-v1")
}
