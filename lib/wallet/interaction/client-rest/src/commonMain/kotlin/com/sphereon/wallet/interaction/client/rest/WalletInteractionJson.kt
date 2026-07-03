/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction.client.rest

import kotlinx.serialization.json.Json

val defaultWalletInteractionJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }
