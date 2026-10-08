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

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata

/**
 * Optional source of SD-JWT VC type metadata (the "VCT" documents wallets fetch from `vct` URLs,
 * draft-ietf-oauth-sd-jwt-vc §6). When a binding is present the issuer can serve VCTs itself
 * instead of relying on statically hosted files.
 *
 * This is a pluggable seam, NOT a mandatory feature:
 *  - IDK ships a config-driven implementation that derives VCTs from the same `oid4vci.issuer`
 *    credential configuration that drives the OID4VCI metadata (one authoring source for both).
 *  - EDK/VDX can contribute an implementation that derives the same VCTs from their richer semantic
 *    catalog/profile model. Both funnel through the shared pure builder
 *    [com.sphereon.sdjwt.vc.buildSdJwtVcTypeMetadata], so the wire shape is defined in one place.
 *  - Deployments that host VCTs statically (or do not issue SD-JWT VC at all) simply bind no
 *    provider; the serving endpoint then reports the VCT as not found.
 */
@JsExportCompat
interface VctTypeMetadataProvider {
    /**
     * Resolve the type metadata for [vct] (the bare identifier the issuer advertises, e.g. the last
     * path segment of the `vct` URL). Returns null when this provider does not know the type — the
     * caller treats that as "not found" rather than an error, so multiple providers / a static
     * fallback can coexist.
     */
    suspend fun resolve(vct: String): SdJwtVcTypeMetadata?

    /** The VCT identifiers this provider can serve. Empty when the provider contributes none. */
    suspend fun listVcts(): List<String>
}

/**
 * Default no-op provider: knows no VCTs. Lets the serving endpoint inject a [VctTypeMetadataProvider]
 * unconditionally (and simply 404) when no real source is configured, keeping the feature optional.
 */
@JsExportCompat
object NoOpVctTypeMetadataProvider : VctTypeMetadataProvider {
    override suspend fun resolve(vct: String): SdJwtVcTypeMetadata? = null

    override suspend fun listVcts(): List<String> = emptyList()
}
