/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.mdoc.data.device

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Codec for the plaintext carried by an ISO/IEC 18013-5 encrypted-document envelope.
 *
 * This is intentionally separate from [DeviceResponseCborCodec]: the encrypted plaintext is a
 * CBOR map of documents, not a DeviceResponse map, and must never be accidentally sent as a
 * clear DeviceResponse.
 */
@JsExportCompat
interface EncryptedDocumentsPlaintextCborCodec {
    fun encode(value: EncryptedDocumentsPlaintext): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<EncryptedDocumentsPlaintext, IdkError>
}
