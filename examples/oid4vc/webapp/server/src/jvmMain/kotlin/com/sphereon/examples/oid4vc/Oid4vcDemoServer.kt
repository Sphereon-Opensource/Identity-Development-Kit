package com.sphereon.examples.oid4vc

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import io.ktor.client.engine.cio.CIO as ClientCIO

fun main() {
    val issuerUrl = System.getenv("ISSUER_URL") ?: "http://localhost:8082"
    val verifierUrl = System.getenv("VERIFIER_URL") ?: "http://localhost:8083"
    val externalBaseUrl = System.getenv("EXTERNAL_BASE_URL") ?: "http://localhost:8080"

    println("Starting OID4VC Demo Web Application...")
    println("  Issuer URL:   $issuerUrl")
    println("  Verifier URL: $verifierUrl")
    println("  External URL: $externalBaseUrl")

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureOid4vcDemo(issuerUrl, verifierUrl, externalBaseUrl)
    }.start(wait = true)
}

fun Application.configureOid4vcDemo(
    issuerUrl: String,
    verifierUrl: String,
    externalBaseUrl: String,
) {
    val httpClient =
        HttpClient(ClientCIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
            }
        }

    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            },
        )
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                """{"error": "${cause.message?.replace("\"", "'")}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }

        route("/api") {
            // Issuer metadata
            get("/issuer/metadata") {
                val response = httpClient.get("$issuerUrl/.well-known/openid-credential-issuer")
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // VCT type metadata
            get("/issuer/vct/{type}") {
                val type = call.parameters["type"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val response = httpClient.get("$externalBaseUrl/oid4vci/vct/$type")
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Create credential offer
            post("/issuer/offers") {
                val body = call.receiveText()
                val response =
                    httpClient.post("$issuerUrl/oid4vci/backend/credential/offers") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Get offer status
            get("/issuer/offers/{id}/status") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val response = httpClient.get("$issuerUrl/oid4vci/backend/credential/offers/$id")
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Create OID4VP auth request
            post("/verifier/requests") {
                val body = call.receiveText()
                val response =
                    httpClient.post("$verifierUrl/oid4vp/backend/auth/requests") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Get auth request status
            get("/verifier/requests/{id}/status") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val response = httpClient.get("$verifierUrl/oid4vp/backend/auth/requests/$id")
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Config
            get("/config") {
                call.respondText(
                    """{"externalBaseUrl":"$externalBaseUrl"}""",
                    ContentType.Application.Json,
                )
            }
        }

        // Static files (React SPA build output)
        staticResources("/", "webapp") {
            default("index.html")
        }
    }
}
