/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.core.api.conf

/**
 * Reads a classpath resource as a String.
 *
 * On JVM: uses the context class loader to find the resource.
 * On other platforms: returns null (classpath not available).
 *
 * @param name the resource name (e.g., "application.properties", "application.yml")
 * @return the resource content as a String, or null if not found or not supported
 */
expect fun readClasspathResource(name: String): String?

/**
 * Reads a classpath resource as a [ByteArray].
 *
 * On JVM: uses the context class loader to find the resource. Used for binary assets such as
 * SVG/CSS/PNG bundled into a module's `resources/` directory (e.g. the IDK login renderer
 * static assets).
 * On other platforms: returns null (classpath not available).
 *
 * @param name the resource name (e.g., "login/sphereon/img/sphereon-logo.svg")
 * @return the resource content as a [ByteArray], or null if not found or not supported
 */
expect fun readClasspathResourceBytes(name: String): ByteArray?
