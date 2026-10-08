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

private fun readFileSync(path: JsString): JsString = js("require('node:fs').readFileSync(path, 'utf8')")

private fun existsSync(path: JsString): Boolean = js("require('node:fs').existsSync(path)")

private fun resolvePath(
    base: JsString,
    relative: JsString,
): JsString = js("require('node:path').resolve(base, relative)")

private fun getCwd(): JsString = js("require('node:process').cwd()")

private fun getEnvVar(name: JsString): JsAny? = js("process.env[name]")

actual fun readTestResource(path: String): String {
    val projectDir = getEnvVar("PROJECT_DIR".toJsString())
    val basePath =
        if (projectDir != null) {
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
    require(existsSync(fullPath)) { "Test resource not found: $fullPath" }
    return readFileSync(fullPath).toString()
}
