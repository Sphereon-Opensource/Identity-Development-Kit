/* Copyright 2026 Sphereon International B.V. */

package com.sphereon.wallet.party.local

import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/** Opaque persistence primitive. JVM/Node/iOS bind SQLite-backed Kottage; Android binds SQLite directly. */
interface WalletPartyDocumentStore {
    suspend fun read(scopeKey: String): String?

    suspend fun write(scopeKey: String, document: String)
}

class KvWalletPartyDocumentStore(
    private val store: KvStore,
    json: Json = walletPartyJson,
) : WalletPartyDocumentStore {
    private val namespace =
        KvNamespace(
            name = "wallet-party-directory",
            codec = KotlinxSerializationJsonKvCodec(json, WalletPartyDocument.serializer()),
        )

    override suspend fun read(scopeKey: String): String? = store.get(namespace, scopeKey).getOrThrow()?.payload

    override suspend fun write(scopeKey: String, document: String) {
        store.put(namespace, scopeKey, WalletPartyDocument(document), Duration.INFINITE).getOrThrow()
    }
}

@Serializable
private data class WalletPartyDocument(val payload: String)

internal val walletPartyJson: Json =
    Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }
