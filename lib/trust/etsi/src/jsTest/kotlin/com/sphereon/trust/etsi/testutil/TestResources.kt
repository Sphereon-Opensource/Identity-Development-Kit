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
    val basePath = if (projectDir != null) {
        nodePath.resolve(projectDir, "src/commonTest/resources") as String
    } else {
        nodePath.resolve(process.cwd(), TestResourceConfig.RESOURCE_PATH) as String
    }
    val fullPath = nodePath.resolve(basePath, path) as String
    require(fs.existsSync(fullPath) as Boolean) { "Test resource not found: $fullPath" }
    return fs.readFileSync(fullPath, "utf8") as String
}
