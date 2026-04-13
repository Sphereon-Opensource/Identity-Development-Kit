/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

/**
 * Reads file content as a string. Returns null if the file doesn't exist or can't be read.
 * Platform-specific implementations handle actual file I/O.
 */
expect fun readFileContent(path: String): String?
