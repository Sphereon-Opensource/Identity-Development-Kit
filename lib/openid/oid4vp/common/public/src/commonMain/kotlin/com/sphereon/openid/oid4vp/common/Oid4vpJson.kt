/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.common

import kotlinx.serialization.json.Json

/** JSON codecs for the extensible OpenID4VP wire format. */
object Oid4vpJson {
    /**
     * OpenID4VP metadata parsers must ignore unrecognized parameters. This keeps the typed
     * model standards-focused while allowing independently deployed Verifiers to add metadata.
     */
    val wire: Json = Json { ignoreUnknownKeys = true }

    val wireNoDefaults: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
}
