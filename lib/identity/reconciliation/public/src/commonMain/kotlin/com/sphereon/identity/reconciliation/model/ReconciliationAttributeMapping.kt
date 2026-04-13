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

/**
 * Rich attribute mapping model for reconciliation providers.
 *
 * Replaces the simple `Map<String, String>` attribute mappings with a structured model
 * that can express required fields, identifier types, and other mapping semantics.
 *
 * @param source The source attribute name from the external provider (e.g., "sub", "eduid")
 * @param target The target canonical attribute name (e.g., "federated_subject", "given_name")
 * @param identifierType Optional identifier type hint (e.g., "SUBJECT_ID", "EMAIL", "INSTITUTION_ID")
 * @param required Whether this attribute must be present for reconciliation to succeed
 */
@Serializable
data class ReconciliationAttributeMapping(
    val source: String,
    val target: String,
    val identifierType: String? = null,
    val required: Boolean = false,
)
