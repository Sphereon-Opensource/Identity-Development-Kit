/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.example.byo

import com.sphereon.ktor.server.jwt.SessionContextAttributeKey
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Registers GET /api/v1/me on the supplied [Routing] node.
 *
 * The route reads the [com.sphereon.di.session.SessionContext] that
 * [com.sphereon.ktor.server.jwt.JwtAuthentication] stashed on the call and
 * renders the tenant + principal the pipeline resolved. Useful as a smoke
 * test of the BYO wiring.
 */
fun Routing.meEndpoint() {
    get("/api/v1/me") {
        val session = call.attributes[SessionContextAttributeKey]
        val principal = session.context.principal
        val body: JsonObject =
            buildJsonObject {
                put("sessionId", JsonPrimitive(session.sessionId))
                put("tenantId", JsonPrimitive(session.context.tenant.tenantId))
                put("principal", JsonPrimitive(principal?.toString() ?: "anonymous"))
            }
        call.respondText(
            text = body.toString(),
            contentType = ContentType.Application.Json,
        )
    }
}
