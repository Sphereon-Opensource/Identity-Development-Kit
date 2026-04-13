package com.sphereon.data.store.blob

import com.sphereon.core.api.error.IdkError

/**
 * Sealed class representing blob store errors.
 */
sealed class BlobStoreError(
    val kind: Kind,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        NOT_FOUND,
        ALREADY_EXISTS,
        IO_ERROR,
        UNSUPPORTED,
        QUOTA_EXCEEDED,
        INTEGRITY_ERROR,
        PERMISSION_DENIED,
        PRECONDITION_FAILED,
        BACKEND_ERROR,
    }

    class NotFound(path: String, cause: Throwable? = null) :
        BlobStoreError(Kind.NOT_FOUND, "Blob not found: $path", cause)

    class AlreadyExists(path: String, cause: Throwable? = null) :
        BlobStoreError(Kind.ALREADY_EXISTS, "Blob already exists: $path", cause)

    class IoError(message: String, cause: Throwable? = null) :
        BlobStoreError(Kind.IO_ERROR, message, cause)

    class Unsupported(operation: String) :
        BlobStoreError(Kind.UNSUPPORTED, "Operation not supported: $operation")

    class QuotaExceeded(message: String) :
        BlobStoreError(Kind.QUOTA_EXCEEDED, message)

    class IntegrityError(message: String, cause: Throwable? = null) :
        BlobStoreError(Kind.INTEGRITY_ERROR, message, cause)

    class PermissionDenied(message: String) :
        BlobStoreError(Kind.PERMISSION_DENIED, message)

    class PreconditionFailed(message: String) :
        BlobStoreError(Kind.PRECONDITION_FAILED, message)

    class BackendError(message: String, cause: Throwable? = null) :
        BlobStoreError(Kind.BACKEND_ERROR, message, cause)

    fun toIdkError(): IdkError = IdkError.fromString(
        message = message,
        exception = cause as? Exception,
        code = "BLOB_${kind.name}",
    )
}
