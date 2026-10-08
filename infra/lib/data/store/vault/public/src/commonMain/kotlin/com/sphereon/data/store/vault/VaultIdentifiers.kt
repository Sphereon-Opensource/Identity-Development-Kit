/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.data.store.vault

import kotlinx.serialization.Serializable

private val VAULT_OPAQUE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

private fun requireOpaqueId(
    label: String,
    value: String,
) {
    require(VAULT_OPAQUE_ID.matches(value)) {
        "$label must be 1..128 characters and contain only letters, digits, '.', '_', ':' or '-'"
    }
}

/** Stable identity of a vault. It is never derived from a tenant or storage path. */
@Serializable
data class VaultId(
    val value: String,
) {
    init {
        requireOpaqueId("VaultId", value)
    }
}

/** Stable identity of a file or folder. Rename and move never change this value. */
@Serializable
data class VaultObjectId(
    val value: String,
) {
    init {
        requireOpaqueId("VaultObjectId", value)
    }
}

/** Stable identity of one immutable object version. */
@Serializable
data class VaultVersionId(
    val value: String,
) {
    init {
        requireOpaqueId("VaultVersionId", value)
    }
}

/** Provider-neutral optimistic-concurrency token. */
@Serializable
data class VaultRevision(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "VaultRevision must not be blank" }
        require(value == value.trim()) { "VaultRevision must not contain surrounding whitespace" }
        require(value.length <= 512) { "VaultRevision must not exceed 512 characters" }
        require(value.none { it.isISOControl() }) { "VaultRevision must not contain control characters" }
    }
}

@Serializable
data class VaultOperationId(
    val value: String,
) {
    init {
        requireOpaqueId("VaultOperationId", value)
    }
}

@Serializable
data class VaultIdempotencyKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "VaultIdempotencyKey must not be blank" }
        require(value.length <= 256) { "VaultIdempotencyKey must not exceed 256 characters" }
        require(value.none { it.isISOControl() }) { "VaultIdempotencyKey must not contain control characters" }
    }
}

/**
 * Canonical absolute vault path.
 *
 * Paths are mutable names, never object identities. Non-canonical input is rejected instead of
 * silently normalized so authorization and signed-manifest checks see exactly the same value.
 */
@Serializable
data class VaultPath(
    val value: String,
) {
    init {
        require(value.length <= MAX_PATH_LENGTH) { "VaultPath must not exceed $MAX_PATH_LENGTH characters" }
        require(value.startsWith('/')) { "VaultPath must be absolute and start with '/'" }
        require(value == ROOT_VALUE || !value.endsWith('/')) { "VaultPath must not end with '/'" }
        require('\\' !in value) { "VaultPath must use '/' separators" }
        require(value.none { it.isISOControl() }) { "VaultPath must not contain control characters" }

        if (value != ROOT_VALUE) {
            value.drop(1).split('/').forEach { segment ->
                require(segment.isNotEmpty()) { "VaultPath must not contain empty segments" }
                require(segment != "." && segment != "..") { "VaultPath must not contain '.' or '..' segments" }
                require(segment.length <= MAX_SEGMENT_LENGTH) {
                    "VaultPath segment must not exceed $MAX_SEGMENT_LENGTH characters"
                }
            }
        }
    }

    val isRoot: Boolean
        get() = value == ROOT_VALUE

    val name: String
        get() = if (isRoot) ROOT_VALUE else value.substringAfterLast('/')

    fun parent(): VaultPath? =
        when {
            isRoot -> null
            value.lastIndexOf('/') == 0 -> ROOT
            else -> VaultPath(value.substringBeforeLast('/'))
        }

    fun child(name: String): VaultPath {
        require(name.isNotEmpty()) { "Child name must not be empty" }
        require('/' !in name && '\\' !in name) { "Child name must be one path segment" }
        return VaultPath(if (isRoot) "/$name" else "$value/$name")
    }

    companion object {
        private const val ROOT_VALUE = "/"
        private const val MAX_PATH_LENGTH = 4096
        private const val MAX_SEGMENT_LENGTH = 255

        val ROOT = VaultPath(ROOT_VALUE)
    }
}
