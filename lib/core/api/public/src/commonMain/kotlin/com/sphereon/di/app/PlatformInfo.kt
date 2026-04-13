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

import com.sphereon.core.api.conf.Env
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("PlatformInfo", exact = true)
interface PlatformInfo {
    val osFamily: OsFamily
    val name: String get() = osFamily.name
    val environment: Env get() = Env

    enum class OsFamily { IOS, ANDROID, JVM, JS, NATIVE, WASM_JS }
}


@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlatformInfo>())
class CommonPlatformInfo : PlatformInfo {
    // We only need this because we generate code via ksp common as well. All other implementations replace this one
    override val osFamily = PlatformInfo.OsFamily.JVM
}
