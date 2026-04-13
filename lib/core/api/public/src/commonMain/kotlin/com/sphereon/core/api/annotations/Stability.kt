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

package com.sphereon.core.api.annotations

/**
 * Marks an API as beta.
 *
 * Beta APIs are functionally complete but may have minor changes in future versions.
 * They are safe to use in production but consumers should be prepared for potential
 * non-breaking changes in minor version updates.
 *
 * Usage:
 * ```kotlin
 * @Beta
 * fun experimentalFeature() { ... }
 * ```
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class Beta(
    /**
     * Optional description of why this API is in beta.
     */
    val message: String = "",
)

/**
 * Marks an API as experimental.
 *
 * Experimental APIs may change or be removed in any version without notice.
 * They are provided for early feedback and should not be used in production.
 *
 * Usage:
 * ```kotlin
 * @Experimental
 * fun unstableFeature() { ... }
 * ```
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is experimental and may change without notice.",
)
annotation class Experimental(
    /**
     * Optional description of why this API is experimental.
     */
    val message: String = "",
)

/**
 * Marks an API as internal to the IDK library.
 *
 * Internal APIs are not part of the public API contract and may change
 * or be removed at any time. External consumers should not use these APIs.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal to the IDK and should not be used externally.",
)
annotation class InternalApi
