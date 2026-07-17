/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputRef
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletSecurityGrant
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Private-store backed, atomic, purpose-bound, one-use sensitive input authority. */
class StoreBackedWalletInteractionSensitiveInputAuthority(
    private val store: WalletInteractionPrivateSessionStore,
) : WalletInteractionSensitiveInputAuthority {
    private val mutex = Mutex()

    override suspend fun register(
        sessionId: WalletInteractionSessionId,
        purpose: WalletInteractionSensitiveInputPurpose,
        value: String,
    ): WalletInteractionSensitiveInputRef {
        require(value.isNotBlank()) { "wallet_interaction_sensitive_input_blank" }
        return mutex.withLock {
            val existing = store.get(sessionId, NAMESPACE)?.values.orEmpty()
            var ref: WalletInteractionSensitiveInputRef
            do {
                ref = WalletInteractionSensitiveInputRef(CryptographyRandom.nextBytes(32).toHex())
            } while (ref.value in existing)
            store.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = NAMESPACE,
                    values = existing + (ref.value to encode(purpose, value)),
                ),
            )
            ref
        }
    }

    override suspend fun consume(
        sessionId: WalletInteractionSessionId,
        purpose: WalletInteractionSensitiveInputPurpose,
        ref: WalletInteractionSensitiveInputRef,
    ): String? =
        mutex.withLock {
            val existing = store.get(sessionId, NAMESPACE)?.values.orEmpty()
            val encoded = existing[ref.value] ?: return@withLock null
            val expectedPrefix = "${purpose.name}:"
            if (!encoded.startsWith(expectedPrefix)) return@withLock null
            val remaining = existing - ref.value
            if (remaining.isEmpty()) {
                store.remove(sessionId, NAMESPACE)
            } else {
                store.put(sessionId, WalletInteractionPrivateSessionData(NAMESPACE, remaining))
            }
            encoded.removePrefix(expectedPrefix)
        }

    override suspend fun clear(sessionId: WalletInteractionSessionId) {
        mutex.withLock { store.remove(sessionId, NAMESPACE) }
    }

    override suspend fun registerSecurityGrant(
        sessionId: WalletInteractionSessionId,
        grant: WalletSecurityGrant,
    ): WalletInteractionSensitiveInputRef =
        register(
            sessionId = sessionId,
            purpose = WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT,
            value = json.encodeToString(WalletSecurityGrant.serializer(), grant),
        )

    override suspend fun consumeSecurityGrant(
        sessionId: WalletInteractionSessionId,
        ref: WalletInteractionSensitiveInputRef,
    ): WalletSecurityGrant? =
        consume(
            sessionId = sessionId,
            purpose = WalletInteractionSensitiveInputPurpose.INTERACTION_SECURITY_GRANT,
            ref = ref,
        )?.let { encoded -> runCatching { json.decodeFromString(WalletSecurityGrant.serializer(), encoded) }.getOrNull() }

    private fun encode(purpose: WalletInteractionSensitiveInputPurpose, value: String): String = "${purpose.name}:$value"

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }

    private companion object {
        const val NAMESPACE = "wallet.interaction.sensitive-input"
        val json = Json { encodeDefaults = true }
    }
}
