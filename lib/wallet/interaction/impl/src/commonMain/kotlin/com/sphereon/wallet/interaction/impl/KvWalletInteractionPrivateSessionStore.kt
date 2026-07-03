/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * KV-backed private protocol/session store.
 *
 * This store is intentionally separate from the replayable interaction state
 * store because values may include raw launch material, protocol transaction
 * identifiers, access tokens, or backend-only coordination data.
 */
class KvWalletInteractionPrivateSessionStore(
    private val kv: KvStore,
    private val ttl: Duration = Duration.INFINITE,
    json: Json = walletInteractionStoreJson,
) : WalletInteractionPrivateSessionStore {
    private val dataNamespace =
        KvNamespace(
            name = PRIVATE_SESSION_NAMESPACE,
            codec = KotlinxSerializationJsonKvCodec(json, WalletInteractionPrivateSessionData.serializer()),
        )
    private val indexNamespace =
        KvNamespace(
            name = PRIVATE_SESSION_INDEX_NAMESPACE,
            codec = KotlinxSerializationJsonKvCodec(json, KvWalletInteractionPrivateSessionIndex.serializer()),
        )

    override suspend fun put(
        sessionId: WalletInteractionSessionId,
        data: WalletInteractionPrivateSessionData,
    ) {
        kv.put(dataNamespace, privateSessionKey(sessionId, data.namespace), data, ttl).getOrThrow()
        val current = privateSessionIndex(sessionId)
        if (data.namespace !in current.namespaces) {
            kv.put(indexNamespace, sessionId.value, current.copy(namespaces = current.namespaces + data.namespace), ttl).getOrThrow()
        }
    }

    override suspend fun get(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ): WalletInteractionPrivateSessionData? {
        require(namespace.isNotBlank()) { "wallet_interaction_private_session_namespace_blank" }
        return kv.get(dataNamespace, privateSessionKey(sessionId, namespace)).getOrThrow()
    }

    override suspend fun remove(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ) {
        require(namespace.isNotBlank()) { "wallet_interaction_private_session_namespace_blank" }
        kv.delete(dataNamespace, privateSessionKey(sessionId, namespace)).getOrThrow()
        val remaining = privateSessionIndex(sessionId).namespaces.filterNot { it == namespace }
        if (remaining.isEmpty()) {
            kv.delete(indexNamespace, sessionId.value).getOrThrow()
        } else {
            kv.put(indexNamespace, sessionId.value, KvWalletInteractionPrivateSessionIndex(remaining), ttl).getOrThrow()
        }
    }

    override suspend fun removeSession(sessionId: WalletInteractionSessionId) {
        privateSessionIndex(sessionId)
            .namespaces
            .forEach { namespace -> kv.delete(dataNamespace, privateSessionKey(sessionId, namespace)).getOrThrow() }
        kv.delete(indexNamespace, sessionId.value).getOrThrow()
    }

    private suspend fun privateSessionIndex(sessionId: WalletInteractionSessionId): KvWalletInteractionPrivateSessionIndex =
        kv.get(indexNamespace, sessionId.value).getOrThrow() ?: KvWalletInteractionPrivateSessionIndex()

    private fun privateSessionKey(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ): String = "${sessionId.value.length}:${sessionId.value}${namespace.length}:$namespace"

    private companion object {
        const val PRIVATE_SESSION_NAMESPACE = "wallet-interaction-private-sessions"
        const val PRIVATE_SESSION_INDEX_NAMESPACE = "wallet-interaction-private-session-index"
    }
}

@Serializable
private data class KvWalletInteractionPrivateSessionIndex(
    val namespaces: List<String> = emptyList(),
)
