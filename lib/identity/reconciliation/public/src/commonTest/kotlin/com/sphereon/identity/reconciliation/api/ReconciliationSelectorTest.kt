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
import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.AttributeProvenanceRef
import com.sphereon.identity.idv.model.AttributePredicate
import com.sphereon.identity.idv.model.MatchOperator
import com.sphereon.identity.reconciliation.model.FailClosed
import com.sphereon.identity.reconciliation.model.KnownHolderState
import com.sphereon.identity.reconciliation.model.ReconciliationDecision
import com.sphereon.identity.reconciliation.model.ReconciliationPlanTemplate
import com.sphereon.identity.reconciliation.model.ReconciliationSelectorInput
import com.sphereon.identity.reconciliation.model.ReconciliationSelectorRule
import com.sphereon.identity.reconciliation.model.RunIdv
import com.sphereon.identity.reconciliation.model.SkipReconciliation
import com.sphereon.identity.reconciliation.model.StepUp
import com.sphereon.identity.reconciliation.model.UseExistingBinding
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class ReconciliationSelectorTest {
    private val ruleVersion = "2026-03-17"

    private fun rule(
        id: String,
        priority: Int = 0,
        decision: ReconciliationDecision = ReconciliationDecision.RUN_IDV,
        providerId: String? = "surf",
        materialProfileId: String? = "holder-only-v1",
        enabled: Boolean = true,
        tenants: Set<String>? = null,
        queryIds: Set<String>? = null,
        credentialTypes: Set<String>? = null,
        knownHolderStates: Set<KnownHolderState>? = null,
        attributePredicates: List<AttributePredicate>? = null,
        dcqlCredentialQueryIds: Set<String>? = null,
        issuers: Set<String>? = null,
    ): ReconciliationSelectorRule =
        ReconciliationSelectorRule(
            id = id,
            priority = priority,
            enabled = enabled,
            tenants = tenants,
            queryIds = queryIds,
            credentialTypes = credentialTypes,
            knownHolderStates = knownHolderStates,
            attributePredicates = attributePredicates,
            dcqlCredentialQueryIds = dcqlCredentialQueryIds,
            issuers = issuers,
            plan =
                ReconciliationPlanTemplate(
                    decision = decision,
                    providerId = providerId,
                    materialProfileId = materialProfileId,
                ),
        )

    private fun input(
        tenantId: String = "t-1",
        queryId: String? = null,
        knownHolderState: KnownHolderState? = null,
        presentedCredentialTypes: Set<String> = emptySet(),
        dcqlCredentialQueryIds: Set<String> = emptySet(),
        issuers: Set<String> = emptySet(),
        availableAttributes: AttributeBag? = null,
    ): ReconciliationSelectorInput =
        ReconciliationSelectorInput(
            tenantId = tenantId,
            queryId = queryId,
            knownHolderState = knownHolderState,
            presentedCredentialTypes = presentedCredentialTypes,
            dcqlCredentialQueryIds = dcqlCredentialQueryIds,
            issuers = issuers,
            availableAttributes = availableAttributes,
        )

    @Test
    fun noRulesReturnsNull() {
        val result = ReconciliationSelector.evaluate(emptyList(), input(), ruleVersion)
        assertNull(result)
    }

    @Test
    fun singleMatchingRuleReturnsPlan() {
        val rules = listOf(rule(id = "r1"))
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertNotNull(result)
        assertIs<RunIdv>(result)
        assertEquals("surf", result.providerId)
        assertEquals(ruleVersion, result.selectorRuleVersion)
    }

    @Test
    fun disabledRulesAreSkipped() {
        val rules = listOf(rule(id = "r1", enabled = false))
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertNull(result)
    }

    @Test
    fun higherPriorityRuleWins() {
        val rules =
            listOf(
                rule(id = "low", priority = 10, providerId = "low-provider"),
                rule(id = "high", priority = 100, providerId = "high-provider"),
            )
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertIs<RunIdv>(result)
        assertEquals("high-provider", result.providerId)
    }

    @Test
    fun tiebreakByIdAscending() {
        val rules =
            listOf(
                rule(id = "b-rule", priority = 50, providerId = "b-provider"),
                rule(id = "a-rule", priority = 50, providerId = "a-provider"),
            )
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertIs<RunIdv>(result)
        assertEquals("a-provider", result.providerId)
    }

    @Test
    fun tenantScopingMatchesCorrectTenant() {
        val rules =
            listOf(
                rule(id = "r1", tenants = setOf("t-1"), providerId = "t1-provider"),
                rule(id = "r2", tenants = setOf("t-2"), providerId = "t2-provider"),
            )
        val result = ReconciliationSelector.evaluate(rules, input(tenantId = "t-1"), ruleVersion)
        assertIs<RunIdv>(result)
        assertEquals("t1-provider", result.providerId)
    }

    @Test
    fun tenantScopingExcludesNonMatchingTenant() {
        val rules =
            listOf(
                rule(id = "r1", tenants = setOf("t-2"), providerId = "t2-provider"),
            )
        val result = ReconciliationSelector.evaluate(rules, input(tenantId = "t-1"), ruleVersion)
        assertNull(result)
    }

    @Test
    fun nullTenantMatchesAnyTenant() {
        val rules =
            listOf(
                rule(id = "r1", tenants = null, providerId = "any-provider"),
            )
        val result = ReconciliationSelector.evaluate(rules, input(tenantId = "arbitrary-tenant"), ruleVersion)
        assertNotNull(result)
    }

    @Test
    fun queryIdMatching() {
        val rules =
            listOf(
                rule(id = "r1", queryIds = setOf("kw1c-enrollment"), providerId = "matched"),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(queryId = "kw1c-enrollment"),
                ruleVersion,
            )
        assertIs<RunIdv>(result)
        assertEquals("matched", result.providerId)
    }

    @Test
    fun queryIdNoMatch() {
        val rules =
            listOf(
                rule(id = "r1", queryIds = setOf("kw1c-enrollment")),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(queryId = "other-query"),
                ruleVersion,
            )
        assertNull(result)
    }

    @Test
    fun knownHolderStateMatching() {
        val rules =
            listOf(
                rule(
                    id = "fast-path",
                    priority = 100,
                    decision = ReconciliationDecision.USE_EXISTING_BINDING,
                    knownHolderStates = setOf(KnownHolderState.MATCHED_HOLDER_KEY),
                    providerId = null,
                    materialProfileId = null,
                ),
                rule(id = "default", priority = 0),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(knownHolderState = KnownHolderState.MATCHED_HOLDER_KEY),
                ruleVersion,
            )
        assertIs<UseExistingBinding>(result)
    }

    @Test
    fun claimTupleMatchRoutesToStepUp() {
        val rules =
            listOf(
                rule(
                    id = "step-up",
                    priority = 90,
                    decision = ReconciliationDecision.STEP_UP,
                    knownHolderStates = setOf(KnownHolderState.MATCHED_CLAIM_TUPLE),
                ),
                rule(id = "default", priority = 0),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(knownHolderState = KnownHolderState.MATCHED_CLAIM_TUPLE),
                ruleVersion,
            )
        assertIs<StepUp>(result)
    }

    @Test
    fun credentialTypeMatching() {
        val rules =
            listOf(
                rule(id = "eduid", credentialTypes = setOf("EduIDCredential"), providerId = "surf"),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(presentedCredentialTypes = setOf("EduIDCredential")),
                ruleVersion,
            )
        assertIs<RunIdv>(result)
        assertEquals("surf", result.providerId)
    }

    @Test
    fun dcqlCredentialQueryIdMatching() {
        val rules =
            listOf(
                rule(id = "r1", dcqlCredentialQueryIds = setOf("employee_credential"), providerId = "entra"),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(dcqlCredentialQueryIds = setOf("employee_credential", "student_credential")),
                ruleVersion,
            )
        assertIs<RunIdv>(result)
        assertEquals("entra", result.providerId)
    }

    @Test
    fun issuerRegexMatching() {
        val rules =
            listOf(
                rule(id = "r1", issuers = setOf(".*surfconext\\.nl.*"), providerId = "surf"),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(issuers = setOf("https://connect.surfconext.nl")),
                ruleVersion,
            )
        assertIs<RunIdv>(result)
        assertEquals("surf", result.providerId)
    }

    @Test
    fun claimPredicateMatching() {
        val claims =
            AttributeBag.of(
                values = mapOf(AttributePath("affiliation") to JsonPrimitive("student")),
                sourceId = AttributeProvenanceRef("test"),
                timestamp = Instant.fromEpochSeconds(0),
            )

        val rules =
            listOf(
                rule(
                    id = "student",
                    attributePredicates =
                        listOf(
                            AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student"),
                        ),
                    providerId = "surf",
                ),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(availableAttributes = claims),
                ruleVersion,
            )
        assertIs<RunIdv>(result)
        assertEquals("surf", result.providerId)
    }

    @Test
    fun claimPredicateNoMatchSkipsRule() {
        val claims =
            AttributeBag.of(
                values = mapOf(AttributePath("affiliation") to JsonPrimitive("employee")),
                sourceId = AttributeProvenanceRef("test"),
                timestamp = Instant.fromEpochSeconds(0),
            )

        val rules =
            listOf(
                rule(
                    id = "student",
                    attributePredicates =
                        listOf(
                            AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student"),
                        ),
                ),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(availableAttributes = claims),
                ruleVersion,
            )
        assertNull(result)
    }

    @Test
    fun skipReconciliationPlan() {
        val rules =
            listOf(
                rule(
                    id = "skip",
                    decision = ReconciliationDecision.SKIP_RECONCILIATION,
                    providerId = null,
                    materialProfileId = null,
                ),
            )
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertIs<SkipReconciliation>(result)
        assertEquals(ruleVersion, result.selectorRuleVersion)
    }

    @Test
    fun failClosedPlan() {
        val rules =
            listOf(
                ReconciliationSelectorRule(
                    id = "block",
                    plan =
                        ReconciliationPlanTemplate(
                            decision = ReconciliationDecision.FAIL_CLOSED,
                            failReason = "Policy forbids reconciliation",
                        ),
                ),
            )
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertIs<FailClosed>(result)
        assertEquals("Policy forbids reconciliation", result.reason)
    }

    @Test
    fun multiRuleMatchHighestPrioritySelected() {
        val claims =
            AttributeBag.of(
                values = mapOf(AttributePath("affiliation") to JsonPrimitive("student")),
                sourceId = AttributeProvenanceRef("test"),
                timestamp = Instant.fromEpochSeconds(0),
            )

        val rules =
            listOf(
                rule(
                    id = "fast-path",
                    priority = 100,
                    decision = ReconciliationDecision.USE_EXISTING_BINDING,
                    knownHolderStates = setOf(KnownHolderState.MATCHED_HOLDER_KEY),
                    providerId = null,
                    materialProfileId = null,
                ),
                rule(
                    id = "student-surf",
                    priority = 50,
                    attributePredicates = listOf(AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student")),
                    providerId = "surf",
                ),
                rule(id = "default", priority = 0, providerId = "default-provider"),
            )

        // NOT_FOUND holder, student claim -> matches student-surf (priority 50) and default (priority 0)
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(knownHolderState = KnownHolderState.NOT_FOUND, availableAttributes = claims),
                ruleVersion,
            )
        assertIs<RunIdv>(result)
        assertEquals("surf", result.providerId)
    }

    @Test
    fun selectorRuleVersionStampedOnPlan() {
        val rules = listOf(rule(id = "r1"))
        val result = ReconciliationSelector.evaluate(rules, input(), "v2026-03-17")
        assertNotNull(result)
        assertEquals("v2026-03-17", result.selectorRuleVersion)
    }

    @Test
    fun emptySetOnRuleTreatedAsUnconstrained() {
        // An empty set (not null) should be treated as "match any" — same as null
        val rules =
            listOf(
                ReconciliationSelectorRule(
                    id = "r1",
                    dcqlCredentialQueryIds = emptySet(),
                    plan =
                        ReconciliationPlanTemplate(
                            decision = ReconciliationDecision.RUN_IDV,
                            providerId = "surf",
                            materialProfileId = "holder-only-v1",
                        ),
                ),
            )
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(dcqlCredentialQueryIds = setOf("some-query")),
                ruleVersion,
            )
        assertNotNull(result, "Empty set constraint should not block matching")
    }

    @Test
    fun invalidIssuerRegexDoesNotCrash() {
        val rules =
            listOf(
                rule(id = "r1", issuers = setOf("[invalid-regex")),
            )
        // Should not throw — invalid regex is treated as non-match
        val result =
            ReconciliationSelector.evaluate(
                rules,
                input(issuers = setOf("https://example.com")),
                ruleVersion,
            )
        assertNull(result)
    }

    @Test
    fun requestedProjectionMatching() {
        val rules =
            listOf(
                rule(
                    id = "r1",
                    decision = ReconciliationDecision.SKIP_RECONCILIATION,
                    providerId = null,
                    materialProfileId = null,
                ),
            )
        // requestedProjection null on input with null requestedProjections on rule -> matches
        val result = ReconciliationSelector.evaluate(rules, input(), ruleVersion)
        assertNotNull(result)
    }
}
