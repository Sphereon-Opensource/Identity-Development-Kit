package com.sphereon.ktor.http.client.server

actual interface TestServer {
    actual fun actualPort(): Int
    actual fun stop()
}

actual fun startServer(opts: ServerOpts): TestServer {
    println("Not yet implemented")

    return object : TestServer {
        override fun actualPort(): Int = 18080
        override fun stop() {
        }
    }
}
