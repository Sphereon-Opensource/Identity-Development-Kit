/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.impl.validation

import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength

val issuerMetadataValidator =
    Validation<CredentialIssuerMetadata> {
        CredentialIssuerMetadata::credentialIssuer {
            minLength(1) hint "credential_issuer is required"
        }
        CredentialIssuerMetadata::credentialEndpoint {
            minLength(1) hint "credential_endpoint is required"
        }
        CredentialIssuerMetadata::credentialConfigurationsSupported {
            constrain("credential_configurations_supported must not be empty") { it.isNotEmpty() }
        }
        CredentialIssuerMetadata::credentialResponseEncryption ifPresent {
            MetadataCredentialResponseEncryption::encValuesSupported {
                constrain("credential_response_encryption.enc_values_supported must not be empty") { it.isNotEmpty() }
            }
        }
        CredentialIssuerMetadata::batchCredentialIssuance ifPresent {
            BatchCredentialIssuance::batchSize {
                constrain("batch_credential_issuance.batch_size must be > 0") { it > 0 }
            }
        }
        CredentialIssuerMetadata::preferredKeyStorageStatusPeriod ifPresent {
            constrain("preferred_key_storage_status_period must be > 0") { it > 0 }
        }
    }
