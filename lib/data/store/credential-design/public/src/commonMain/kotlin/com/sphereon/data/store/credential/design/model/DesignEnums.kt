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

@JsExportCompat
@Serializable
enum class DesignEntityType {
    CREDENTIAL,
    ISSUER,
    VERIFIER,
}

@JsExportCompat
@Serializable
enum class DesignHostingMode {
    LOCAL,
    CACHED_EXTERNAL,
    INFERRED,
}

@JsExportCompat
@Serializable
enum class VctHostingMode {
    HOSTED,
    EXTERNAL,
    NONE,
}

@JsExportCompat
@Serializable
enum class DesignSourceType {
    LOCAL_OVERRIDE,
    LOCAL_SHARED,
    SCHEMA_INFERENCE,
    JSON_LD_CONTEXT,
    CREDENTIAL_TEMPLATE,
    PARTY_STORE,
    SD_JWT_VCT_METADATA,
    SD_JWT_ISSUER_METADATA,
    OID4VCI_CREDENTIAL_CONFIGURATION,
    OID4VCI_ISSUER_METADATA,
    OIDC_DISCOVERY,
    OPENID_FEDERATION,
    OAUTH_CLIENT_REGISTRATION,
    EIDAS_REGISTRY,
    EIDAS_CREDENTIAL_CATALOGUE,
    W3C_VC_RENDER_METHOD,
    OCA_BUNDLE,
    MANUAL_IMPORT,
}

@JsExportCompat
@Serializable
enum class RenderVariantKind {
    SIMPLE_CARD,
    SVG_TEMPLATE,
    W3C_RENDER_METHOD,
    PDF_TEMPLATE,
    EXTERNAL_REFERENCE,
}

@JsExportCompat
@Serializable
enum class ClaimValueKind {
    STRING,
    BOOLEAN,
    NUMBER,
    INTEGER,
    ARRAY,
    OBJECT,
    DATE,
    DATE_TIME,
    URI,
    IMAGE,
    BINARY,
    MARKDOWN,
    REFERENCE,
    UNKNOWN,
}

@JsExportCompat
@Serializable
enum class ClaimWidgetHint {
    TEXT,
    MULTILINE_TEXT,
    CHECKBOX,
    BADGE,
    LIST,
    TABLE,
    GROUP,
    DATE,
    DATE_TIME,
    URI,
    IMAGE,
    MARKDOWN,
    HIDDEN,
    PICKLIST,
    FILE,
}

@JsExportCompat
@Serializable
enum class SdPolicy {
    ALWAYS,
    ALLOWED,
    NEVER,
}

@JsExportCompat
@Serializable
enum class SvgOrientation { PORTRAIT, LANDSCAPE }

@JsExportCompat
@Serializable
enum class SvgColorScheme { LIGHT, DARK }

@JsExportCompat
@Serializable
enum class SvgContrast { NORMAL, HIGH }

@JsExportCompat
@Serializable
enum class DesignAssetType {
    LOGO,
    BACKGROUND_IMAGE,
    SVG_TEMPLATE,
    PDF_TEMPLATE,
}
