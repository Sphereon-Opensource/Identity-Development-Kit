package com.sphereon.example.graalvm

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph

/**
 * Example AppComponent for GraalVM Native Image demonstration.
 *
 * This component is processed by kotlin-inject at compile time,
 * generating concrete implementation code. This means no runtime
 * reflection is needed, making it perfect for GraalVM Native Image.
 *
 * Services are provided via @DependencyGraph which merges all @ContributesBinding
 * annotated implementations into this component.
 */
@DependencyGraph(AppScope::class)
abstract class GraalVMExampleAppComponent(
    application: Any,
    appId: String,
    profile: String,
    version: String
) : AbstractAppComponent(
    application = application,
    version = version,
    appId = appId,
    profile = profile,
    rootScopeProvider = DefaultRootScopeProvider()
) {
    // kotlin-inject generates properties for services via @ContributesBinding
    // We don't need to declare them explicitly - they're merged via @DependencyGraph

    companion object {
        fun init(
            application: Any,
            appId: String = "graalvm-example",
            profile: String = "production",
            version: String = "1.0.0"
        ): GraalVMExampleAppComponent {
            val component = createGraph<GraalVMExampleAppComponent>(
                application,
                appId,
                profile,
                version
            )
            component.initRootScopeProvider()
            return component
        }
    }
}
