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

private val isBrowser: Boolean =
    js(
        "typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'",
    ) as Boolean

actual fun readBrowserStorageItem(key: String): String? {
    if (!isBrowser) return null
    return try {
        val storage: dynamic = js("window.localStorage")
        storage.getItem(key) as? String
    } catch (_: dynamic) {
        null
    }
}

actual fun writeBrowserStorageItem(
    key: String,
    value: String,
) {
    if (!isBrowser) return
    try {
        val storage: dynamic = js("window.localStorage")
        storage.setItem(key, value)
    } catch (_: dynamic) {
        // Ignore — storage may be full or blocked
    }
}
