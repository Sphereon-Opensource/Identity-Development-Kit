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

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test for the BYO OIDC example.
 *
 * Boots a Keycloak testcontainer, imports a `demo` realm with one public
 * client and one user, mints a user token via Resource Owner Password
 * Credentials grant, and calls the Ktor app the example wires together.
 *
 * The test skips gracefully when Docker is not available, per the project
 * memory note about CI runners without Docker.
 *
 * JUnit5 test instance lifecycle is PER_CLASS so `@BeforeAll` is non-static
 * and our one-shot container boot can share state across tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ByoOidcE2ETest {
    private lateinit var keycloak: GenericContainer<*>
    private var dockerAvailable: Boolean = false
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeAll
    fun bootKeycloak() {
        // Per the testcontainers-wsl2 skill: do an actual start attempt and
        // skip gracefully when Docker isn't available. DockerClientFactory's
        // isDockerAvailable() is unreliable on Docker 29.x / WSL2.
        try {
            keycloak =
                GenericContainer("quay.io/keycloak/keycloak:26.2")
                    .withExposedPorts(KEYCLOAK_HTTP_PORT)
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    .withEnv("KC_HEALTH_ENABLED", "true")
                    .withCommand("start-dev", "--import-realm")
                    .withCopyFileToContainer(
                        MountableFile.forClasspathResource("demo-realm.json"),
                        "/opt/keycloak/data/import/demo-realm.json",
                    ).waitingFor(
                        Wait
                            .forHttp("/realms/demo/.well-known/openid-configuration")
                            .forPort(KEYCLOAK_HTTP_PORT)
                            .withStartupTimeout(java.time.Duration.ofMinutes(START_TIMEOUT_MINUTES)),
                    )
            keycloak.start()
            dockerAvailable = true
        } catch (e: Throwable) {
            dockerAvailable = false
            println("Docker unavailable, skipping ByoOidcE2ETest: ${e.message}")
        }
    }

    private fun issuerUrl(): String {
        val port = keycloak.getMappedPort(KEYCLOAK_HTTP_PORT)
        val host = keycloak.host
        return "http://$host:$port/realms/demo"
    }

    @Test
    fun testAuthenticatedRequestReturnsSessionContext() =
        runTest {
            Assumptions.assumeTrue(dockerAvailable, "Docker not available; skipping")

            val issuer = issuerUrl()
            val accessToken = mintUserToken(issuer)

            testApplication {
                application {
                    configureByoOidcModule(
                        ByoOidcConfig(
                            issuer = issuer,
                            audience = "byo-demo",
                            jwksUri = null,
                        ),
                    )
                }

                val response =
                    client.get("/api/v1/me") {
                        header(HttpHeaders.Authorization, "Bearer $accessToken")
                    }
                assertEquals(HttpStatusCode.OK, response.status, "Expected 200 for authenticated request")
                val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                // OidcPrincipalResolver picks the OIDC `sub` claim (a stable
                // Keycloak-assigned UUID) over `preferred_username` — correct
                // OIDC behaviour. All we can assert is that we got a non-blank
                // principal (i.e. the token was validated and claims were read).
                val principal = body["principal"]?.jsonPrimitive?.content
                assertNotNull(principal, "principal must be populated for a valid token")
                assertTrue(
                    principal.isNotBlank() && principal != "anonymous",
                    "Expected resolved principal, got: $principal"
                )
                // Tenant will be the anonymous tenant id because no tenant
                // resolver was registered on the IDK default pipeline. That's
                // intentional for the BYO demo — tenancy is EDK's story.
                assertNotNull(body["sessionId"]?.jsonPrimitive?.content)
            }
        }

    @Test
    fun testUnauthenticatedRequestReturns401WithRfc6750Header() =
        runTest {
            Assumptions.assumeTrue(dockerAvailable, "Docker not available; skipping")
            val issuer = issuerUrl()

            testApplication {
                application {
                    configureByoOidcModule(
                        ByoOidcConfig(
                            issuer = issuer,
                            audience = "byo-demo",
                            jwksUri = null,
                        ),
                    )
                }

                val response = client.get("/api/v1/me")
                assertEquals(HttpStatusCode.Unauthorized, response.status)
                val www = response.headers[HttpHeaders.WWWAuthenticate]
                assertNotNull(www, "WWW-Authenticate header must be set on 401")
                assertTrue(www.startsWith("Bearer error=\"invalid_token\""), "Got: $www")
            }
        }

    @Test
    fun testHealthEndpointBypassesAuth() =
        runTest {
            Assumptions.assumeTrue(dockerAvailable, "Docker not available; skipping")
            val issuer = issuerUrl()

            testApplication {
                application {
                    configureByoOidcModule(
                        ByoOidcConfig(
                            issuer = issuer,
                            audience = "byo-demo",
                            jwksUri = null,
                        ),
                    )
                }

                val response = client.get("/health")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("ok", response.bodyAsText())
            }
        }

    /**
     * Mint a user access token from Keycloak via the Resource Owner
     * Password Credentials grant. The demo realm enables direct access
     * grants on `demo-client` so this works without a browser.
     */
    private suspend fun mintUserToken(issuer: String): String {
        val tokenUrl = "$issuer/protocol/openid-connect/token"
        val body =
            "grant_type=password" +
                "&client_id=demo-client" +
                "&username=testuser" +
                "&password=testpass"

        // Use a short-lived HTTP client rather than pulling in Ktor client
        // just for this — keeps deps minimal.
        val conn =
            (
                java.net
                    .URI(tokenUrl)
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            ).apply {
                requestMethod = "POST"
                setRequestProperty(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        val status = conn.responseCode
        val responseBody =
            (if (status in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader()
                .use { it.readText() }
        check(status == 200) { "Token endpoint returned $status: $responseBody" }

        val obj = json.parseToJsonElement(responseBody).jsonObject
        val token = obj["access_token"]?.jsonPrimitive?.content
        return token ?: error("No access_token in response: $responseBody")
    }

    private companion object {
        private const val KEYCLOAK_HTTP_PORT = 8080
        private const val START_TIMEOUT_MINUTES = 3L
    }
}
