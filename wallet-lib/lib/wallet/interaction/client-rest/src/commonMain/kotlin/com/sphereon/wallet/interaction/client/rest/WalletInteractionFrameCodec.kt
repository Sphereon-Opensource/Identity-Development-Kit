/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.client.rest

import com.sphereon.wallet.interaction.WalletInteractionClientFrame
import com.sphereon.wallet.interaction.WalletInteractionServerFrame
import kotlinx.serialization.json.Json

class WalletInteractionFrameCodec(
    private val json: Json = defaultWalletInteractionJson,
) {
    fun encodeClient(frame: WalletInteractionClientFrame): String = json.encodeToString(WalletInteractionClientFrame.serializer(), frame)

    fun decodeClient(frame: String): WalletInteractionClientFrame = json.decodeFromString(WalletInteractionClientFrame.serializer(), frame)

    fun encodeServer(frame: WalletInteractionServerFrame): String = json.encodeToString(WalletInteractionServerFrame.serializer(), frame)

    fun decodeServer(frame: String): WalletInteractionServerFrame = json.decodeFromString(WalletInteractionServerFrame.serializer(), frame)
}
