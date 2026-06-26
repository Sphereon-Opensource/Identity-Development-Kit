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
 */

package com.sphereon.core.api.conf

/**
 * Thrown by a [PropertySource] when the configuration it serves cannot be
 * fetched yet because a backing dependency (typically a remotely distributed
 * config slice) is not available.
 *
 * This is a *transient* condition, not a programming error: the request races
 * a lazy, per-tenant config fetch that has not completed (or the platform that
 * serves the slice is briefly unreachable). The synchronous [PropertySource]
 * read contract (`getProperty(): T?`) cannot return an error value, so the
 * source raises this exception to fail closed rather than silently returning a
 * missing value.
 *
 * The command-execution HTTP path maps this distinctly to a 503
 * (`ErrorCategory.UNAVAILABLE`) so the caller can retry, instead of the generic
 * 500 that an unrecognised exception produces.
 */
class ConfigUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
