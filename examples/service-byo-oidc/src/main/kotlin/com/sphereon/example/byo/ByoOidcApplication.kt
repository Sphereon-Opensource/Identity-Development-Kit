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

import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.ktor.server.jwt.JwtAuthentication
import com.sphereon.oauth2.jwt.validation.IdpConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * BYO OIDC example entry point.
 *
 * A bring-your-own-OIDC deployment uses IDK's real JWT validation stack
 * against an externally operated IdP (Keycloak, Okta, Entra ID, etc.). The
 * demo installs two Ktor plugins:
 *
 *  1. [KotlinInjectPlugin] attaches the Metro AppGraph to the Ktor pipeline
 *     and builds a per-request session graph on every call. This exposes
 *     `call.getAppService<T>()` and `call.getSessionService<T>()` to the rest
 *     of the stack.
 *  2. [JwtAuthentication] validates the bearer token, resolves identity, and
 *     stashes a `SessionContext` on the call. With [KotlinInjectPlugin]
 *     installed, it pulls IDK's session-scoped
 *     [com.sphereon.oauth2.jwt.validation.JwtValidationService] and the
 *     app-scoped [com.sphereon.di.context.IdentityResolutionPipeline] and
 *     [com.sphereon.di.session.SessionContextFactory] directly out of the
 *     graphs. No bridging code is required.
 *
 * What's "BYO" in this demo is the IdP, not the validator. Any OIDC-compliant
 * IdP the operator prefers can be plugged in by pointing `AUTH_IDP_ISSUER`
 * at its realm URL. The validator is 100 % IDK.
 *
 * Single-IdP scope: the registry is seeded with the one IdP configured here
 * and `strictIssuerMatching` defaults to `true`, so tokens from any other
 * issuer fail closed. Multi-IdP tenant routing is an EDK concern and is out
 * of scope for this example.
 *
 * Environment:
 *  - AUTH_IDP_ISSUER   required, e.g. `https://keycloak.example.com/realms/demo`
 *  - AUTH_AUDIENCE     optional, defaults to `byo-demo`
 *  - PORT              optional, defaults to 8080
 */
fun main() {
    val issuer =
        System.getenv(ENV_ISSUER)
            ?: error("$ENV_ISSUER is required (e.g. https://keycloak.example.com/realms/demo)")
    val audience = System.getenv(ENV_AUDIENCE) ?: DEFAULT_AUDIENCE
    val port = System.getenv(ENV_PORT)?.toIntOrNull() ?: DEFAULT_PORT

    println("Starting BYO OIDC example on :$port")
    println("  Issuer:   $issuer")
    println("  Audience: $audience")

    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        configureByoOidcModule(
            ByoOidcConfig(
                issuer = issuer,
                audience = audience,
                jwksUri = null,
            ),
        )
    }.start(wait = true)
}

/**
 * Configuration for the BYO example module.
 *
 * @property issuer     The IdP issuer URL. Tokens whose `iss` claim does not
 *                      match (after trailing-slash normalisation) are rejected.
 * @property audience   The expected `aud` claim value.
 * @property jwksUri    Optional explicit JWKS URI. When null, IDK's JWKS URL
 *                      resolver derives the URL from the Keycloak convention
 *                      (`<issuer>/protocol/openid-connect/certs`) - set
 *                      explicitly for non-Keycloak providers.
 */
data class ByoOidcConfig(
    val issuer: String,
    val audience: String,
    val jwksUri: String?,
)

/**
 * Ktor module. Builds the IDK AppGraph, installs [KotlinInjectPlugin] so the
 * session graph is available per request, installs [JwtAuthentication] (its
 * default service resolvers read from the kotlin-inject graphs), and exposes
 * the demo routes.
 *
 * Plugin install order matters: [KotlinInjectPlugin] must be installed before
 * [JwtAuthentication] so that the per-call session graph is attached by the
 * time the JWT plugin's `onCall` handler fires.
 */
fun Application.configureByoOidcModule(config: ByoOidcConfig) {
    val idpConfig =
        IdpConfig
            .oidc(
                id = "primary",
                issuer = config.issuer,
                audience = config.audience,
            ).copy(
                // When non-null, `VerifyJwtCommand` passes this through to
                // IDK's `JwksUrlExternalIdentifierResolutionService`, which
                // is what lets this demo reach a testcontainer's mapped port
                // that `.well-known/openid-configuration` won't know about.
                jwksUri = config.jwksUri ?: defaultKeycloakJwksUri(config.issuer),
            )

    val appGraph = createByoOidcAppGraph(idpConfig = idpConfig)

    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
        // The JWT plugin populates its own SessionContext from the validated
        // claims (stashed under SessionContextAttributeKey). The kotlin-inject
        // plugin's tenant resolver is only used as the DI vehicle, so a fixed
        // resolver pinned to the issuer is sufficient for this single-IdP demo.
        tenantResolver = FixedTenantResolver(config.issuer)
    }

    install(JwtAuthentication) {
        // jwtValidationService, identityResolutionPipeline, and
        // sessionContextFactory all default to reading from the kotlin-inject
        // graphs via call.getSessionService / call.getAppService. No wiring
        // needed beyond installing both plugins.
        this.requireAuth = true
        this.anonymousPaths = listOf("/health", "/ready")
        this.expectedAudience = config.audience
    }

    routing {
        get("/health") { call.respondText("ok") }
        get("/ready") { call.respondText("ok") }
        meEndpoint()
    }
}

/**
 * Keycloak's JWKS endpoint convention. Other OIDC providers publish JWKS
 * through their discovery document; the demo keeps this simple by hard-coding
 * the Keycloak path and letting other providers supply `jwksUri` explicitly.
 */
private fun defaultKeycloakJwksUri(issuer: String): String = "${issuer.trimEnd('/')}/protocol/openid-connect/certs"

private const val ENV_ISSUER = "AUTH_IDP_ISSUER"
private const val ENV_AUDIENCE = "AUTH_AUDIENCE"
private const val ENV_PORT = "PORT"
private const val DEFAULT_AUDIENCE = "byo-demo"
private const val DEFAULT_PORT = 8080
