/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.resolver

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for resolving trust lists from various sources (HTTP, file, IPFS, etc.).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustListResolver", exact = true)
@JsExportCompat
interface TrustListResolver {
    fun getId(): String

    suspend fun resolve(
        uri: String,
        options: ResolutionOptions = ResolutionOptions(),
    ): TrustListData

    fun supports(uri: String): Boolean
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolutionOptions", exact = true)
@JsExportCompat
data class ResolutionOptions(
    val timeoutMs: Long = 30000,
    val useCache: Boolean = true,
    val maxCacheAgeMs: Long = 3600000,
    val verifySignature: Boolean = true,
    @JsExportIgnoreCompat
    val customOptions: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustListData", exact = true)
@JsExportCompat
data class TrustListData(
    val data: ByteArray,
    val sourceUri: String,
    val contentType: String? = null,
    val fromCache: Boolean = false,
    val retrievedAt: Long,
    val cacheToken: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as TrustListData
        if (!data.contentEquals(other.data)) {
            return false
        }
        if (sourceUri != other.sourceUri) {
            return false
        }
        if (contentType != other.contentType) {
            return false
        }
        if (fromCache != other.fromCache) {
            return false
        }
        if (retrievedAt != other.retrievedAt) {
            return false
        }
        if (cacheToken != other.cacheToken) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sourceUri.hashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + fromCache.hashCode()
        result = 31 * result + retrievedAt.hashCode()
        result = 31 * result + (cacheToken?.hashCode() ?: 0)
        return result
    }
}

class TrustListResolutionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
