/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.http.callback

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Signature scheme applied to outbound callback requests. A callback that names a secret reference
 * is signed with this scheme; a callback without one is sent unsigned.
 *
 * HMAC-SHA256 uses `X-Sphereon-Callback-Signature: sha256=<lowercase hex HMAC-SHA256>` over the
 * exact UTF-8 request body bytes sent on the wire. A secret reference without an explicit
 * algorithm selects HMAC-SHA256.
 */
@JsExportCompat
@Serializable
enum class CallbackSigningAlgorithm {
    HMAC_SHA256,
}

/** Stable wire names for signed Sphereon callbacks. */
object CallbackSigning {
    const val SIGNATURE_HEADER = "X-Sphereon-Callback-Signature"
    const val HMAC_SHA256_PREFIX = "sha256="
}
