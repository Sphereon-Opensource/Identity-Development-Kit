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

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.matching.model.IdentityMatch
import kotlinx.serialization.Serializable

@Serializable
data class CreateReconciliationSessionArgs(
    val identifierHash: String,
    val identifierType: IdentifierType,
    val providerId: String,
    val tenantId: String,
    val redirectUri: String,
    val sessionTtlSeconds: Long = 300,
)

@Serializable
data class CreateReconciliationSessionResult(
    val session: ReconciliationSession,
    val authorizationUrl: String,
)

@Serializable
data class CompleteReconciliationArgs(
    val sessionId: String,
    val tenantId: String,
    val authorizationCode: String,
    val state: String,
    val internalIdentityId: String,
    val hashKeyVersion: String? = null,
)

@Serializable
data class CompleteReconciliationResult(
    val session: ReconciliationSession,
    val match: IdentityMatch,
)

@Serializable
data class GetReconciliationSessionArgs(
    val sessionId: String,
    val tenantId: String,
)

@Serializable
data class CancelReconciliationSessionArgs(
    val sessionId: String,
    val tenantId: String,
)
