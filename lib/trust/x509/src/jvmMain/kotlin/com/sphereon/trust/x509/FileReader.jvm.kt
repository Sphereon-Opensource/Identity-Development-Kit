/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

import java.io.File

actual fun readFileContent(path: String): String? {
    return try {
        val file = File(path)
        if (file.exists() && file.canRead()) file.readText(Charsets.UTF_8) else null
    } catch (_: Exception) {
        null
    }
}
