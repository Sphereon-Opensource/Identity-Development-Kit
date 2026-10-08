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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.sphereon.core.compat.xml.c14n

import kotlin.js.ExperimentalWasmJsInterop

@JsFun("() => typeof globalThis.document !== 'undefined'")
private external fun isDocumentDefined(): Boolean

@JsFun(
    """() => {
    const jsdom = eval("require('jsdom')");
    const dom = new jsdom.JSDOM('<!DOCTYPE html><html><body></body></html>');
    const window = dom.window;
    Object.getOwnPropertyNames(window).forEach(function(key) {
        if (typeof globalThis[key] === 'undefined') {
            try { globalThis[key] = window[key]; } catch(e) {}
        }
    });
}""",
)
private external fun installJsdom()

actual fun ensureDomAvailable() {
    if (isDocumentDefined()) return
    installJsdom()
}
