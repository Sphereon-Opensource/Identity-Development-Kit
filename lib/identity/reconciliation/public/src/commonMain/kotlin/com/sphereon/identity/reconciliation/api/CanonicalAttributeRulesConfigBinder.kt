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

package com.sphereon.identity.reconciliation.api

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.identity.reconciliation.model.CanonicalAttributeRule
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode

/**
 * Binds canonical attribute rules from configuration via [PropertyResolver].
 *
 * Uses indexed list format:
 * ```
 * identity.reconciliation.attribute-rules[0].canonical-name=given_name
 * identity.reconciliation.attribute-rules[0].merge-mode=OIDC_WINS
 * identity.reconciliation.attribute-rules[0].required=true
 * identity.reconciliation.attribute-rules[0].persist=true
 * identity.reconciliation.attribute-rules[0].project=true
 * identity.reconciliation.attribute-rules[4].source-aliases.surf=sub
 * ```
 *
 * @param configService The property resolver to read configuration from
 * @param knownProviderIds Provider IDs to check for source-aliases (avoids needing a `names` sub-key)
 * @param prefix Config prefix (default: `identity.reconciliation.attribute-rules`)
 */
class CanonicalAttributeRulesConfigBinder(
    private val configService: PropertyResolver,
    private val knownProviderIds: Set<String> = emptySet(),
    private val prefix: String = PREFIX,
) {
    companion object {
        const val PREFIX = "identity.reconciliation.attribute-rules"
    }

    fun bind(): List<CanonicalAttributeRule> {
        val rules = mutableListOf<CanonicalAttributeRule>()
        var index = 0
        while (true) {
            val rulePrefix = "$prefix[$index]"
            val canonicalName =
                configService.getPropertyAsString("$rulePrefix.canonical-name", null)
                    ?: break

            val mergeModeStr =
                configService
                    .getPropertyAsString("$rulePrefix.merge-mode", null)
                    ?.replace('-', '_')
                    ?.uppercase()
                    ?: error("Missing 'merge-mode' for canonical attribute rule: $canonicalName (at index $index)")

            val mergeMode =
                runCatching { CanonicalMergeMode.valueOf(mergeModeStr) }.getOrElse {
                    error(
                        "Invalid merge-mode '$mergeModeStr' for attribute rule '$canonicalName'. " +
                            "Allowed: ${CanonicalMergeMode.entries.joinToString(", ") { it.name }}",
                    )
                }

            rules.add(
                CanonicalAttributeRule(
                    canonicalName = canonicalName,
                    mergeMode = mergeMode,
                    required = configService.getPropertyAsString("$rulePrefix.required", "false")?.toBoolean() == true,
                    persist = configService.getPropertyAsString("$rulePrefix.persist", "false")?.toBoolean() == true,
                    project = configService.getPropertyAsString("$rulePrefix.project", "false")?.toBoolean() == true,
                    sourceAliases = readSourceAliases(rulePrefix),
                ),
            )
            index++
        }

        // Validate uniqueness
        val duplicates = rules.groupBy { it.canonicalName }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate canonical-name(s) in attribute rules: ${duplicates.joinToString(", ")}"
        }

        return rules
    }

    private fun readSourceAliases(rulePrefix: String): Map<String, String> {
        val aliasPrefix = "$rulePrefix.source-aliases"
        return knownProviderIds
            .mapNotNull { providerId ->
                configService
                    .getPropertyAsString("$aliasPrefix.$providerId", null)
                    ?.let { providerId to it }
            }.toMap()
    }
}
