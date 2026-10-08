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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.identity.reconciliation.model.CanonicalAttributeBag
import com.sphereon.identity.reconciliation.model.CanonicalAttributeRule
import com.sphereon.identity.reconciliation.model.CanonicalMergeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class RequiredAttributeViolation
    @JvmOverloads
    constructor(
        val canonicalName: String,
        val expectedSourceHint: String? = null,
    )

fun validateRequiredAttributes(
    canonical: CanonicalAttributeBag,
    rules: List<CanonicalAttributeRule>,
): List<RequiredAttributeViolation> =
    rules
        .filter { it.required }
        .filter { rule -> canonical.attributes[rule.canonicalName] == null }
        .map { rule ->
            RequiredAttributeViolation(
                canonicalName = rule.canonicalName,
                expectedSourceHint =
                    when (rule.mergeMode) {
                        CanonicalMergeMode.WALLET_ONLY -> "wallet"
                        CanonicalMergeMode.OIDC_ONLY -> "oidc"
                        else -> "any"
                    },
            )
        }

fun attributesToProject(
    canonical: CanonicalAttributeBag,
    rules: List<CanonicalAttributeRule>,
): Map<String, JsonElement> {
    val projectable = rules.filter { it.project }.map { it.canonicalName }.toSet()
    return canonical.attributes.filterKeys { it in projectable }
}

fun attributesToPersist(
    canonical: CanonicalAttributeBag,
    rules: List<CanonicalAttributeRule>,
): Map<String, JsonElement> {
    val persistable = rules.filter { it.persist }.map { it.canonicalName }.toSet()
    return canonical.attributes.filterKeys { it in persistable }
}
