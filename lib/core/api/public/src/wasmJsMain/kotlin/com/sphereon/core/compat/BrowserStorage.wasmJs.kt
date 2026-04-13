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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.sphereon.core.compat

import kotlin.js.ExperimentalWasmJsInterop

@JsFun("() => typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'")
private external fun isBrowserEnv(): Boolean

@JsFun(
    """(key) => {
    try { return localStorage.getItem(key); }
    catch(e) { return null; }
}""",
)
private external fun localStorageGet(key: JsString): JsString?

@JsFun(
    """(key, value) => {
    try { localStorage.setItem(key, value); }
    catch(e) { /* ignore */ }
}""",
)
private external fun localStorageSet(
    key: JsString,
    value: JsString,
)

actual fun readBrowserStorageItem(key: String): String? {
    if (!isBrowserEnv()) return null
    return localStorageGet(key.toJsString())?.toString()
}

actual fun writeBrowserStorageItem(
    key: String,
    value: String,
) {
    if (!isBrowserEnv()) return
    localStorageSet(key.toJsString(), value.toJsString())
}
