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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object AttributePredicateEvaluator {
    fun evaluate(
        predicates: List<AttributePredicate>,
        attributes: AttributeBag,
    ): Boolean = predicates.all { evaluateOne(it, attributes) }

    private fun evaluateOne(
        predicate: AttributePredicate,
        attributes: AttributeBag,
    ): Boolean {
        val element = attributes[predicate.attributePath]
        val text = element?.let { extractText(it) }

        return when (predicate.operator) {
            MatchOperator.EXISTS -> {
                element != null
            }

            MatchOperator.NOT_EMPTY -> {
                !text.isNullOrBlank()
            }

            MatchOperator.EQUALS -> {
                text == predicate.value
            }

            MatchOperator.CONTAINS -> {
                text != null && predicate.value != null &&
                    text.contains(predicate.value)
            }

            MatchOperator.REGEX -> {
                text != null && predicate.value != null &&
                    Regex(predicate.value).containsMatchIn(text)
            }
        }
    }

    private fun extractText(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
}
