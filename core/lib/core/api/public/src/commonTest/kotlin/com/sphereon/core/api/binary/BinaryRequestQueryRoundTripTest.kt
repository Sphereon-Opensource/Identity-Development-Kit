/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.binary

import com.sphereon.core.api.http.GenericHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryRequestQueryRoundTripTest {
    private val request =
        GenericHttpRequest(
            method = "GET",
            path = "/items",
            queryParameters = mapOf("state" to "A", "page" to "1"),
            multiValueQueryParameters = mapOf("state" to listOf("A", "B,C"), "page" to listOf("1")),
        )

    @Test
    fun repeatedQueryValuesSurviveTheBinaryRoundTrip() {
        val binary = BinaryRequest.fromGenericHttpRequest(request, "items.list")
        assertEquals(request.multiValueQueryParameters, binary.multiValueQueryParams)
        assertEquals(mapOf("state" to "A", "page" to "1"), binary.queryParams)

        val back = binary.toGenericHttpRequest()
        assertEquals(request.multiValueQueryParameters, back.multiValueQueryParameters)
        assertEquals(request.queryParameters, back.queryParameters)
    }

    @Test
    fun requestWithoutRepeatedValuesKeepsAnEmptyMultiValueView() {
        val binary = BinaryRequest(commandId = "items.list", queryParams = mapOf("page" to "1"))
        assertEquals(emptyMap(), binary.toGenericHttpRequest().multiValueQueryParameters)
    }
}
