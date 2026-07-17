/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd

import kotlinx.serialization.Serializable

/**
 * Bootstrap-facing custody configuration. Product and runner bootstraps hold THIS,
 * never a KMS reference: the WscdFactory internals own KMS wiring.
 */
@Serializable
sealed interface WscdConfig {
    val profile: WscdProfile

    @Serializable
    data class Software(
        val storageDir: String? = null
    ) : WscdConfig {
        override val profile: WscdProfile get() = WscdProfile.Software
    }

    @Serializable
    data class LocalNative(
        val requireStrongBox: Boolean = false
    ) : WscdConfig {
        override val profile: WscdProfile get() = WscdProfile.LocalNative
    }

    @Serializable
    data class Remote(
        val endpoint: String
    ) : WscdConfig {
        override val profile: WscdProfile get() = WscdProfile.Remote
    }
}
