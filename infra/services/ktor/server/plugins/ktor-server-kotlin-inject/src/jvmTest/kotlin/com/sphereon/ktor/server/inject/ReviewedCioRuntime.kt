package com.sphereon.ktor.server.inject

import io.ktor.server.cio.CIO
import org.junit.Assume.assumeTrue

/**
 * Closing the connection after a framing rejection is the reviewed Sphereon CIO's behavior: stock Ktor CIO keeps
 * a keep-alive connection open despite the response's `Connection: close`. The build selects the reviewed CIO only
 * when `gradle/oidfed-cio-inputs.json` is present; the public build runs on stock CIO.
 */
internal fun assumeReviewedCioRuntime() {
    val location = CIO::class.java.protectionDomain?.codeSource?.location?.toString().orEmpty()
    assumeTrue("Runs on the reviewed Sphereon CIO only; this runtime is $location", "-sphereon-cio-" in location)
}
