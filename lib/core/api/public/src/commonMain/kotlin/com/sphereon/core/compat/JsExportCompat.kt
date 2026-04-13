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

package com.sphereon.core.compat

/**
 * A compatibility annotation that maps to @JsExport on Kotlin/JS targets
 * and is a no-op on all other targets (including wasmJs, wasmWasi, JVM, Native).
 *
 * In Kotlin/Wasm, @JsExport cannot be applied to classes, enums, interfaces,
 * or data classes — only to package-level functions with primitive parameters.
 * This annotation allows commonMain code to mark types for JS export without
 * causing compilation errors on wasmJs/wasmWasi targets.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
expect annotation class JsExportCompat()
