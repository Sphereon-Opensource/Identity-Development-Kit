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

package com.sphereon.data.store.credential.design.model

import kotlinx.serialization.Serializable

@Serializable
enum class IssuerMetadataPreference { OID4VCI_FIRST, SD_JWT_FIRST, MERGE, LOCAL_ONLY }

@Serializable
enum class CredentialMetadataPreference { SD_JWT_FIRST, OID4VCI_FIRST, MERGE, LOCAL_ONLY }

@Serializable
data class DesignResolutionPolicy(
    val schemaHintsEnabled: Boolean = true,
    val jsonLdContextHintsEnabled: Boolean = true,
    val markdownRenderingEnabled: Boolean = false,
    val issuerMetadataPreference: IssuerMetadataPreference = IssuerMetadataPreference.OID4VCI_FIRST,
    val credentialMetadataPreference: CredentialMetadataPreference = CredentialMetadataPreference.SD_JWT_FIRST,
    val preferIntegrityProtectedSources: Boolean = true,
    val allowRemoteTemplateFetch: Boolean = false,
    val allowRemoteAssetFetch: Boolean = false,
)

@Serializable
data class CredentialDesignRefreshConfig(
    val enabled: Boolean = false,
    val defaultTtlSeconds: Long = 86400,
    val rehostRemoteAssets: Boolean = true,
    val maxAssetSizeBytes: Long = 5_000_000,
)

@Serializable
data class CredentialDesignValidationConfig(
    val maxBindingsPerDesign: Int = 16,
    val maxDisplaysPerDesign: Int = 64,
    val maxClaimsPerDesign: Int = 256,
    val maxRenderVariantsPerDesign: Int = 32,
    val maxEntryCodesPerClaim: Int = 1024,
    val failOnUnknownSourceType: Boolean = true,
    val validateOnRead: Boolean = false,
)

@Serializable
data class CredentialDesignModuleConfig(
    val policy: DesignResolutionPolicy = DesignResolutionPolicy(),
    val refresh: CredentialDesignRefreshConfig = CredentialDesignRefreshConfig(),
    val validation: CredentialDesignValidationConfig = CredentialDesignValidationConfig(),
)
