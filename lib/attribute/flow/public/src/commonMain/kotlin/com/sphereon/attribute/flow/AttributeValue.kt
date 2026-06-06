/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.data.store.blob.BlobInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The typed value carried by an [AttributeRecord].
 *
 * Most attributes are plain JSON data ([AttributeData]); some carry a reference to a stored blob
 * ([AttributeBlob]), a cryptographic key ([AttributeKey]), or supporting verification evidence
 * ([AttributeEvidence]). Sealed so consumer `when` expressions stay exhaustive; variants are
 * top-level data classes (mirroring [AttributeOrigin]) so kotlinx-serialization and JS export
 * stay well-behaved.
 */
@JsExportCompat
@Serializable
sealed interface AttributeValue

/**
 * A plain data value (string, number, boolean, object, array). Becomes a credential "claim"
 * only once a downstream assembler maps it into a credential.
 */
@Serializable
@SerialName("data")
data class AttributeData(
    val value: JsonElement,
) : AttributeValue

/**
 * A reference to a stored blob, plus an optional integrity hash for credential embedding.
 * Wraps the existing [BlobInfo] from `lib-data-store-blob-public`.
 */
@Serializable
@SerialName("blob")
data class AttributeBlob(
    val blobInfo: BlobInfo,
    val hash: String? = null,
    val hashAlgorithm: String? = null,
) : AttributeValue

/**
 * A cryptographic key, with usage semantics. Wraps the existing [KeyInfo] from
 * `lib-crypto-core-public` (which is itself serializable and may carry just a reference or the
 * full key material).
 */
@Serializable
@SerialName("key")
data class AttributeKey(
    val keyInfo: KeyInfo<KeyType>,
    val usage: KeyUsage,
) : AttributeValue

/**
 * A reference to supporting evidence (document scan, IDV result, audit log, ...). Attached to
 * credentials as a hash/URI and retained post-issuance for compliance. May reference a blob.
 */
@Serializable
@SerialName("evidence")
data class AttributeEvidence(
    val evidenceId: String,
    val evidenceType: String,
    val hash: String? = null,
    val hashAlgorithm: String? = null,
    val blobInfo: BlobInfo? = null,
    val uri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) : AttributeValue

/** How a key carried by an [AttributeKey] is used. */
@JsExportCompat
@Serializable
enum class KeyUsage {
    /** Key used to sign the credential. */
    ISSUER_SIGNING,

    /** Key the credential is bound to (the `cnf` claim). */
    HOLDER_BINDING,

    /** Key used to encrypt the credential. */
    ENCRYPTION,
}
