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

package com.sphereon.core.plugins

import com.sphereon.di.session.SessionScope
import kotlin.reflect.KClass

/*
 * Annotation for denoting the scope of a plugin-specific graph or session in the application.
 *
 * This annotation is used to define a runtime-scoped dependency context for services and components
 * that belong to the lifecycle of a particular plugin. It ensures isolation and proper resource
 * management within the plugin framework.
 *
 * The `PluginScope` is typically applied in scenarios where services or components should be
 * constrained within the context of a plugin's lifecycle. It aids in managing dependencies and
 * their initialization/extensions appropriately.
 *
 * Annotated elements are restricted to being initialized and used while the corresponding plugin
 * is active and within its valid scope. This enables controlled behavior for instances tied to
 * plugin services.
 */

// @Scope
// @Retention(AnnotationRetention.RUNTIME)
// annotation class PluginScope(val id: String)

/**
 * Marker annotation that indicates a session interface should have a composite facade
 * generated if more than one implementation is present.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class MultiService(
    val id: String,
    val plugin: String,
    val scope: KClass<*> = SessionScope::class,
)
