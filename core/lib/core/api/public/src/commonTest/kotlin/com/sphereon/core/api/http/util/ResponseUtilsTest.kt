/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.core.api.http.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseUtilsTest {
    @Test
    fun quotesPlainAsciiNameAsAttachmentByDefault() {
        assertEquals(
            "attachment; filename=\"hello.txt\"; filename*=UTF-8''hello.txt",
            ResponseUtils.contentDisposition("hello.txt"),
        )
    }

    @Test
    fun emitsInlineDispositionWhenRequested() {
        assertTrue(ResponseUtils.contentDisposition("hello.txt", inline = true).startsWith("inline; "))
    }

    @Test
    fun escapesQuoteInFallbackAndEncodesInExtended() {
        // A double-quote must not terminate the quoted-string early.
        val header = ResponseUtils.contentDisposition("ev\"il.pdf")
        assertTrue(header.contains("filename=\"ev\\\"il.pdf\""), "quote not escaped: $header")
        assertTrue(header.contains("filename*=UTF-8''ev%22il.pdf"), "quote not encoded: $header")
    }

    @Test
    fun stripsControlCharsFromFallbackAndEncodesThem() {
        val header = ResponseUtils.contentDisposition("a\r\nb.txt")
        assertTrue(header.contains("filename=\"ab.txt\""), "control chars not stripped: $header")
        assertTrue(header.contains("filename*=UTF-8''a%0D%0Ab.txt"), "control chars not encoded: $header")
    }

    @Test
    fun handlesNonAsciiName() {
        val header = ResponseUtils.contentDisposition("naïve.pdf")
        assertTrue(header.contains("filename=\"na_ve.pdf\""), "non-ascii fallback wrong: $header")
        assertTrue(header.contains("filename*=UTF-8''na%C3%AFve.pdf"), "non-ascii encoding wrong: $header")
    }

    @Test
    fun emptyAfterStrippingFallsBackToDownload() {
        val header = ResponseUtils.contentDisposition("")
        assertTrue(header.contains("filename=\"download\""), "empty fallback wrong: $header")
    }
}
