package com.sphereon.trust.etsi.testutil

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
    js("""
        Object.getOwnPropertyNames(window).forEach(function(key) {
            if (typeof globalThis[key] === 'undefined') {
                try { globalThis[key] = window[key]; } catch(e) {}
            }
        });
    """)
}
