package com.sphereon.core.compat.xml.c14n

// Top-level js() expressions for wasmJs interop (must be top-level function bodies)
private fun isDocumentDefined(): Boolean =
    js("typeof globalThis.document !== 'undefined'")

private fun createJsdom(): JsAny =
    js("new (require('jsdom').JSDOM)('<!DOCTYPE html><html><body></body></html>')")

private fun getWindow(dom: JsAny): JsAny =
    js("dom.window")

private fun copyGlobals(window: JsAny): Unit =
    js("""
        Object.getOwnPropertyNames(window).forEach(function(key) {
            if (typeof globalThis[key] === 'undefined') {
                try { globalThis[key] = window[key]; } catch(e) {}
            }
        })
    """)

actual fun ensureDomAvailable() {
    if (isDocumentDefined()) return
    val dom = createJsdom()
    val window = getWindow(dom)
    copyGlobals(window)
}
