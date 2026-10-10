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
 * Native targets have no WebCrypto. [SoftwareWscd] always falls back to its KMS-backed
 * provisioning/signing path here, exactly as on the JVM.
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
    // No browser-custody registry exists on native targets; nothing to forget.
}
