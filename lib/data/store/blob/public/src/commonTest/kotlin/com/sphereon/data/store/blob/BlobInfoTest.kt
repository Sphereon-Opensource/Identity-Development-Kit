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
import kotlin.test.assertFailsWith

class BlobInfoTest {
    // ── Path validation security tests ────────────────────────────────

    @Test
    fun rejectsParentDirectoryTraversal() {
        assertFailsWith<IllegalArgumentException>("../etc/passwd should be rejected") {
            BlobInfo.validatePath("../etc/passwd")
        }
    }

    @Test
    fun rejectsNestedParentTraversal() {
        assertFailsWith<IllegalArgumentException>("nested .. should be rejected") {
            BlobInfo.validatePath("docs/../../etc/shadow")
        }
    }

    @Test
    fun rejectsMidPathTraversal() {
        assertFailsWith<IllegalArgumentException>("mid-path .. should be rejected") {
            BlobInfo.validatePath("tenant/docs/../../../etc/passwd")
        }
    }

    @Test
    fun rejectsTrailingTraversal() {
        assertFailsWith<IllegalArgumentException>("trailing .. should be rejected") {
            BlobInfo.validatePath("docs/..")
        }
    }

    @Test
    fun rejectsDotSegment() {
        assertFailsWith<IllegalArgumentException>("\".\" segment should be rejected") {
            BlobInfo.validatePath("docs/./file.txt")
        }
    }

    @Test
    fun rejectsNullBytes() {
        assertFailsWith<IllegalArgumentException>("null bytes should be rejected") {
            BlobInfo.validatePath("docs/file\u0000.txt")
        }
    }

    @Test
    fun rejectsAbsolutePath() {
        assertFailsWith<IllegalArgumentException>("absolute path should be rejected") {
            BlobInfo.validatePath("/etc/passwd")
        }
    }

    @Test
    fun rejectsBackslash() {
        assertFailsWith<IllegalArgumentException>("backslash should be rejected") {
            BlobInfo.validatePath("docs\\file.txt")
        }
    }

    @Test
    fun acceptsValidNestedPath() {
        // validatePath should not throw for valid paths
        BlobInfo.validatePath("tenant/docs/guides/getting-started.md")
    }

    @Test
    fun acceptsSimplePath() {
        BlobInfo.validatePath("hello.txt")
    }

    @Test
    fun acceptsDotsInFilename() {
        // ".." as a path SEGMENT is rejected, but dots in filenames are fine
        BlobInfo.validatePath("docs/file.v2.0.tar.gz")
    }

    // ── BlobInfo.sanitizePath tests ───────────────────────────────────

    @Test
    fun sanitizePathNormalizesSlashes() {
        assertEquals("docs/file.txt", BlobInfo.sanitizePath("docs//file.txt"))
        assertEquals("docs/file.txt", BlobInfo.sanitizePath("docs///file.txt"))
    }

    @Test
    fun sanitizePathStripsLeadingSlash() {
        assertEquals("docs/file.txt", BlobInfo.sanitizePath("/docs/file.txt"))
    }

    @Test
    fun sanitizePathStripsTrailingSlash() {
        assertEquals("docs/file.txt", BlobInfo.sanitizePath("docs/file.txt/"))
    }

    @Test
    fun sanitizePathRejectsTraversal() {
        assertFailsWith<IllegalArgumentException> {
            BlobInfo.sanitizePath("docs/../../../etc/passwd")
        }
    }

    @Test
    fun sanitizePathRejectsBlank() {
        assertFailsWith<IllegalArgumentException> {
            BlobInfo.sanitizePath("   ")
        }
    }

    // ── BlobInfo construction tests ──────────────────────────────────

    @Test
    fun blobInfoWithPathAndStoreId() {
        val info = BlobInfo(storeId = "memory", path = "docs/file.txt")
        assertEquals("memory", info.storeId)
        assertEquals("docs/file.txt", info.path)
    }

    @Test
    fun blobInfoOfFactoryMethod() {
        val info = BlobInfo.of("s3", "tenant-1/docs/report.pdf")
        assertEquals("s3", info.storeId)
        assertEquals("tenant-1/docs/report.pdf", info.path)
    }

    @Test
    fun blobInfoWithPathCreatesNewInstance() {
        val original = BlobInfo(storeId = "memory", path = "docs/old.txt")
        val updated = original.withPath("docs/new.txt")
        assertEquals("docs/new.txt", updated.path)
        assertEquals("memory", updated.storeId)
    }

    @Test
    fun blobInfoWithStoreCreatesNewInstance() {
        val original = BlobInfo(storeId = "memory", path = "docs/file.txt")
        val updated = original.withStore("s3")
        assertEquals("s3", updated.storeId)
        assertEquals("docs/file.txt", updated.path)
    }

    @Test
    fun blobInfoToBlobMetadataConversion() {
        val info =
            BlobInfo(
                storeId = "memory",
                path = "docs/file.txt",
                contentType = "text/plain",
                metadata = mapOf("category" to "report"),
            )
        val metadata = info.toBlobMetadata()
        assertEquals("text/plain", metadata.contentType)
        assertEquals("report", metadata.custom["category"])
    }
}
