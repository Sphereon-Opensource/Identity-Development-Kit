/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JWS protected header for AS-minted tokens. Always includes [typ] and the
 * SigningKeyStore `kid` when the sign-time identifier carries one, so the
 * header matches JWKS even if PKCS12/KMS resolution drops the wire-visible kid.
 */
internal fun asSigningProtectedHeader(
    typ: String,
    signingIdentifier: ManagedIdentifierOptsOrResult,
): JsonObject =
    buildJsonObject {
        put("typ", typ)
        signingIdentifier.wireVisibleKid()?.let { put("kid", it) }
    }

internal fun ManagedIdentifierOptsOrResult.wireVisibleKid(): String? =
    when (this) {
        is ManagedOptsKeyInfo -> identifier.kid
        is ManagedOptsKid -> identifier
        else -> lookup.kid
    }?.takeIf { it.isNotBlank() }
