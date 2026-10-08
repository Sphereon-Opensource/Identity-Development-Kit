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
 *
 */

package com.sphereon.crypto.core.sign.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import kotlin.jvm.JvmOverloads

/**
 * Signature parameters for one-step JWS/JWT signing via [com.sphereon.crypto.core.sign.SignatureService.sign].
 *
 * When passed to `sign()`, the service will:
 * 1. Prepare a JWS using [issuer] for header/payload enrichment
 * 2. Sign the JWS signing input with the provided keyInfo
 * 3. Assemble a compact JWT and return it as [com.sphereon.crypto.core.sign.model.SignOutput.signedData]
 *
 * @property issuer Managed identifier for JWS header enrichment (kid/x5c/jwk) and payload claims (iss).
 * @property mode How to represent the identifier in the JWS header.
 * @property opts JWS creation options (header overrides, issuer payload update control).
 * @property payload Optional explicit JWS payload (JsonObject or String). If null, [SignInput.input] bytes are used.
 */
@JsExportCompat
data class
JwsSignatureParameters
    @JvmOverloads
    constructor(
        override val signatureLevel: SignatureLevel = SignatureLevel.JWS,
        override val signaturePackaging: SignaturePackaging = SignaturePackaging.ENVELOPING,
        override val digestAlgorithm: DigestAlg? = null,
        val issuer: ManagedIdentifierOptsOrResult,
        val mode: JwsIdentifierMode = JwsIdentifierMode.AUTO,
        val opts: CreateJwsOpts = CreateJwsOpts(),
        val payload: Any? = null,
    ) : SignatureParameters
