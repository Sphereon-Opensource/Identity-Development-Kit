/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

sealed interface ReconciliationError : IdkErrorType {

    data class SessionNotFound(
        val sessionId: String,
        override val code: String = "identity_reconciliation.session_not_found",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.session_not_found",
            defaultMessage = "Reconciliation session not found: $sessionId"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class SessionExpired(
        val sessionId: String,
        override val code: String = "identity_reconciliation.session_expired",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.session_expired",
            defaultMessage = "Reconciliation session expired: $sessionId"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class SessionInvalidState(
        val sessionId: String,
        val currentStatus: String,
        val expectedStatus: String,
        override val code: String = "identity_reconciliation.session_invalid_state",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.session_invalid_state",
            defaultMessage = "Session $sessionId in invalid state: $currentStatus, expected: $expectedStatus"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class ProviderNotFound(
        val providerId: String,
        override val code: String = "identity_reconciliation.provider_not_found",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.provider_not_found",
            defaultMessage = "Reconciliation provider not found: $providerId"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class OidcFlowFailed(
        val reason: String,
        override val code: String = "identity_reconciliation.oidc_flow_failed",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.oidc_flow_failed",
            defaultMessage = "OIDC flow failed: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class TokenExchangeFailed(
        val reason: String,
        override val code: String = "identity_reconciliation.token_exchange_failed",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.token_exchange_failed",
            defaultMessage = "Token exchange failed: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class StateMismatch(
        val expected: String,
        val actual: String,
        override val code: String = "identity_reconciliation.state_mismatch",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.state_mismatch",
            defaultMessage = "State mismatch: expected '$expected', got '$actual'"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class MatchCreationFailed(
        val reason: String,
        override val code: String = "identity_reconciliation.match_creation_failed",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.match_creation_failed",
            defaultMessage = "Failed to create identity match: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class CryptoFailed(
        val operation: String,
        val reason: String,
        override val code: String = "identity_reconciliation.crypto_failed",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.crypto_failed",
            defaultMessage = "Crypto operation '$operation' failed: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class RequiredAttributesMissing(
        val missingAttributes: List<String>,
        override val code: String = "identity_reconciliation.required_attributes_missing",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.required_attributes_missing",
            defaultMessage = "Required attributes missing after mapping: ${missingAttributes.joinToString(", ")}"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class IdentifierAttributeMissing(
        val attributeName: String,
        val providerId: String,
        override val code: String = "identity_reconciliation.identifier_attribute_missing",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.identifier_attribute_missing",
            defaultMessage = "Identifier attribute '$attributeName' not found in mapped attributes for provider '$providerId'. This is a configuration error — check attribute mappings."
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError

    data class UserInfoFetchFailed(
        val reason: String,
        override val code: String = "identity_reconciliation.user_info_fetch_failed",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.reconciliation.error.user_info_fetch_failed",
            defaultMessage = "Failed to fetch user info: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : ReconciliationError
}
