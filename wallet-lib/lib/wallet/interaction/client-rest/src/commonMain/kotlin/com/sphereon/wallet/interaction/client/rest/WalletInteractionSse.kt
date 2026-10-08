/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.client.rest

import com.sphereon.wallet.interaction.WalletInteractionApiConstants
import com.sphereon.wallet.interaction.WalletInteractionStateEvent
import kotlinx.serialization.json.Json

data class ParsedWalletInteractionSse(
    val id: String?,
    val event: String?,
    val data: WalletInteractionStateEvent,
)

class WalletInteractionSseParser(
    private val json: Json = defaultWalletInteractionJson,
) {
    fun parseMany(stream: String): List<ParsedWalletInteractionSse> =
        stream
            .replace("\r\n", "\n")
            .split(Regex("\n\\s*\n"))
            .filter { it.isNotBlank() }
            .map(::parse)

    fun parse(frame: String): ParsedWalletInteractionSse {
        var id: String? = null
        var event: String? = null
        val data = StringBuilder()

        frame.lineSequence().forEach { line ->
            when {
                line.startsWith("id:") -> {
                    id = line.removePrefix("id:").trim()
                }

                line.startsWith("event:") -> {
                    event = line.removePrefix("event:").trim()
                }

                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trim())
                }
            }
        }

        require(event == null || event == WalletInteractionApiConstants.Sse.EVENT_STATE) {
            "Unsupported wallet interaction SSE event: $event"
        }

        return ParsedWalletInteractionSse(
            id = id,
            event = event,
            data = json.decodeFromString(WalletInteractionStateEvent.serializer(), data.toString()),
        )
    }
}
