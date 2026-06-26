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
    // Demo profile name. Surfaces HAIP / x509-* compose env-files to the frontend so the
    // verifier UI can lock the options HAIP fixes (response_mode = direct_post.jwt,
    // request_uri_method = post). Set via DEMO_PROFILE in compose; defaults to "default".
    val demoProfile = System.getenv("DEMO_PROFILE")?.takeIf { it.isNotBlank() } ?: "default"

    println("Starting OID4VC Demo Web Application...")
    println("  Issuer URL:   $issuerUrl")
    println("  Verifier URL: $verifierUrl")
    println("  External URL: $externalBaseUrl")
    println("  Profile:      $demoProfile")

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureOid4vcDemo(issuerUrl, verifierUrl, externalBaseUrl, demoProfile)
    }.start(wait = true)
}

fun Application.configureOid4vcDemo(
    issuerUrl: String,
    verifierUrl: String,
    externalBaseUrl: String,
    demoProfile: String = "default",
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
                val response = httpClient.get("$issuerUrl/.well-known/openid-credential-issuer/oid4vci")
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // VCT type metadata
            get("/issuer/vct/{type}") {
                val type = call.parameters["type"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val response = httpClient.get("$externalBaseUrl/public/schema/vct/$type")
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Create credential offer
            post("/issuer/offers") {
                val body = call.receiveText()
                val response =
                    httpClient.post("$issuerUrl/api/oid4vci/v1/backend/credential/offers") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                call.respondBytes(response.readRawBytes(), ContentType.Application.Json, HttpStatusCode(response.status.value, ""))
            }

            // Get offer status
            get("/issuer/offers/{id}/status") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val response = httpClient.get("$issuerUrl/api/oid4vci/v1/backend/credential/offers/$id")
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
                // `profile` is the compose env-file profile (default / did-jwk / x509-san-dns
                // / x509-hash / haip). When `haip`, the frontend locks options HAIP fixes:
                // response_mode = direct_post.jwt, request_uri_method = post (HAIP §5).
                val haipMode = demoProfile.equals("haip", ignoreCase = true)
                call.respondText(
                    """{"externalBaseUrl":"$externalBaseUrl","profile":"$demoProfile","haip":$haipMode}""",
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
