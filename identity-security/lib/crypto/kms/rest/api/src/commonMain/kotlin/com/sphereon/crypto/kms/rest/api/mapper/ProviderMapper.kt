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

package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.kms.rest.api.generated.models.KeyProvider
import com.sphereon.crypto.kms.rest.api.generated.models.KeyProviderOwnership
import com.sphereon.crypto.kms.rest.api.generated.models.KeyProviderType
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeyProvidersResponse

/**
 * What the management plane knows about a provider that the KMS engine itself does not.
 *
 * A resource-backed provider has no directly constructible engine type: the engine only knows it
 * runs under a permit-bound lease. Its real technology, human-readable name, and owner live in the
 * resource record, so a caller that can resolve the record supplies them here. Absent metadata
 * means the provider is engine-native and describes itself.
 */
data class KeyProviderPresentation(
    val type: KeyProviderType,
    val displayName: String?,
    val ownership: KeyProviderOwnership,
    val sharedFromPlatform: Boolean,
    val isDefault: Boolean,
)

/**
 * Engine-native providers do not have a management-plane presentation record. They still need
 * an operator-facing label on the runtime inventory, so derive a stable label from the provider
 * type instead of serializing a missing display name.
 */
internal fun fallbackProviderDisplayName(providerType: String, providerId: String): String =
    when (providerType.trim().lowercase().replace('-', '_')) {
        "software" -> "Software KMS"
        "aws_kms" -> "AWS KMS"
        "azure_keyvault", "azure_key_vault" -> "Azure Key Vault KMS"
        else -> providerId.trim().takeIf { it.isNotEmpty() } ?: "KMS Provider"
    }

/**
 * The published technology for an engine provider type, or null when the engine type is not one the
 * API describes. Engine spellings differ in separators and case, so they are normalised first.
 */
fun keyProviderTypeOf(providerType: String): KeyProviderType? =
    when (providerType.trim().lowercase().replace('-', '_')) {
        "software" -> KeyProviderType.SOFTWARE
        "aws_kms" -> KeyProviderType.AWS_KMS
        "azure_keyvault", "azure_key_vault" -> KeyProviderType.AZURE_KEYVAULT
        else -> null
    }

/**
 * Renders a provider for an inventory. A provider whose technology neither a presentation record
 * nor its engine type can name is left out, so one unrecognised provider cannot fail the listing.
 */
fun KmsProvider.toRestOrNull(presentation: KeyProviderPresentation? = null): KeyProvider? {
    val type = presentation?.type ?: keyProviderTypeOf(this.kmsProviderType) ?: return null
    return toRest(presentation, type)
}

fun KmsProvider.toRest(presentation: KeyProviderPresentation? = null): KeyProvider =
    toRest(
        presentation,
        presentation?.type
            ?: keyProviderTypeOf(this.kmsProviderType)
            ?: throw IllegalStateException("Provider '${this.id}' has no published provider type"),
    )

private fun KmsProvider.toRest(presentation: KeyProviderPresentation?, type: KeyProviderType): KeyProvider =
    KeyProvider(
        providerId = this.id,
        type = type,
        displayName = presentation?.displayName ?: fallbackProviderDisplayName(this.kmsProviderType, this.id),
        ownership = presentation?.ownership,
        sharedFromPlatform = presentation?.sharedFromPlatform,
        isDefault = presentation?.isDefault,
    )

fun Array<KeyProvider>.toRestResponse(): ListKeyProvidersResponse =
    ListKeyProvidersResponse(
        providers = this,
    )
