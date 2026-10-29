package com.sphereon.ktor.server.inject.http

import com.sphereon.core.api.binary.BinaryRequest
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatedQueryParameterTest {
    @Test
    fun repeatedQueryParametersKeepEveryOccurrence() = testApplication {
        application {
            routing {
                get("/items") {
                    val request = call.request.toGenericHttpRequest(call)
                    assertEquals(listOf("A", "B,C"), request.multiValueQueryParameters["state"])
                    assertEquals(listOf("1"), request.multiValueQueryParameters["page"])
                    // The scalar view is unchanged.
                    assertEquals("1", request.queryParameters["page"])

                    val roundTrip = BinaryRequest.fromGenericHttpRequest(request, "items.list").toGenericHttpRequest()
                    assertEquals(request.multiValueQueryParameters, roundTrip.multiValueQueryParameters)
                    call.respondText("ok")
                }
            }
        }
        assertEquals(HttpStatusCode.OK, client.get("/items?state=A&state=B%2CC&page=1").status)
    }
}
