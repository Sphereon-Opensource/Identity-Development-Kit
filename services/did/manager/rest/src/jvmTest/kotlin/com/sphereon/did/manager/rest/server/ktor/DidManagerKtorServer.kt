/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server.ktor

import com.sphereon.core.api.log.Log
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    Log.app().withTag("DidManagerKtorServer").info("Starting DID Manager REST Server")

    val appGraph =
        createDidManagerAppGraph(
            application = Unit,
            appId = "did-manager",
            profile = System.getenv("APP_PROFILE") ?: "development",
            version = "1.0.0",
        )

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureDidManager(appGraph)
    }.start(wait = true)
}

/**
 * Configures a Ktor application with the DID Manager REST adapter.
 *
 * Uses [installUniversalHttpAdapters] to auto-discover every [com.sphereon.core.api.http.HttpAdapter]
 * contributed via DI. Downstream projects that want to add their own adapters simply include
 * this function from a host app that has those adapters on the classpath.
 *
 * @param tenantResolver Strategy for resolving the tenant from each incoming call.
 *                       Header-based resolution has been removed from the Ktor plugin (clients
 *                       cannot be trusted to supply a tenant header); callers must supply a
 *                       resolver. Defaults to [FixedTenantResolver] returning "default" — fine
 *                       for single-tenant local runs and demos; production deployments should
 *                       pass a real resolver (e.g. the TenantResolutionPlugin from
 *                       `services/ktor-server-tenant-resolution`).
 */
fun Application.configureDidManager(
    appGraph: AppGraph,
    tenantResolver: TenantResolver = FixedTenantResolver("default"),
) {
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
        this.tenantResolver = tenantResolver
    }
    log.info("KotlinInject plugin installed - full DI enabled")

    install(ContentNegotiation) { json() }

    routing {
        get("/health") { call.respondText("OK") }
    }

    installUniversalHttpAdapters()
}

@DependencyGraph(AppScope::class)
abstract class DidManagerAppGraph : AbstractAppGraph() {
    // The DidRepository binding flows from DidRepositorySelectorModule (lib-did-manager-impl)
    // + the persistence dialect contributions on the classpath. Defaults to memory when no
    // `did.persistence.type` property is set; switch to sqlite (or postgresql/mysql via EDK
    // modules) by setting that property and the matching connectionUrl.

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): DidManagerAppGraph
    }
}

fun createDidManagerAppGraph(
    application: Any,
    appId: String = "did-manager",
    profile: String = "production",
    version: String = "1.0.0",
): AppGraph {
    val graph =
        createGraphFactory<DidManagerAppGraph.Factory>().create(
            application = application,
            version = version,
            appId = appId,
            profile = profile,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
