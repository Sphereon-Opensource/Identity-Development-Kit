/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.defaults.app

import com.sphereon.di.app.App
import com.sphereon.di.app.PlatformInfo
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<App>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppImpl", exact = true)
class AppImpl(
    override val application: Any,
    override val rootScopeProvider: RootScopeProvider,
    override val platformInfo: PlatformInfo,
    @Named("version") override val version: String,
    @Named("appId") override val appId: String,
    @Named("profile") override val profile: String,
) : App
