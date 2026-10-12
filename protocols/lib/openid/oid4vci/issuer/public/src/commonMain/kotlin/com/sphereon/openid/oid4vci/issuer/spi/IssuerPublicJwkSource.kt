/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.issuer.spi

import kotlinx.serialization.json.JsonObject

/**
 * Optional server-owned source of public issuer signing material.
 *
 * When bound, a missing answer is authoritative: callers must not fall back to a local key store.
 * Implementations must resolve [keyName] against the authenticated tenant and issuer binding and
 * return public JWK members only. The key name is never taken from a credential request.
 */
interface IssuerPublicJwkSource {
    suspend fun resolvePublicJwk(keyName: String): JsonObject?
}
