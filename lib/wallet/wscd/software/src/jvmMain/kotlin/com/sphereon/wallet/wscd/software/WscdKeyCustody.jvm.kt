/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.crypto.core.generic.SignatureAlgorithm

/**
 * The JVM has no WebCrypto. [SoftwareWscd] always falls back to its existing KMS-backed
 * provisioning/signing path on this platform, so this seam is a permanent no-op here: JVM
 * behavior is unchanged (the jvmTest suite in this module pins the KMS-backed path end to end).
 */
internal actual suspend fun tryGenerateBrowserWscdKeyPair(
    alias: String,
    algorithm: SignatureAlgorithm,
): BrowserWscdKeyPair? = null

internal actual suspend fun tryBrowserWscdSign(
    alias: String,
    digest: ByteArray,
): ByteArray? = null

internal actual fun forgetBrowserWscdKey(alias: String) {
    // No browser-custody registry exists on the JVM; nothing to forget.
}
