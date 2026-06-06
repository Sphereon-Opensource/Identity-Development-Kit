/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server.command

import com.sphereon.crypto.core.json.CryptoJsonSupport
import kotlinx.serialization.json.Json

/**
 * Shared `Json` configurations for DID Manager HTTP endpoint commands.
 *
 * Hoisted out of the individual `*EndpointCommands.kt` files so that PATCH/POST decoding
 * behaviour, default-encoding, and the polymorphic-crypto module stay aligned across
 * every endpoint.
 */
internal val endpointJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

/**
 * Variant of [endpointJson] that includes the polymorphic-crypto serializer module.
 *
 * Capability descriptors carry typed crypto values (curves, signature algorithms) that
 * require `CryptoJsonSupport.module` to round-trip. Other endpoints do not need this
 * module and use [endpointJson].
 */
internal val capabilityEndpointJson: Json =
    Json {
        serializersModule = CryptoJsonSupport.module
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
