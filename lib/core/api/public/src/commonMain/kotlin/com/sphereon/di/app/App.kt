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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * This is the main app either injected or as a component
 * We provide both the component and an injected version of the same data.
 * Typically, you create the component once. Then the injected version can be injected in any scope anywhere, instead of having to keep a
 * static reference in your application to access it. To be clear there should eb one to keep the scope alive, but at least the rest of your
 * code does not depend on the static reference, since you can simply inject
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("App", exact = true)
interface App : AppProperties {
    /**
     * The application itself. Be aware that not every platform will provide a useful implementation.
     * Mainly the mobile platforms provide an actual application you can use properties/methods from
     */
    val application: Any

    val rootScopeProvider: RootScopeProvider

    val platformInfo: PlatformInfo
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("AppProperties", exact = true)
interface AppProperties {


    val version: String

    /**
     * The application ID. This string is provided by the host platform.
     */
    val appId: String

    val profile: String

}


