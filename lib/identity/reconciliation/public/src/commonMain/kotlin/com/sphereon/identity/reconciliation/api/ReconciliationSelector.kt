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

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.identity.idv.model.AttributePredicate
import com.sphereon.identity.idv.model.AttributePredicateEvaluator
import com.sphereon.identity.reconciliation.model.BindingPolicy
import com.sphereon.identity.reconciliation.model.FailClosed
import com.sphereon.identity.reconciliation.model.KnownHolderState
import com.sphereon.identity.reconciliation.model.ReconciliationDecision
import com.sphereon.identity.reconciliation.model.ReconciliationPlan
import com.sphereon.identity.reconciliation.model.ReconciliationPlanTemplate
import com.sphereon.identity.reconciliation.model.ReconciliationSelectorInput
import com.sphereon.identity.reconciliation.model.ReconciliationSelectorRule
import com.sphereon.identity.reconciliation.model.RunIdv
import com.sphereon.identity.reconciliation.model.SkipReconciliation
import com.sphereon.identity.reconciliation.model.StepUp
import com.sphereon.identity.reconciliation.model.UseExistingBinding

object ReconciliationSelector {
    fun evaluate(
        rules: List<ReconciliationSelectorRule>,
        input: ReconciliationSelectorInput,
        ruleVersion: String,
    ): ReconciliationPlan? =
        rules
            .filter { it.enabled }
            .filter { rule -> matches(rule, input) }
            .sortedWith(compareByDescending<ReconciliationSelectorRule> { it.priority }.thenBy { it.id })
            .firstOrNull()
            ?.let { resolve(it.plan, ruleVersion) }

    private fun matches(
        rule: ReconciliationSelectorRule,
        input: ReconciliationSelectorInput,
    ): Boolean =
        matchesTenants(rule.tenants, input.tenantId) &&
            matchesSet(rule.entryPointTypes, input.entryPointType) &&
            matchesSet(rule.triggerTypes, input.triggerType) &&
            matchesSet(rule.queryIds, input.queryId) &&
            matchesAnyOf(rule.dcqlCredentialQueryIds, input.dcqlCredentialQueryIds) &&
            matchesAnyOf(rule.dcqlCredentialSetRefs, input.dcqlCredentialSetRefs) &&
            matchesAnyOf(rule.credentialTypes, input.presentedCredentialTypes) &&
            matchesIssuers(rule.issuers, input.issuers) &&
            matchesKnownHolder(rule.knownHolderStates, input.knownHolderState) &&
            matchesAnyOf(rule.requestedProjections, input.requestedProjection?.let { setOf(it) } ?: emptySet()) &&
            matchesPredicates(rule.attributePredicates, input.availableAttributes)

    private fun matchesTenants(
        expected: Set<String>?,
        tenantId: String,
    ): Boolean = expected == null || expected.contains(tenantId)

    private fun matchesSet(
        expected: Set<String>?,
        actual: String?,
    ): Boolean = expected == null || (actual != null && expected.contains(actual))

    private fun matchesAnyOf(
        expected: Set<String>?,
        actual: Set<String>,
    ): Boolean = expected.isNullOrEmpty() || expected.any { actual.contains(it) }

    private fun matchesIssuers(
        patterns: Set<String>?,
        issuers: Set<String>,
    ): Boolean =
        patterns == null ||
            patterns.any { pattern ->
                issuers.any { issuer ->
                    try {
                        Regex(pattern).containsMatchIn(issuer)
                    } catch (_: Throwable) {
                        // Invalid regex — treat as non-match rather than crashing rule evaluation
                        false
                    }
                }
            }

    private fun matchesKnownHolder(
        expected: Set<KnownHolderState>?,
        actual: KnownHolderState?,
    ): Boolean = expected == null || (actual != null && expected.contains(actual))

    private fun matchesPredicates(
        predicates: List<AttributePredicate>?,
        attributes: AttributeBag?,
    ): Boolean = predicates.isNullOrEmpty() || (attributes != null && AttributePredicateEvaluator.evaluate(predicates, attributes))

    private fun resolve(
        template: ReconciliationPlanTemplate,
        ruleVersion: String,
    ): ReconciliationPlan =
        when (template.decision) {
            ReconciliationDecision.SKIP_RECONCILIATION -> {
                SkipReconciliation(
                    selectorRuleVersion = ruleVersion,
                )
            }

            ReconciliationDecision.USE_EXISTING_BINDING -> {
                UseExistingBinding(
                    requiredAttributeNames = template.requiredAttributeNames,
                    materialProfileId = template.materialProfileId,
                    selectorRuleVersion = ruleVersion,
                )
            }

            ReconciliationDecision.RUN_IDV -> {
                RunIdv(
                    providerId = template.providerId ?: error("RunIdv requires providerId"),
                    methodId = template.methodId,
                    materialProfileId = template.materialProfileId ?: error("RunIdv requires materialProfileId"),
                    requiredAttributeNames = template.requiredAttributeNames,
                    minimumAssurance = template.minimumAssurance,
                    bindingPolicy = template.bindingPolicy,
                    selectorRuleVersion = ruleVersion,
                )
            }

            ReconciliationDecision.STEP_UP -> {
                StepUp(
                    providerId = template.providerId ?: error("StepUp requires providerId"),
                    methodId = template.methodId,
                    materialProfileId = template.materialProfileId ?: error("StepUp requires materialProfileId"),
                    requiredAttributeNames = template.requiredAttributeNames,
                    selectorRuleVersion = ruleVersion,
                )
            }

            ReconciliationDecision.FAIL_CLOSED -> {
                FailClosed(
                    reason = template.failReason ?: "Policy requires reconciliation but no valid plan could be resolved",
                    selectorRuleVersion = ruleVersion,
                )
            }
        }
}
