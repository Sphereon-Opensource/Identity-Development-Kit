package com.sphereon.crypto.core

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual fun createTestHttpClient(): HttpClient = HttpClient(Darwin)

actual fun isBrowserEnv(): Boolean = false
