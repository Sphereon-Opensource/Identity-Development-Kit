/*
 * Copyright 2025 Sphereon International B.V.
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
 */

package com.sphereon.trust.etsi.testutil

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.AppComponent
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
abstract class NativeEtsiTestAppComponent(
    application: Any,
    appId: String = "test",
    profile: String = "console-log-profile",
    version: String = "version-example",
) : NativeEtsiTestAppComponentMerged,
    AbstractAppComponent(application, appId = appId, profile = profile, version = version, rootScopeProvider = DefaultRootScopeProvider())

@SingleIn(UserScope::class)
@DependencyGraph(UserScope::class)
abstract class NativeEtsiTestContextComponent(
    @DependencyGraph val appComponent: NativeEtsiTestAppComponent
) : NativeEtsiTestContextComponentMerged

@DependencyGraph(SessionScope::class)
@SingleIn(SessionScope::class)
@DependencyGraph
abstract class NativeEtsiTestSessionComponent(
    @DependencyGraph val appComponent: NativeEtsiTestAppComponent,
    @DependencyGraph val contextComponent: NativeEtsiTestContextComponent
) : NativeEtsiTestSessionComponentMerged

actual fun createEtsiTestAppComponent(testInstance: Any): AppComponent {
    return createNativeEtsiTestAppComponent("test")
}
