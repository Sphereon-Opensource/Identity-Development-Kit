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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class DesignBinding
    @JvmOverloads
    constructor(
        val vct: String? = null,
        val vctHostingMode: VctHostingMode = VctHostingMode.NONE,
        val credentialConfigurationId: String? = null,
        val schemaId: String? = null,
        val docType: String? = null,
        val vcType: String? = null,
        val vcContext: String? = null,
        val issuerId: String? = null,
        val issuerDid: String? = null,
        val issuerUri: String? = null,
        val verifierClientId: String? = null,
        val ocaSaid: String? = null,
    )

@JsExportCompat
@Serializable
enum class DesignBindingKey {
    VCT,
    VCT_HOSTING_MODE,
    CREDENTIAL_CONFIGURATION_ID,
    SCHEMA_ID,
    DOC_TYPE,
    VC_TYPE,
    VC_CONTEXT,
    ISSUER_ID,
    ISSUER_DID,
    ISSUER_URI,
    VERIFIER_CLIENT_ID,
    OCA_SAID,
}
