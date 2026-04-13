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

package com.sphereon.data.store.blob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BlobStoreErrorTest {
    @Test
    fun notFoundHasCorrectKind() {
        val error = BlobStoreError.NotFound("some/path")
        assertEquals(BlobStoreError.Kind.NOT_FOUND, error.kind)
    }

    @Test
    fun notFoundHasCorrectMessage() {
        val error = BlobStoreError.NotFound("some/path")
        assertEquals("Blob not found: some/path", error.message)
    }

    @Test
    fun alreadyExistsHasCorrectKind() {
        val error = BlobStoreError.AlreadyExists("some/path")
        assertEquals(BlobStoreError.Kind.ALREADY_EXISTS, error.kind)
    }

    @Test
    fun alreadyExistsHasCorrectMessage() {
        val error = BlobStoreError.AlreadyExists("some/path")
        assertEquals("Blob already exists: some/path", error.message)
    }

    @Test
    fun ioErrorHasCorrectKind() {
        val error = BlobStoreError.IoError("disk full")
        assertEquals(BlobStoreError.Kind.IO_ERROR, error.kind)
    }

    @Test
    fun ioErrorHasCorrectMessage() {
        val error = BlobStoreError.IoError("disk full")
        assertEquals("disk full", error.message)
    }

    @Test
    fun unsupportedHasCorrectKind() {
        val error = BlobStoreError.Unsupported("deletePrefix")
        assertEquals(BlobStoreError.Kind.UNSUPPORTED, error.kind)
    }

    @Test
    fun unsupportedHasCorrectMessage() {
        val error = BlobStoreError.Unsupported("deletePrefix")
        assertEquals("Operation not supported: deletePrefix", error.message)
    }

    @Test
    fun quotaExceededHasCorrectKind() {
        val error = BlobStoreError.QuotaExceeded("limit reached")
        assertEquals(BlobStoreError.Kind.QUOTA_EXCEEDED, error.kind)
    }

    @Test
    fun integrityErrorHasCorrectKind() {
        val error = BlobStoreError.IntegrityError("checksum mismatch")
        assertEquals(BlobStoreError.Kind.INTEGRITY_ERROR, error.kind)
    }

    @Test
    fun permissionDeniedHasCorrectKind() {
        val error = BlobStoreError.PermissionDenied("read-only")
        assertEquals(BlobStoreError.Kind.PERMISSION_DENIED, error.kind)
    }

    @Test
    fun preconditionFailedHasCorrectKind() {
        val error = BlobStoreError.PreconditionFailed("etag mismatch")
        assertEquals(BlobStoreError.Kind.PRECONDITION_FAILED, error.kind)
    }

    @Test
    fun backendErrorHasCorrectKind() {
        val error = BlobStoreError.BackendError("connection refused")
        assertEquals(BlobStoreError.Kind.BACKEND_ERROR, error.kind)
    }

    // toIdkError tests

    @Test
    fun notFoundToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.NotFound("x").toIdkError()
        assertEquals("BLOB_NOT_FOUND", idkError.code)
    }

    @Test
    fun alreadyExistsToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.AlreadyExists("x").toIdkError()
        assertEquals("BLOB_ALREADY_EXISTS", idkError.code)
    }

    @Test
    fun ioErrorToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.IoError("fail").toIdkError()
        assertEquals("BLOB_IO_ERROR", idkError.code)
    }

    @Test
    fun unsupportedToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.Unsupported("op").toIdkError()
        assertEquals("BLOB_UNSUPPORTED", idkError.code)
    }

    @Test
    fun quotaExceededToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.QuotaExceeded("full").toIdkError()
        assertEquals("BLOB_QUOTA_EXCEEDED", idkError.code)
    }

    @Test
    fun integrityErrorToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.IntegrityError("bad hash").toIdkError()
        assertEquals("BLOB_INTEGRITY_ERROR", idkError.code)
    }

    @Test
    fun permissionDeniedToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.PermissionDenied("nope").toIdkError()
        assertEquals("BLOB_PERMISSION_DENIED", idkError.code)
    }

    @Test
    fun preconditionFailedToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.PreconditionFailed("etag").toIdkError()
        assertEquals("BLOB_PRECONDITION_FAILED", idkError.code)
    }

    @Test
    fun backendErrorToIdkErrorProducesCorrectCode() {
        val idkError = BlobStoreError.BackendError("down").toIdkError()
        assertEquals("BLOB_BACKEND_ERROR", idkError.code)
    }

    @Test
    fun toIdkErrorPreservesCauseException() {
        val cause = RuntimeException("underlying failure")
        val error = BlobStoreError.IoError("io problem", cause)
        val idkError = error.toIdkError()
        assertNotNull(idkError.exception)
        assertEquals("underlying failure", idkError.exception?.message)
    }

    @Test
    fun toIdkErrorWorksWithNullCause() {
        val error = BlobStoreError.NotFound("missing.txt")
        val idkError = error.toIdkError()
        assertNull(idkError.exception)
    }

    @Test
    fun toIdkErrorPreservesMessage() {
        val error = BlobStoreError.BackendError("server exploded")
        val idkError = error.toIdkError()
        assertEquals("server exploded", idkError.message.defaultMessage)
    }
}
