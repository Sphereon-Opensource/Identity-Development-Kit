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

package com.sphereon.data.store.vault.portability

import kotlinx.serialization.Serializable

@Serializable
enum class VaultVerificationIssueCode {
    INVALID_PATH,
    PATH_TRAVERSAL,
    RESERVED_PATH,
    DUPLICATE_PATH,
    CASE_COLLISION,
    UNICODE_COLLISION,
    OBJECT_COUNT_LIMIT,
    PATH_DEPTH_LIMIT,
    ENTRY_SIZE_LIMIT,
    TOTAL_SIZE_LIMIT,
    DECOMPRESSION_RATIO_LIMIT,
    MISSING_BAGIT_DECLARATION,
    INVALID_BAGIT_DECLARATION,
    MISSING_PAYLOAD_MANIFEST,
    MISSING_TAG_MANIFEST,
    MISSING_TAG_MANIFEST_ENTRY,
    MALFORMED_MANIFEST,
    MISSING_PAYLOAD,
    UNLISTED_PAYLOAD,
    CHECKSUM_MISMATCH,
    TAG_CHECKSUM_MISMATCH,
    SIZE_MISMATCH,
    EXCLUDED_CONTENT,
    MISSING_PROFILE_ENTRY,
    INVALID_PROFILE,
    INVALID_SIGNED_MANIFEST,
    SIGNATURE_VERIFIER_REQUIRED,
    IO_ERROR,
}

@Serializable
data class VaultVerificationIssue(
    val code: VaultVerificationIssueCode,
    val path: String? = null,
    val message: String,
)

@Serializable
data class VaultPackageVerificationResult(
    val valid: Boolean,
    val issues: List<VaultVerificationIssue>,
    val verifiedPayloadCount: Long,
    val verifiedPayloadBytes: Long,
) {
    init {
        require(valid == issues.isEmpty()) { "valid must agree with issues" }
        require(verifiedPayloadCount >= 0) { "verifiedPayloadCount must be non-negative" }
        require(verifiedPayloadBytes >= 0) { "verifiedPayloadBytes must be non-negative" }
    }
}

sealed class VaultPortabilityError(
    val code: String,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    class InvalidPath(
        path: String,
        problem: PortablePathProblem,
    ) : VaultPortabilityError("VAULT_PORTABILITY_INVALID_PATH", "Invalid portable path '$path': $problem")

    class ExcludedContent(
        path: String,
        reason: String,
    ) : VaultPortabilityError("VAULT_PORTABILITY_EXCLUDED_CONTENT", "Export excludes '$path': $reason")

    class InvalidPayload(
        message: String,
        cause: Throwable? = null,
    ) : VaultPortabilityError("VAULT_PORTABILITY_INVALID_PAYLOAD", message, cause)

    class TdfProviderRequired : VaultPortabilityError(
        "VAULT_TDF_PROVIDER_REQUIRED",
        "A standards-compliant recipient-scoped TDF envelope provider is required",
    )

    class EnvelopeFailure(
        message: String,
        cause: Throwable? = null,
    ) : VaultPortabilityError("VAULT_TDF_ENVELOPE_FAILURE", message, cause)
}
