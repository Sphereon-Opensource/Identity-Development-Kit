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

@JsModule("node:fs")
external val fs: dynamic

@JsModule("node:path")
external val nodePath: dynamic

@JsModule("node:process")
external val process: dynamic

actual fun readTestResource(path: String): String {
    // Try PROJECT_DIR env var first (set by Gradle), then fall back to cwd-based path
    val projectDir = process.env.PROJECT_DIR as? String
    val basePath =
        if (projectDir != null) {
            nodePath.resolve(projectDir, "src/commonTest/resources") as String
        } else {
            nodePath.resolve(process.cwd(), TestResourceConfig.RESOURCE_PATH) as String
        }
    val fullPath = nodePath.resolve(basePath, path) as String
    require(fs.existsSync(fullPath) as Boolean) { "Test resource not found: $fullPath" }
    return fs.readFileSync(fullPath, "utf8") as String
}
