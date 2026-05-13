/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.api.http

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.api.http.response.createdResponse
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.http.response.noContentResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoContentResponseTest {
    @Test
    fun defaultStatusCodeIs204() {
        val response = noContentResponse()
        assertEquals(204, response.statusCode)
    }

    @Test
    fun customStatusCodeIsUsed() {
        val response = noContentResponse(statusCode = 200)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun hasContentTypeHeader() {
        val response = noContentResponse()
        assertEquals("application/json", response.headers["Content-Type"])
    }
}

class CreatedResponseTest {
    @Test
    fun statusCodeIs201() {
        val response = createdResponse(location = "/users/123", body = "{}")
        assertEquals(201, response.statusCode)
    }

    @Test
    fun hasLocationHeader() {
        val response = createdResponse(location = "/users/123", body = "{}")
        assertEquals("/users/123", response.headers["Location"])
    }

    @Test
    fun hasContentTypeHeader() {
        val response = createdResponse(location = "/users/123", body = "{}")
        assertEquals("application/json", response.headers["Content-Type"])
    }

    @Test
    fun hasBody() {
        val response = createdResponse(location = "/users/123", body = """{"id":"123"}""")
        assertEquals("""{"id":"123"}""", response.body)
    }
}

class JsonResponseTest {
    @Test
    fun usesProvidedStatusCode() {
        val response = jsonResponse(statusCode = 200, body = "{}")
        assertEquals(200, response.statusCode)
    }

    @Test
    fun hasContentTypeHeader() {
        val response = jsonResponse(statusCode = 200, body = "{}")
        assertEquals("application/json", response.headers["Content-Type"])
    }

    @Test
    fun hasBody() {
        val response = jsonResponse(statusCode = 200, body = """{"data":"test"}""")
        assertEquals("""{"data":"test"}""", response.body)
    }
}

class ErrorResponseFromThrowableTest {
    @Test
    fun illegalArgumentExceptionReturns400() {
        val response = errorResponse(IllegalArgumentException("bad input"))
        assertEquals(400, response.statusCode)
    }

    @Test
    fun noSuchElementExceptionReturns404() {
        val response = errorResponse(NoSuchElementException("not found"))
        assertEquals(404, response.statusCode)
    }

    @Test
    fun notFoundExceptionReturns404() {
        val response = errorResponse(NotFoundException("resource not found"))
        assertEquals(404, response.statusCode)
    }

    @Test
    fun illegalStateExceptionReturns409() {
        val response = errorResponse(IllegalStateException("conflict"))
        assertEquals(409, response.statusCode)
    }

    @Test
    fun unsupportedOperationExceptionReturns501() {
        val response = errorResponse(UnsupportedOperationException("not implemented"))
        assertEquals(501, response.statusCode)
    }

    @Test
    fun unknownExceptionReturns500() {
        val response = errorResponse(RuntimeException("unknown error"))
        assertEquals(500, response.statusCode)
    }

    @Test
    fun errorMessageIsIncludedInBody() {
        val response = errorResponse(IllegalArgumentException("bad input"))
        assertTrue(response.body?.contains("bad input") == true)
    }

    @Test
    fun nullMessageUsesUnknownError() {
        val response = errorResponse(RuntimeException())
        assertTrue(response.body?.contains("Unknown error") == true)
    }

    @Test
    fun hasContentTypeHeader() {
        val response = errorResponse(RuntimeException("error"))
        assertEquals("application/json", response.headers["Content-Type"])
    }
}

class ErrorResponseFromStatusCodeTest {
    @Test
    fun usesProvidedStatusCode() {
        val response = errorResponse(statusCode = 403, message = "forbidden")
        assertEquals(403, response.statusCode)
    }

    @Test
    fun usesProvidedMessage() {
        val response = errorResponse(statusCode = 403, message = "forbidden")
        assertTrue(response.body?.contains("forbidden") == true)
    }

    @Test
    fun hasContentTypeHeader() {
        val response = errorResponse(statusCode = 403, message = "forbidden")
        assertEquals("application/json", response.headers["Content-Type"])
    }

    @Test
    fun bodyIncludesStatusCodeLabel() {
        // Body envelope carries the symbolic label derived from the status code,
        // not the numeric value (per the no-httpStatus-on-error-body policy).
        val response = errorResponse(statusCode = 403, message = "forbidden")
        assertTrue(response.body?.contains("FORBIDDEN") == true)
    }
}
