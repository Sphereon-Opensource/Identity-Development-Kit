package com.sphereon.crypto.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createTestHttpClient(): HttpClient = HttpClient(CIO)

actual fun isBrowserEnv(): Boolean = false
