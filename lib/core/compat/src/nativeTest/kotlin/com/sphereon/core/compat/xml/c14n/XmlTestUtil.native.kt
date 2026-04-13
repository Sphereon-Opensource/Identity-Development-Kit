package com.sphereon.core.compat.xml.c14n

actual fun ensureDomAvailable() {
    // No-op: Native (iOS/Linux) uses xmlutil's pure Kotlin DOM implementation
}
