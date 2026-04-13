/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.error

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

sealed interface IdentityMatchingError : IdkErrorType {

    data class MatchNotFound(
        val identifierHash: String? = null,
        val matchId: String? = null,
        override val code: String = "identity_matching.match_not_found",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.matching.error.match_not_found",
            defaultMessage = "Identity match not found${matchId?.let { " for id: $it" } ?: identifierHash?.let { " for hash: $it" } ?: ""}"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : IdentityMatchingError

    data class DuplicateMatch(
        val identifierHash: String,
        override val code: String = "identity_matching.duplicate_match",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.matching.error.duplicate_match",
            defaultMessage = "Identity match already exists for identifier hash: $identifierHash"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : IdentityMatchingError

    data class StoreError(
        val operation: String,
        val details: String,
        override val code: String = "identity_matching.store_error",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.matching.error.store_error",
            defaultMessage = "Store error during $operation: $details"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : IdentityMatchingError

    data class InvalidIdentifier(
        val reason: String,
        override val code: String = "identity_matching.invalid_identifier",
        override val message: IdkError.Message = IdkError.Message(
            i18nKey = "identity.matching.error.invalid_identifier",
            defaultMessage = "Invalid identifier: $reason"
        ),
        override val severity: IdkError.Severity = IdkError.Severity.ERROR,
        override val exception: Throwable? = null,
        override val causes: List<IdkErrorType> = emptyList(),
        override val meta: Map<String, Any?> = emptyMap()
    ) : IdentityMatchingError
}
