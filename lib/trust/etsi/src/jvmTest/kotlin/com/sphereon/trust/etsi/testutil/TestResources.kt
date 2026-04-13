package com.sphereon.trust.etsi.testutil

import java.io.File

actual fun readTestResource(path: String): String {
    val file = File("${TestResourceConfig.RESOURCE_PATH}/$path")
    require(file.exists()) { "Test resource not found: ${file.absolutePath}" }
    return file.readText()
}
