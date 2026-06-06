/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.wallet

import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A derived, read-only summary of a [WalletDocument] at a point in time.
 *
 * All fields are computed from the document and its credential instances;
 * this type is never stored independently. Use [WalletDocument.metadata] to obtain it.
 */
@Serializable
data class WalletDocumentMetadata(
    val documentId: String,
    val issuer: IdentifierRef,
    val subjects: List<IdentifierRef> = emptyList(),
    val credentialFormat: CredentialFormat,
    val credentialType: String,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val status: CredentialInstanceState,
    val instanceCount: Int,
    val boundInstanceCount: Int,
    val issuerDisplay: List<DisplayProperties> = emptyList(),
    val credentialDisplay: List<DisplayProperties> = emptyList(),
    val updatedAt: Instant,
)
