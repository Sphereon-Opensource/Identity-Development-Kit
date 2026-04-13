/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.openid.oid4vp.dcql.dsl

import com.sphereon.openid.oid4vp.dcql.DcqlTrustedAuthority
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Builder scope for trusted authorities in a DCQL credential query.
 *
 * Trusted authorities specify constraints on who may have issued the requested credential.
 * Three types are supported per OpenID4VP 1.0 Section 6.1.1:
 * - `openid_federation`: OpenID Federation entity identifiers (HTTPS URLs)
 * - `etsi_trusted_list`: ETSI Trusted List URLs (HTTPS URLs)
 * - `authority_key_identifier`: X.509 Authority Key Identifiers (base64url-encoded)
 *
 * Example:
 * ```kotlin
 * credential("identity") {
 *     trustedAuthorities {
 *         openIdFederation("https://federation.example.com")
 *         etsiTrustedList("https://eidas.europa.eu/TL/EN_TL.xml")
 *     }
 * }
 * ```
 */
@DcqlDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustedAuthoritiesScope", exact = true)
@JsExportCompat
class TrustedAuthoritiesScope {
    private val authorities = mutableListOf<DcqlTrustedAuthority>()

    /**
     * Adds an OpenID Federation trusted authority.
     *
     * OpenID Federation entity identifiers are HTTPS URLs that identify
     * entities within an OpenID Federation.
     *
     * @param entityIds One or more entity identifiers (HTTPS URLs)
     */
    fun openIdFederation(vararg entityIds: String) {
        if (entityIds.isNotEmpty()) {
            authorities.add(
                DcqlTrustedAuthority(
                    type = DcqlTrustedAuthority.TYPE_OPENID_FEDERATION,
                    values = entityIds.toList()
                )
            )
        }
    }

    /**
     * Adds an ETSI Trusted List authority.
     *
     * ETSI Trusted Lists are XML documents published at HTTPS URLs that
     * list trusted service providers (typically for eIDAS compliance).
     *
     * @param urls One or more trusted list URLs (HTTPS)
     */
    fun etsiTrustedList(vararg urls: String) {
        if (urls.isNotEmpty()) {
            authorities.add(
                DcqlTrustedAuthority(
                    type = DcqlTrustedAuthority.TYPE_ETSI_TRUSTED_LIST,
                    values = urls.toList()
                )
            )
        }
    }

    /**
     * Adds an X.509 Authority Key Identifier trusted authority.
     *
     * Authority Key Identifiers are base64url-encoded values that identify
     * X.509 certificate issuers.
     *
     * @param akis One or more base64url-encoded Authority Key Identifiers
     */
    fun authorityKeyIdentifier(vararg akis: String) {
        if (akis.isNotEmpty()) {
            authorities.add(
                DcqlTrustedAuthority(
                    type = DcqlTrustedAuthority.TYPE_AUTHORITY_KEY_IDENTIFIER,
                    values = akis.toList()
                )
            )
        }
    }

    internal fun build(): List<DcqlTrustedAuthority>? {
        return authorities.takeIf { it.isNotEmpty() }
    }
}
