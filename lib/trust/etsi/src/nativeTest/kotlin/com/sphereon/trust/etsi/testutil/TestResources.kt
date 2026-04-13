@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sphereon.trust.etsi.testutil

import kotlinx.cinterop.*
import platform.posix.*

actual fun readTestResource(path: String): String {
    val fullPath = "${TestResourceConfig.RESOURCE_PATH}/$path"
    val file = fopen(fullPath, "r") ?: error("Test resource not found: $fullPath")
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        rewind(file)
        return memScoped {
            val buffer = allocArray<ByteVar>(size + 1)
            fread(buffer, 1u.convert(), size.convert(), file)
            buffer[size] = 0
            buffer.toKString()
        }
    } finally {
        fclose(file)
    }
}
