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

import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.httpAdapterDescription
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpAdapterDispatcherTest {

    private class TestAdapter(
        private val predicate: (GenericHttpRequest) -> Boolean,
        private val statusCode: Int
    ) : RoutableHttpAdapter {
        override val id: String = "TEST_${statusCode}"

        override fun describe() = httpAdapterDescription(id) {
            mount {
                serverPrefix = ""
                adapterBasePath = "/"
            }
            endpoint(HttpMethod.GET, "/")
        }

        override fun canHandle(request: GenericHttpRequest): Boolean = predicate(request)
        override suspend fun handleRequest(request: GenericHttpRequest): GenericHttpResponse = GenericHttpResponse(statusCode = statusCode)
    }

    @Test
    fun dispatchReturns404WhenNoAdapterMatches() = runTest {
        val dispatcher = HttpAdapterDispatcher(
            adapters = setOf(
                TestAdapter(predicate = { false }, statusCode = 200)
            )
        )

        val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/oid4vp/request-uri/abc"))
        assertEquals(404, response.statusCode)
    }

    @Test
    fun dispatchRoutesToSingleMatchingAdapter() = runTest {
        val dispatcher = HttpAdapterDispatcher(
            adapters = setOf(
                TestAdapter(predicate = { it.matches("GET", "/a") }, statusCode = 201),
                TestAdapter(predicate = { false }, statusCode = 200)
            )
        )

        val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/a"))
        assertEquals(201, response.statusCode)
    }

    @Test
    fun dispatchReturns500WhenMultipleAdaptersMatch() = runTest {
        val dispatcher = HttpAdapterDispatcher(
            adapters = setOf(
                TestAdapter(predicate = { true }, statusCode = 200),
                TestAdapter(predicate = { true }, statusCode = 201)
            )
        )

        val response = dispatcher.dispatch(GenericHttpRequest(method = "GET", path = "/anything"))
        assertEquals(500, response.statusCode)
    }
}
