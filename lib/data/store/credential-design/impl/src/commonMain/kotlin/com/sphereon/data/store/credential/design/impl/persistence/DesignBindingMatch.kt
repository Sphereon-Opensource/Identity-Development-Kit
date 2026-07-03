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

package com.sphereon.data.store.credential.design.impl.persistence

import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey

internal fun DesignBinding.matchesKey(
    key: DesignBindingKey,
    value: String,
): Boolean =
    when (key) {
        DesignBindingKey.VCT -> vct == value
        DesignBindingKey.VCT_HOSTING_MODE -> vctHostingMode.name == value
        DesignBindingKey.CREDENTIAL_CONFIGURATION_ID -> credentialConfigurationId == value
        DesignBindingKey.SCHEMA_ID -> schemaId == value
        DesignBindingKey.DOC_TYPE -> docType == value
        DesignBindingKey.TYPE -> type == value
        DesignBindingKey.CONTEXT -> context == value
        DesignBindingKey.ISSUER_ID -> issuerId == value
        DesignBindingKey.ISSUER_DID -> issuerDid == value
        DesignBindingKey.ISSUER_URI -> issuerUri == value
        DesignBindingKey.VERIFIER_CLIENT_ID -> verifierClientId == value
        DesignBindingKey.OCA_SAID -> ocaSaid == value
        DesignBindingKey.CREDENTIAL_TYPE_FORMAT -> credentialType?.format?.name == value
        DesignBindingKey.CREDENTIAL_DESIGN_ID -> credentialDesignId?.toString() == value
        DesignBindingKey.CREDENTIAL_DESIGN_VERSION -> credentialDesignVersion?.toString() == value
    }

internal fun List<DesignBinding>.anyMatchesKey(
    key: DesignBindingKey,
    value: String,
): Boolean = any { it.matchesKey(key, value) }
