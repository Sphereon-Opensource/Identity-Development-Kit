package com.sphereon.crypto.kms.rest.server

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.AppComponent
import com.sphereon.di.app.RootScopeProvider
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

/**
 * Example KMS REST Server using Ktor with kotlin-inject.
 *
 * This example shows how to:
 * 1. Create an AppComponent
 * 2. Pass it to the Ktor configuration
 * 3. Enable automatic DI with HttpAdapter from session scope
 */
fun main() {
    println("Starting KMS Ktor Server...")

    // Step 1: Create your AppComponent (one time at startup)
    val appComponent = createKmsKtorAppComponent(
        application = Unit,
        appId = "kms-ktor-server",
        profile = System.getenv("APP_PROFILE") ?: "development",
        version = "1.0.0"
    )

    // Step 2: Start Ktor server and pass AppComponent to configuration
    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureKms(appComponent)  // ← AppComponent passed here!
    }.start(wait = true)
}

/**
 * Example AppComponent for KMS Ktor Server.
 *
 * This merges all @ContributesBinding services including:
 * - KmsHttpAdapter (session-scoped)
 * - KmsRestService and all its dependencies
 * - All KMS providers
 *
 * Note: Remove @DependencyGraph annotation to let KSP generate it automatically,
 * or manually extend KmsKtorAppComponentMerged.
 */
@DependencyGraph(AppScope::class)
abstract class KmsKtorAppComponent : AbstractAppComponent() {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): KmsKtorAppComponent
    }

}

fun createKmsKtorAppComponent(
    application: Any,
    appId: String = "kms-ktor-server",
    profile: String = "production",
    version: String = "1.0.0"
): AppComponent {
    val component = createGraphFactory<KmsKtorAppComponent.Factory>().create(
        application = application,
        version = version,
        appId = appId,
        profile = profile,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}
