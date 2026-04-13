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

package com.sphereon.identity.reconciliation.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface ReconciliationPlan {
    val materialProfileId: String?
    val requiredAttributeNames: Set<String>
    val selectorRuleVersion: String
}

@Serializable
data class UseExistingBinding(
    override val requiredAttributeNames: Set<String> = emptySet(),
    override val materialProfileId: String? = null,
    override val selectorRuleVersion: String,
) : ReconciliationPlan

@Serializable
data class RunIdv(
    val providerId: String,
    val methodId: String? = null,
    override val materialProfileId: String,
    override val requiredAttributeNames: Set<String> = emptySet(),
    val minimumAssurance: String? = null,
    val bindingPolicy: BindingPolicy = BindingPolicy.REUSE_OR_CREATE,
    override val selectorRuleVersion: String,
) : ReconciliationPlan

@Serializable
data class StepUp(
    val providerId: String,
    val methodId: String? = null,
    override val materialProfileId: String,
    override val requiredAttributeNames: Set<String> = emptySet(),
    override val selectorRuleVersion: String,
) : ReconciliationPlan

@Serializable
data class FailClosed(
    val reason: String,
    override val materialProfileId: String? = null,
    override val requiredAttributeNames: Set<String> = emptySet(),
    override val selectorRuleVersion: String,
) : ReconciliationPlan

@Serializable
data class SkipReconciliation(
    override val selectorRuleVersion: String,
) : ReconciliationPlan {
    override val materialProfileId: String? = null
    override val requiredAttributeNames: Set<String> = emptySet()
}

@Serializable
enum class BindingPolicy {
    REUSE_OR_CREATE,
    CREATE_NEW,
    REUSE_ONLY,
}
