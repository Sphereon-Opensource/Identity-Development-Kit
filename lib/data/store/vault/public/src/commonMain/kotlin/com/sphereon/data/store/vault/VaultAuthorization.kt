/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class VaultAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    LIST,
    SEARCH,
    COPY,
    MOVE,
    MANAGE_POLICY,
    MANAGE_GRANTS,
    EXPORT,
    IMPORT,
}

@Serializable
enum class VaultGrantEffect {
    ALLOW,
    DENY,
}

@Serializable
data class VaultGrantScope(
    val pathPrefix: VaultPath? = null,
    val objectIds: Set<VaultObjectId> = emptySet(),
    val objectKinds: Set<VaultObjectKind> = emptySet(),
)

@Serializable
data class VaultGrant(
    val grantId: String,
    val vaultId: VaultId,
    val principalRef: String,
    val effect: VaultGrantEffect,
    val actions: Set<VaultAction>,
    val scope: VaultGrantScope = VaultGrantScope(),
    val purposes: Set<String> = emptySet(),
    val notBefore: Instant? = null,
    val expiresAt: Instant? = null,
    val revision: VaultRevision,
) {
    init {
        require(grantId.isNotBlank()) { "grantId must not be blank" }
        require(principalRef.isNotBlank()) { "principalRef must not be blank" }
        require(actions.isNotEmpty()) { "A grant must contain at least one action" }
        require(notBefore == null || expiresAt == null || expiresAt > notBefore) {
            "expiresAt must be after notBefore"
        }
    }
}

@Serializable
data class VaultApprovalRequirement(
    val requirementId: String,
    val requiredApprovals: Int,
    val distinctPrincipals: Boolean = true,
    val expiresAt: Instant? = null,
) {
    init {
        require(requirementId.isNotBlank()) { "requirementId must not be blank" }
        require(requiredApprovals > 0) { "requiredApprovals must be positive" }
    }
}

@Serializable
data class VaultObligation(
    val type: String,
    val parameters: Map<String, String> = emptyMap(),
) {
    init {
        require(type.isNotBlank()) { "Obligation type must not be blank" }
    }
}

@Serializable
data class VaultDecision(
    val decisionId: String,
    val permitted: Boolean,
    val obligations: List<VaultObligation> = emptyList(),
    val requiredApprovals: List<VaultApprovalRequirement> = emptyList(),
) {
    init {
        require(decisionId.isNotBlank()) { "decisionId must not be blank" }
    }
}

@Serializable
data class VaultOperationContext(
    val operationId: VaultOperationId,
    val actorRef: String,
    val purpose: String? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(actorRef.isNotBlank()) { "actorRef must not be blank" }
    }
}

@Serializable
data class VaultMutationContext(
    val operation: VaultOperationContext,
    val idempotencyKey: VaultIdempotencyKey,
)
