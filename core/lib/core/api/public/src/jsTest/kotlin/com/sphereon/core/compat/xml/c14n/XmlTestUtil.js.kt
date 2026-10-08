/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.core.compat.xml.c14n

@JsModule("jsdom")
external val jsdomModule: dynamic

actual fun ensureDomAvailable() {
    if (js("typeof globalThis.document !== 'undefined'") as Boolean) return
    initJsdom(jsdomModule)
}

private fun initJsdom(module: dynamic) {
    val dom: dynamic = js("new module.JSDOM('<!DOCTYPE html><html><body></body></html>')")
    installWindowGlobals(dom.window)
}

/**
 * Copies all browser DOM globals from the jsdom window onto Node.js globalThis.
 * This is the same approach used by the `global-jsdom` npm package.
 */
private fun installWindowGlobals(window: dynamic) {
    js(
        """
        Object.getOwnPropertyNames(window).forEach(function(key) {
            if (typeof globalThis[key] === 'undefined') {
                try { globalThis[key] = window[key]; } catch(e) {}
            }
        });
    """,
    )
}
