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

package com.sphereon.identity.idv.model

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.attribute.flow.AttributePath
import com.sphereon.attribute.flow.AttributeProvenanceRef
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AttributePredicateEvaluatorTest {
    private fun bagOf(vararg pairs: Pair<String, String>): AttributeBag =
        AttributeBag.of(
            values = pairs.associate { (key, value) -> AttributePath(key) to JsonPrimitive(value) },
            sourceId = AttributeProvenanceRef("test"),
            timestamp = Instant.fromEpochSeconds(0),
        )

    @Test
    fun existsReturnsTrueWhenAttributePresent() {
        val attributes = bagOf("email" to "user@example.com")
        val predicate = AttributePredicate(AttributePath("email"), MatchOperator.EXISTS)
        assertTrue(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun existsReturnsFalseWhenAttributeAbsent() {
        val attributes = bagOf("name" to "test")
        val predicate = AttributePredicate(AttributePath("email"), MatchOperator.EXISTS)
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun notEmptyReturnsTrueForNonBlankValue() {
        val attributes = bagOf("name" to "Alice")
        val predicate = AttributePredicate(AttributePath("name"), MatchOperator.NOT_EMPTY)
        assertTrue(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun notEmptyReturnsFalseForBlankValue() {
        val attributes = bagOf("name" to "  ")
        val predicate = AttributePredicate(AttributePath("name"), MatchOperator.NOT_EMPTY)
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun notEmptyReturnsFalseForMissingAttribute() {
        val attributes = AttributeBag.empty()
        val predicate = AttributePredicate(AttributePath("name"), MatchOperator.NOT_EMPTY)
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun equalsMatchesExactValue() {
        val attributes = bagOf("affiliation" to "student")
        val predicate = AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student")
        assertTrue(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun equalsDoesNotMatchDifferentValue() {
        val attributes = bagOf("affiliation" to "employee")
        val predicate = AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student")
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun equalsReturnsFalseWhenAttributeMissing() {
        val attributes = AttributeBag.empty()
        val predicate = AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student")
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun containsMatchesSubstring() {
        val attributes = bagOf("email" to "user@example.com")
        val predicate = AttributePredicate(AttributePath("email"), MatchOperator.CONTAINS, "@example.com")
        assertTrue(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun containsDoesNotMatchMissingSubstring() {
        val attributes = bagOf("email" to "user@other.com")
        val predicate = AttributePredicate(AttributePath("email"), MatchOperator.CONTAINS, "@example.com")
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun regexMatchesPattern() {
        val attributes = bagOf("email" to "user@example.com")
        val predicate = AttributePredicate(AttributePath("email"), MatchOperator.REGEX, "^[^@]+@example\\.com$")
        assertTrue(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun regexDoesNotMatchNonMatchingPattern() {
        val attributes = bagOf("email" to "user@other.com")
        val predicate = AttributePredicate(AttributePath("email"), MatchOperator.REGEX, "^[^@]+@example\\.com$")
        assertFalse(AttributePredicateEvaluator.evaluate(listOf(predicate), attributes))
    }

    @Test
    fun emptyPredicateListAlwaysMatches() {
        val attributes = bagOf("name" to "test")
        assertTrue(AttributePredicateEvaluator.evaluate(emptyList(), attributes))
    }

    @Test
    fun multiplePredicatesAllMustMatch() {
        val attributes = bagOf("affiliation" to "student", "email" to "user@example.com")
        val predicates =
            listOf(
                AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student"),
                AttributePredicate(AttributePath("email"), MatchOperator.EXISTS),
            )
        assertTrue(AttributePredicateEvaluator.evaluate(predicates, attributes))
    }

    @Test
    fun multiplePredicatesFailsIfOneDoesNotMatch() {
        val attributes = bagOf("affiliation" to "employee", "email" to "user@example.com")
        val predicates =
            listOf(
                AttributePredicate(AttributePath("affiliation"), MatchOperator.EQUALS, "student"),
                AttributePredicate(AttributePath("email"), MatchOperator.EXISTS),
            )
        assertFalse(AttributePredicateEvaluator.evaluate(predicates, attributes))
    }
}
