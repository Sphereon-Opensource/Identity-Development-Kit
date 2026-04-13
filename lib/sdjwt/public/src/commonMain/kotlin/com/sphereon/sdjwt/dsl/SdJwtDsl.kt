/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt.dsl

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * DSL marker annotation to prevent scope pollution in nested SD-JWT builders.
 *
 * This annotation ensures that methods from outer scopes are not accidentally
 * called in nested DSL blocks. For example, when building nested SD objects,
 * you won't accidentally call methods from the outer builder.
 *
 * Example:
 * ```kotlin
 * sdJwtPayload {
 *     iss("https://issuer.example.com")
 *     objSd("address") {
 *         // Methods from outer scope are not accessible here
 *         claim("street", "123 Main St")
 *         claimSd("zip", "12345")
 *     }
 * }
 * ```
 */
@DslMarker
annotation class SdJwtDslMarker

/**
 * Creates an SD-JWT payload using a DSL builder.
 *
 * This is the main entry point for building SD-JWT payloads. It provides a type-safe
 * DSL for constructing JWT payloads with selective disclosure capabilities.
 *
 * An SD-JWT is fundamentally a JWT with selective disclosure capabilities. The DSL
 * reflects this relationship - standard JWT claims work identically, while SD-specific
 * methods (`claimSd`, `subSd`, etc.) mark claims for selective disclosure.
 *
 * Example:
 * ```kotlin
 * val payload = sdJwtPayload {
 *     iss("https://issuer.example.com")
 *     subSd("user-123")                     // Subject as SD
 *     claimSd("email", "user@example.com")  // Custom claim as SD
 *     claim("verified", true)               // Non-SD claim
 *     minimumDigests(3)
 * }
 *
 * // Access the result
 * payload.claims       // JsonObject - no metadata pollution
 * payload.sdClaims     // Set<String>: ["sub", "email"]
 * payload.minimumDigests // Int?: 3
 * ```
 *
 * @param builderAction The DSL block to build the SD-JWT payload
 * @return The constructed [SdJwtPayload] containing claims and SD metadata
 */
@OptIn(ExperimentalContracts::class)
inline fun sdJwtPayload(builderAction: SdJwtPayloadBuilder.() -> Unit): SdJwtPayload {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }
    val builder = SdJwtPayloadBuilder()
    builder.builderAction()
    return builder.build()
}
