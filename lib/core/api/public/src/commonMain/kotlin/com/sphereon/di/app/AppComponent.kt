/*
 * © 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.di.app

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.PropertiesFileAppPropertySource
import com.sphereon.core.api.conf.PropertySourceBootstrap
import kotlinx.coroutines.cancel
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import software.amazon.app.platform.scope.Scoped
import software.amazon.app.platform.scope.coroutine.metro.AppScopeCoroutineScopeGraph
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.context.UserContextManager
import dev.zacsweers.metro.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import software.amazon.app.platform.scope.coroutine.metro.CoroutineDispatcherGraph
import software.amazon.app.platform.scope.coroutine.DefaultCoroutineDispatcher
import software.amazon.app.platform.scope.coroutine.IoCoroutineDispatcher
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Shared interface for the app component. The final components live in the platform specific source
 * folders in order to have access to platform specific code.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppComponent", exact = true)
interface AppComponent: App {

    val userContextManager: UserContextManager

   /** All [Scoped] instances part of the app scope. */
    @ForScope(AppScope::class)
    public val appScopedInstances: Set<Scoped>

    /** The coroutine scope that runs as long as the app scope is alive. */
    @ForScope(AppScope::class)
    public val appScopeCoroutineScopeScoped: CoroutineScopeScoped
    fun destroy()
}


/**
 * API to get the application and ID. There are different implementations for Android, iOS, Jvm, Native etc.
 * their implementations are bound to this interface through code-gen. The platform implementation can be found in the respective platform folders
 *
 * Typically, the component extending this abstract version will have the @DependencyGraph annotation on it
 *
 * So we expect in your app to have your App Component have the @DependencyGraph on it and extend this component.
 *
 * @property application The application itself. Be aware that not every platform will provide a useful implementation.
 * Mainly the mobile platforms provide an actual application you can use properties/methods from
 * @property appId The application ID. This string is provided by the host platform.
 * @property profile The profile of the application. This is typically used for development and testing or for different deployments of the same app.
 * @property version The version of the application.
 *
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractAppComponent", exact = true)
abstract class AbstractAppComponent : AppComponent {

    abstract override val application: Any

    @get:Named("version")
    abstract override val version: String

    @get:Named("appId")
    abstract override val appId: String

    @get:Named("profile")
    abstract override val profile: String

    abstract override val rootScopeProvider: RootScopeProvider

    open fun initRootScopeProvider(): AppComponent {
        rootScopeProvider.create(this)
        (this as? PropertySourceBootstrap.Component)?.propertySourceBootstrap?.registerAppSources()
        val appConfigService = (this as? AppConfigService.Component)?.appConfigService
        val propertiesSource = (this as? PropertiesFileAppPropertySource.Component)?.propertiesFileAppPropertySource
        if (appConfigService != null && propertiesSource != null) {
            val localSources = appConfigService.getPropertySources(includeParents = false)
            if (!localSources.contains(propertiesSource.getName())) {
                appConfigService.addPropertySource(propertiesSource)
            }
        }
        return this
    }

    @ForScope(AppScope::class)
    abstract override val appScopedInstances: Set<Scoped>

    @ForScope(AppScope::class)
    abstract override val appScopeCoroutineScopeScoped: CoroutineScopeScoped


    override fun destroy(): Unit {
        if (!rootScopeProvider.isDestroyed() && !rootScopeProvider.rootScope.isDestroyed()) {
            rootScopeProvider.destroy()
        }
        try {
            // Let's be sure to also cleanup our instance. Should be handled by the above call though
            appScopeCoroutineScopeScoped.cancel()
        } catch (_: Exception) {
            // Silently ignore cleanup errors
        }
    }

}

/**
 * Provides an empty Scoped instance for AppScope to satisfy the Set<Scoped> multibinding.
 */
@ContributesTo(
    AppScope::class,
    replaces = [
        software.amazon.app.platform.scope.coroutine.metro.CoroutineDispatcherGraph::class,
        software.amazon.app.platform.scope.coroutine.metro.AppScopeCoroutineScopeGraph::class,
    ]
)
interface AppScopedProviders {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideAppScopeEmptyScoped(): Scoped = Scoped.NO_OP

    @Provides
    @DefaultCoroutineDispatcher
    fun provideIdkDefaultCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @software.amazon.app.platform.scope.coroutine.IoCoroutineDispatcher
    fun provideIdkIoCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @SingleIn(AppScope::class)
    @ForScope(AppScope::class)
    fun provideIdkAppScopeCoroutineScopeScoped(
        @software.amazon.app.platform.scope.coroutine.IoCoroutineDispatcher dispatcher: CoroutineDispatcher
    ): software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped {
        return software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped(
            dispatcher + kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.CoroutineName("AppScope")
        )
    }

    @Provides
    @ForScope(AppScope::class)
    fun provideIdkAppCoroutineScope(
        @ForScope(AppScope::class) scoped: software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
    ): kotlinx.coroutines.CoroutineScope {
        return scoped.createChild()
    }
}
