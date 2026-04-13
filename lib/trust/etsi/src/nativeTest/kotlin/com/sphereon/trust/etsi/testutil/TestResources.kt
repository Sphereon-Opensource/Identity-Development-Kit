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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sphereon.trust.etsi.testutil

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind

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
            buffer[size.toInt()] = 0.toByte()
            buffer.toKString()
        }
    } finally {
        fclose(file)
    }
}
