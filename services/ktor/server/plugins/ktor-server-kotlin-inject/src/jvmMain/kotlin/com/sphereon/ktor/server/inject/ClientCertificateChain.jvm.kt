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

package com.sphereon.ktor.server.inject

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey

/**
 * Ktor [AttributeKey] under which the universal HTTP adapter looks up a pre-extracted TLS
 * client certificate chain (DER, leaf-first). Test fixtures and engine-aware plugins set
 * this attribute on each request that carries a peer certificate; commonMain code then
 * reads it through [ApplicationCall.extractClientCertificateChain] without depending on any
 * Ktor engine module.
 *
 * Why an attribute, not a reflective probe of the engine's TLS surface: Ktor's mTLS APIs
 * vary by engine (Netty's `ClientAuth`, CIO's manual SSL handshake, etc.) and reflective
 * access leaks engine internals. An attribute keeps the contract one-way and stable.
 */
val ClientCertificateChainAttributeKey: AttributeKey<List<ByteArray>> =
    AttributeKey("com.sphereon.ktor.server.inject.ClientCertificateChain")

internal actual fun ApplicationCall.extractClientCertificateChain(): List<ByteArray>? =
    if (attributes.contains(ClientCertificateChainAttributeKey)) {
        attributes[ClientCertificateChainAttributeKey].takeIf { it.isNotEmpty() }
    } else {
        null
    }
