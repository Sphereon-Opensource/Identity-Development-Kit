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

import com.sphereon.core.api.error.IdkError

/** Provider-neutral error surface for every [VaultService] implementation. */
sealed class VaultError(
    val kind: Kind,
    val code: String,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        NOT_FOUND,
        ALREADY_EXISTS,
        INVALID_REQUEST,
        PRECONDITION_FAILED,
        PERMISSION_DENIED,
        UNSUPPORTED,
        INTEGRITY_ERROR,
        CLIENT_UNWRAP_REQUIRED,
        BACKEND_ERROR,
    }

    class NotFound(
        resource: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.NOT_FOUND, "VAULT_NOT_FOUND", "Vault resource not found: $resource", cause)

    class AlreadyExists(
        resource: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.ALREADY_EXISTS, "VAULT_ALREADY_EXISTS", "Vault resource already exists: $resource", cause)

    class InvalidRequest(
        message: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.INVALID_REQUEST, "VAULT_INVALID_REQUEST", message, cause)

    class PreconditionFailed(
        message: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.PRECONDITION_FAILED, "VAULT_PRECONDITION_FAILED", message, cause)

    class PermissionDenied(
        message: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.PERMISSION_DENIED, "VAULT_PERMISSION_DENIED", message, cause)

    class Unsupported(
        operation: String,
    ) : VaultError(Kind.UNSUPPORTED, "VAULT_UNSUPPORTED", "Vault operation not supported: $operation")

    class IntegrityError(
        message: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.INTEGRITY_ERROR, "VAULT_INTEGRITY_ERROR", message, cause)

    /**
     * A caller requested plaintext from an OWNER_CONTROLLED_ZERO_ACCESS vault, but this service
     * is not authorized or able to unwrap it. The protected package reference can be resolved by
     * an authorized client; ciphertext is never returned as if it were plaintext.
     */
    class ClientUnwrapRequired(
        val vaultId: VaultId,
        val objectId: VaultObjectId,
        val versionId: VaultVersionId,
        val protectedPackageRef: String,
    ) : VaultError(
            kind = Kind.CLIENT_UNWRAP_REQUIRED,
            code = CODE,
            message = "Client unwrap is required for vault object ${objectId.value} version ${versionId.value}",
        ) {
        init {
            require(protectedPackageRef.isNotBlank()) { "protectedPackageRef must not be blank" }
        }

        companion object {
            const val CODE = "VAULT_CLIENT_UNWRAP_REQUIRED"
        }
    }

    class BackendError(
        message: String,
        cause: Throwable? = null,
    ) : VaultError(Kind.BACKEND_ERROR, "VAULT_BACKEND_ERROR", message, cause)

    fun toIdkError(): IdkError =
        IdkError.fromString(
            message = message,
            exception = cause as? Exception,
            code = code,
        )
}
