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
 */

package com.sphereon.core.api.testutil

import com.sphereon.core.api.app.CoreApiAppExtensionComponent
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.di.context.CrossContextOperations
import com.sphereon.core.defaults.app.AppImpl
import com.sphereon.core.defaults.context.CrossContextOperationsImpl
import com.sphereon.di.app.App
import com.sphereon.di.app.AppComponent

expect fun createCoreApiTestAppComponent(
    testInstance: Any,
    appId: String = "test",
    profile: String = "console-log-profile",
    version: String = "0.0.1-TEST"
): AppComponent

// Extension helpers so commonTest code can access merged component properties
// without referencing platform-specific merged interfaces.

val AppComponent.app: App
    get() = (this as AppImpl.Component).app

val AppComponent.appConfigService: AppConfigService
    get() = (this as CoreApiAppExtensionComponent).appConfig

val AppComponent.crossContextOperations: CrossContextOperations
    get() = (this as CrossContextOperationsImpl.Component).crossContextOperations

fun AppComponent.appLogger() = (this as CoreApiAppExtensionComponent).appLogger()
