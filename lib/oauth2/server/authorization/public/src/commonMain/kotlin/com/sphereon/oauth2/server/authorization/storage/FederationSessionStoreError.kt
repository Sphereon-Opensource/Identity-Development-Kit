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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

/**
 * Errors emitted by [FederationSessionStore] implementations. Mirrors the
 * `TenantIdpRegistryError` / `ResourceServerError` shape (sealed interface over
 * [IdkErrorType] with per-variant data classes carrying a stable [code]) so transport
 * layers can dispatch on `error.code` without pattern-matching free-form reason strings.
 */
sealed interface FederationSessionStoreError : IdkErrorType {
    /**
     * Underlying storage backend failed (database unreachable, serialization error,
     * transaction rollback, etc.). [cause] carries the original throwable for diagnostics
     * while [reason] provides a short human-readable summary.
     */
    data class StorageFailure(
        val reason: String,
        val cause: Throwable? = null,
        override val code: String = "FED_SESSION_STORAGE_FAILURE",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.federation.session.error.storage-failure",
                defaultMessage = "Federation session store failure: $reason",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val category: ErrorCategory = ErrorCategory.INTERNAL,
        override val exception: Throwable? = cause,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("reason" to reason),
    ) : FederationSessionStoreError

    /**
     * A pending federation record already exists for the given OAuth2 [state]. State is the
     * CSRF token and must be globally unique across concurrent initiate calls; a collision
     * indicates either a PRNG malfunction on the caller side or a replay attempt.
     */
    data class StateCollision(
        val state: String,
        override val code: String = "FED_SESSION_STATE_COLLISION",
        override val message: IdkError.Message =
            IdkError.Message(
                i18nKey = "oauth2.federation.session.error.state-collision",
                defaultMessage = "Federation state '$state' is already in use",
            ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val category: ErrorCategory = ErrorCategory.CONFLICT,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = mapOf("state" to state),
    ) : FederationSessionStoreError
}
