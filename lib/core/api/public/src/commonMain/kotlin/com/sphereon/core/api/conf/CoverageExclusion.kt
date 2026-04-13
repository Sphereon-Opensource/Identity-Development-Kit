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

/**
 * Marker annotation to exclude classes from code coverage analysis.
 *
 * Apply this to data classes whose generated methods (equals, hashCode, copy)
 * create excessive branch coverage requirements that don't reflect actual
 * business logic coverage.
 *
 * Kover is configured to exclude classes annotated with this annotation
 * from coverage verification.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CoverageExcludedDataClass
