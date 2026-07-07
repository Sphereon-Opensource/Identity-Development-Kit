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

package com.sphereon.data.store.credential.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicDesignAssetPathsTest {
    private val hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun basePath_isCanonicalPublicMount() {
        assertEquals("/public/assets/design", PublicDesignAssetPaths.BASE_PATH)
    }

    @Test
    fun assetPath_isContentAddressedUnderBasePath() {
        assertEquals("/public/assets/design/$hash", PublicDesignAssetPaths.assetPath(hash))
    }

    // ---- extensionForContentType ----

    @Test
    fun extensionForContentType_png_returnsPng() {
        assertEquals("png", PublicDesignAssetPaths.extensionForContentType("image/png"))
    }

    @Test
    fun extensionForContentType_jpeg_returnsJpg() {
        assertEquals("jpg", PublicDesignAssetPaths.extensionForContentType("image/jpeg"))
    }

    @Test
    fun extensionForContentType_svg_returnsSvg() {
        assertEquals("svg", PublicDesignAssetPaths.extensionForContentType("image/svg+xml"))
    }

    @Test
    fun extensionForContentType_unknown_returnsNull() {
        assertNull(PublicDesignAssetPaths.extensionForContentType("application/octet-stream"))
    }

    @Test
    fun extensionForContentType_null_returnsNull() {
        assertNull(PublicDesignAssetPaths.extensionForContentType(null))
    }

    @Test
    fun extensionForContentType_caseInsensitive() {
        assertEquals("png", PublicDesignAssetPaths.extensionForContentType("IMAGE/PNG"))
    }

    @Test
    fun extensionForContentType_stripsCharsetParameter() {
        assertEquals("png", PublicDesignAssetPaths.extensionForContentType("image/png; charset=utf-8"))
    }

    // ---- assetPath with contentType ----

    @Test
    fun assetPath_withPngContentType_appendsDotPng() {
        val path = PublicDesignAssetPaths.assetPath(hash, "image/png")
        assertEquals("/public/assets/design/$hash.png", path)
    }

    @Test
    fun assetPath_withNullContentType_hasNoExtension() {
        val path = PublicDesignAssetPaths.assetPath(hash, null)
        assertEquals("/public/assets/design/$hash", path)
    }

    @Test
    fun assetPath_withUnknownContentType_hasNoExtension() {
        val path = PublicDesignAssetPaths.assetPath(hash, "application/octet-stream")
        assertEquals("/public/assets/design/$hash", path)
    }

    // ---- toAbsolute ----

    @Test
    fun toAbsolute_usesOriginWhenBaseCarriesIssuerPath() {
        assertEquals(
            "https://acme.saas.localtest.me/public/assets/design/$hash.png",
            PublicDesignAssetPaths.toAbsolute(
                "/public/assets/design/$hash.png",
                "https://acme.saas.localtest.me/oid4vci",
            ),
        )
    }

    // ---- hashFromLeaf ----

    @Test
    fun hashFromLeaf_withExtension_stripsExtension() {
        assertEquals("abc123", PublicDesignAssetPaths.hashFromLeaf("abc123.png"))
    }

    @Test
    fun hashFromLeaf_withoutExtension_returnsWholeLeaf() {
        assertEquals("abc123", PublicDesignAssetPaths.hashFromLeaf("abc123"))
    }

    @Test
    fun hashFromLeaf_realHash_stripsExtension() {
        assertEquals(hash, PublicDesignAssetPaths.hashFromLeaf("$hash.svg"))
    }
}
