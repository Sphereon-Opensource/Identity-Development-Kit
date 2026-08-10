/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.did.manager.impl

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk

internal fun publicJwk(): Jwk =
    Jwk(
        kty = JwaKeyType.EC,
        crv = JwaCurve.P_256,
        x = "f83OJ3D2xF4yVPs6k2lE0_C3lq8GG5GpQ1GkGvI0zGY",
        y = "x_FEzRu9m0cN5yZKkH9VqxcWxLb5Y7EFYqmP9FxbnTc",
        kid = "wscd-public-key-1",
        alg = JwaAlgorithm.ES256,
        use = "sig",
    )
