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

package com.sphereon.trust.etsi.testutil

// Top-level js() expressions for wasmJs interop (must be top-level function bodies)
private fun isDocumentDefined(): Boolean = js("typeof globalThis.document !== 'undefined'")

private fun createJsdom(): JsAny = js("new (require('jsdom').JSDOM)('<!DOCTYPE html><html><body></body></html>')")

private fun getWindow(dom: JsAny): JsAny = js("dom.window")

private fun copyGlobals(window: JsAny): Unit =
    js(
        """
        Object.getOwnPropertyNames(window).forEach(function(key) {
            if (typeof globalThis[key] === 'undefined') {
                try { globalThis[key] = window[key]; } catch(e) {}
            }
        })
    """,
    )

actual fun ensureDomAvailable() {
    if (isDocumentDefined()) return
    val dom = createJsdom()
    val window = getWindow(dom)
    copyGlobals(window)
}
