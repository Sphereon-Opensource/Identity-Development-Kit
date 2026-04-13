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

package com.sphereon.core.api.conf

import kotlinx.io.files.Path
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName


@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigEnvironment", exact = true)
interface ConfigEnvironment : PropertyResolver {

    val parent: ConfigEnvironment?
    val level: ConfigLevel
    fun getActiveProfile(): String

    fun getAppName(): String


    fun getConfigLocation(): Path
    fun getPropertySources(includeParents: Boolean = true): PropertySources

    fun getNamespace(): String
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AppConfigEnvironment", exact = true)
interface AppConfigEnvironment : ConfigEnvironment {
    override val parent: ConfigEnvironment?
        get() = null
    override val level: ConfigLevel
        get() = ConfigLevel.APP

    /**
     * Component interface to expose AppConfigEnvironment publicly from the AppComponent.
     * This allows Spring Boot property sources and other services to access the config environment.
     */
    @ContributesTo(AppScope::class)
    interface Component {
        val appConfigEnvironment: AppConfigEnvironment
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("TenantConfigEnvironment", exact = true)
interface TenantConfigEnvironment : ConfigEnvironment {
    override val parent: AppConfigEnvironment
    override val level: ConfigLevel
        get() = ConfigLevel.TENANT

    /**
     * Component interface to expose TenantConfigEnvironment publicly from the UserContextComponent.
     * This allows Spring Boot property sources and other services to access the config environment.
     */
    @ContributesTo(UserScope::class)
    interface Component {
        val tenantConfigEnvironment: TenantConfigEnvironment
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("PrincipalConfigEnvironment", exact = true)
interface PrincipalConfigEnvironment : ConfigEnvironment {
    override val parent: TenantConfigEnvironment
    override val level: ConfigLevel
        get() = ConfigLevel.PRINCIPAL

    /**
     * Component interface to expose PrincipalConfigEnvironment publicly from the UserContextComponent.
     * This allows Spring Boot property sources and other services to access the config environment.
     */
    @ContributesTo(UserScope::class)
    interface Component {
        val principalConfigEnvironment: PrincipalConfigEnvironment
    }
}
