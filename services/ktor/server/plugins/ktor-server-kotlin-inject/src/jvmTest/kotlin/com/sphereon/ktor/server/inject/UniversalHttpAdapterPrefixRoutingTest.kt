/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.ktor.server.inject

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class UniversalHttpAdapterPrefixRoutingTest {
    @Test
    fun configuredPrefixDispatchesDescendantPaths() =
        testApplication {
            application {
                installUniversalHttpAdapters {
                    pathPrefix = "/api/kms/v1"
                }
            }

            // There is intentionally no KotlinInject session in this minimal host. A matching
            // universal route therefore reaches dispatch and returns its controlled 500. A 404
            // would mean Ktor never mounted the descendant route at all.
            val response = client.get("/api/kms/v1/providers")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
