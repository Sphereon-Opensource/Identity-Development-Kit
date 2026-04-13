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

package com.sphereon.core.api.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericHttpBodyTest {

    @Test
    fun emptyBodyReportsIsEmptyTrueAndReturnsNullForConversions() {
        val body = GenericHttpBody.Empty

        assertTrue(body.isEmpty)
        assertNull(body.asTextOrNull())
        assertNull(body.asBytesOrNull())
    }

    @Test
    fun textBodyStoresAndReturnsText() {
        val body = GenericHttpBody.Text("hello world")

        assertFalse(body.isEmpty)
        assertEquals("hello world", body.asTextOrNull())
        assertEquals("hello world", body.asBytesOrNull()?.decodeToString())
    }

    @Test
    fun textBodyWithEmptyStringReportsIsEmptyTrue() {
        val body = GenericHttpBody.Text("")

        assertTrue(body.isEmpty)
        assertEquals("", body.asTextOrNull())
    }

    @Test
    fun bytesBodyStoresAndReturnsBytes() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val body = GenericHttpBody.Bytes(data)

        assertFalse(body.isEmpty)
        assertTrue(data.contentEquals(body.asBytesOrNull()!!))
    }

    @Test
    fun bytesBodyWithEmptyArrayReportsIsEmptyTrue() {
        val body = GenericHttpBody.Bytes(byteArrayOf())

        assertTrue(body.isEmpty)
    }

    @Test
    fun bytesBodyEqualsUsesContentEquality() {
        val body1 = GenericHttpBody.Bytes(byteArrayOf(1, 2, 3))
        val body2 = GenericHttpBody.Bytes(byteArrayOf(1, 2, 3))
        val body3 = GenericHttpBody.Bytes(byteArrayOf(4, 5, 6))

        assertEquals(body1, body2)
        assertFalse(body1 == body3)
    }

    @Test
    fun lazyTextEvaluatesSupplierLazily() {
        var invoked = false
        val body = GenericHttpBody.LazyText(supplier = {
            invoked = true
            "lazy content"
        })

        // Not yet invoked
        assertFalse(invoked)

        // Access value triggers evaluation
        assertEquals("lazy content", body.value)
        assertTrue(invoked)

        // asTextOrNull also returns the value
        assertEquals("lazy content", body.asTextOrNull())
    }

    @Test
    fun lazyTextWithNullSupplierResultReportsIsEmptyTrue() {
        val body = GenericHttpBody.LazyText(supplier = { null })

        assertTrue(body.isEmpty)
        assertNull(body.asTextOrNull())
    }

    @Test
    fun lazyBytesEvaluatesSupplierLazily() {
        var invoked = false
        val expected = byteArrayOf(0xCA.toByte(), 0xFE.toByte())
        val body = GenericHttpBody.LazyBytes(supplier = {
            invoked = true
            expected
        })

        // Not yet invoked
        assertFalse(invoked)

        // Access value triggers evaluation
        assertTrue(expected.contentEquals(body.value!!))
        assertTrue(invoked)
    }

    @Test
    fun ofTextCreatesTextBodyForNonNullString() {
        val body = GenericHttpBody.ofText("test")

        assertTrue(body is GenericHttpBody.Text)
        assertEquals("test", body.asTextOrNull())
    }

    @Test
    fun ofTextCreatesEmptyBodyForNullString() {
        val body = GenericHttpBody.ofText(null)

        assertTrue(body is GenericHttpBody.Empty)
    }

    @Test
    fun ofTextCreatesEmptyBodyForBlankString() {
        val body = GenericHttpBody.ofText("   ")

        assertTrue(body is GenericHttpBody.Empty)
    }

    @Test
    fun ofBytesCreatesBytesBodyForNonEmptyArray() {
        val data = byteArrayOf(1, 2, 3)
        val body = GenericHttpBody.ofBytes(data)

        assertTrue(body is GenericHttpBody.Bytes)
        assertTrue(data.contentEquals(body.asBytesOrNull()!!))
    }

    @Test
    fun ofBytesCreatesEmptyBodyForNullArray() {
        val body = GenericHttpBody.ofBytes(null)

        assertTrue(body is GenericHttpBody.Empty)
    }

    @Test
    fun ofBytesCreatesEmptyBodyForEmptyArray() {
        val body = GenericHttpBody.ofBytes(byteArrayOf())

        assertTrue(body is GenericHttpBody.Empty)
    }

    @Test
    fun ofLazyTextCreatesLazyTextBody() {
        val body = GenericHttpBody.ofLazyText(supplier = { "lazy" })

        assertTrue(body is GenericHttpBody.LazyText)
        assertEquals("lazy", body.asTextOrNull())
    }

    @Test
    fun ofLazyBytesCreatesLazyBytesBody() {
        val data = byteArrayOf(1, 2)
        val body = GenericHttpBody.ofLazyBytes(supplier = { data })

        assertTrue(body is GenericHttpBody.LazyBytes)
        assertTrue(data.contentEquals(body.asBytesOrNull()!!))
    }

    // GenericHttpRequest body integration tests

    @Test
    fun genericHttpRequestWithBodySupplierUsesLazyText() {
        val request = GenericHttpRequest(
            method = "POST",
            path = "/test",
            bodySupplier = { "request body" }
        )

        assertTrue(request.bodyContent is GenericHttpBody.LazyText)
        assertEquals("request body", request.body)
        assertEquals("request body", request.bodyContent.asTextOrNull())
    }

    @Test
    fun genericHttpRequestWithTextBodyCreatesProperBody() {
        val request = GenericHttpRequest.withTextBody(
            method = "POST",
            path = "/test",
            body = """{"key": "value"}"""
        )

        assertTrue(request.bodyContent is GenericHttpBody.Text)
        assertEquals("""{"key": "value"}""", request.body)
    }

    @Test
    fun genericHttpRequestWithBinaryBodyCreatesProperBody() {
        val data = byteArrayOf(0x00, 0x01, 0x02)
        val request = GenericHttpRequest.withBinaryBody(
            method = "POST",
            path = "/test",
            body = data
        )

        assertTrue(request.bodyContent is GenericHttpBody.Bytes)
        assertTrue(data.contentEquals(request.bodyBytes!!))
    }

    @Test
    fun genericHttpRequestContentTypeAccessorWorks() {
        val request = GenericHttpRequest(
            method = "POST",
            path = "/test",
            headers = mapOf("Content-Type" to "application/json")
        )

        assertEquals("application/json", request.contentType)
    }

    @Test
    fun genericHttpRequestAcceptAccessorParsesMultipleValues() {
        val request = GenericHttpRequest(
            method = "GET",
            path = "/test",
            headers = mapOf("Accept" to "application/json, text/plain;q=0.9")
        )

        assertEquals(listOf("application/json", "text/plain"), request.accept)
    }

    @Test
    fun genericHttpRequestCopyPreservesBodyContent() {
        val original = GenericHttpRequest.withTextBody(
            method = "POST",
            path = "/test",
            body = "original"
        )

        val copied = original.copy(path = "/new-path")

        assertEquals("original", copied.body)
        assertEquals("/new-path", copied.path)
    }

    // GenericHttpResponse body integration tests

    @Test
    fun genericHttpResponseWithStringBodyCreatesTextBodyContent() {
        val response = GenericHttpResponse(
            statusCode = 200,
            body = """{"result": "success"}"""
        )

        assertTrue(response.bodyContent is GenericHttpBody.Text)
        assertEquals("""{"result": "success"}""", response.body)
    }

    @Test
    fun genericHttpResponseWithTextBodyCreatesProperBody() {
        val response = GenericHttpResponse.withTextBody(
            statusCode = 200,
            body = "response text"
        )

        assertTrue(response.bodyContent is GenericHttpBody.Text)
        assertEquals("response text", response.body)
    }

    @Test
    fun genericHttpResponseWithBinaryBodyCreatesProperBody() {
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val response = GenericHttpResponse.withBinaryBody(
            statusCode = 200,
            body = data,
            headers = mapOf("Content-Type" to "application/octet-stream")
        )

        assertTrue(response.bodyContent is GenericHttpBody.Bytes)
        assertTrue(data.contentEquals(response.bodyBytes!!))
    }

    @Test
    fun genericHttpResponseContentTypeAccessorWorks() {
        val response = GenericHttpResponse(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = "{}"
        )

        assertEquals("application/json", response.contentType)
    }
}
