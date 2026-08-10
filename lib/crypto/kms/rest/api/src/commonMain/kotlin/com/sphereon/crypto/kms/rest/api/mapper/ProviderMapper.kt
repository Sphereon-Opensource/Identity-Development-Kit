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

fun KmsProvider.toRest(presentation: KeyProviderPresentation? = null): KeyProvider =
    KeyProvider(
        providerId = this.id,
        type = presentation?.type ?: KeyProviderType.valueOf(this.kmsProviderType.uppercase()),
        displayName = presentation?.displayName,
        ownership = presentation?.ownership,
        sharedFromPlatform = presentation?.sharedFromPlatform,
        isDefault = presentation?.isDefault,
    )

fun Array<KeyProvider>.toRestResponse(): ListKeyProvidersResponse =
    ListKeyProvidersResponse(
        providers = this,
    )
