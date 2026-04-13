package com.sphereon.trust.etsi.testutil

private fun readFileSync(path: JsString): JsString =
    js("require('node:fs').readFileSync(path, 'utf8')")

private fun existsSync(path: JsString): Boolean =
    js("require('node:fs').existsSync(path)")

private fun resolvePath(base: JsString, relative: JsString): JsString =
    js("require('node:path').resolve(base, relative)")

private fun getCwd(): JsString =
    js("require('node:process').cwd()")

private fun getEnvVar(name: JsString): JsAny? =
    js("process.env[name]")

actual fun readTestResource(path: String): String {
    val projectDir = getEnvVar("PROJECT_DIR".toJsString())
    val basePath = if (projectDir != null) {
        resolvePath(projectDir.unsafeCast<JsString>(), "src/commonTest/resources".toJsString())
    } else {
        // wasmJs cwd is the wasm package dir. Navigate up to find the module root
        // by looking for the specific resource directory.
        var dir = getCwd()
        var found = false
        for (i in 0 until 15) {
            val candidate = resolvePath(dir, "lib/trust/etsi/src/commonTest/resources".toJsString())
            if (existsSync(candidate)) {
                dir = candidate
                found = true
                break
            }
            dir = resolvePath(dir, "..".toJsString())
        }
        if (found) dir else resolvePath(getCwd(), TestResourceConfig.RESOURCE_PATH.toJsString())
    }
    val fullPath = resolvePath(basePath, path.toJsString())
    require(existsSync(fullPath)) { "Test resource not found: ${fullPath.toString()}" }
    return readFileSync(fullPath).toString()
}
