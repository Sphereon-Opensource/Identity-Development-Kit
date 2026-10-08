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
 *
 */

package com.sphereon.trust.etsi.lote.model

import com.sphereon.core.compat.JsExportCompat
import kotlin.jvm.JvmStatic

/**
 * Enumerates the eIDAS 2.0 roles that can be verified against EU trust lists.
 *
 * Each role declares the one standards profile through which it may be
 * resolved.  QEAA providers are member-state TS 119 612 services.  The
 * remaining roles are TS 119 602 LoTE services.
 *
 * Per ETSI TS 119 602 Annex C/D and ETSI TS 119 612 service identifiers.
 */
@JsExportCompat
enum class EtsiTrustListProfile {
    TS_119_612_MEMBER_STATE,
    TS_119_602_LOTE,
}

@JsExportCompat
enum class EidasRole(
    /** TS 119 602 LoTEType URI, or null for member-state TS 119 612 QEAA services. */
    val loTEType: String?,
    /** Service type URI for the issuance/trust-establishing service. */
    val issuanceServiceType: String,
    /** Revocation service URI, which is never sufficient to establish issuer trust. */
    val revocationServiceType: String?,
    /** Deprecated service identifiers accepted only for explicitly transitional callers. */
    val legacyServiceTypes: List<String>,
    /** Standards profile used for trust-list routing. */
    val trustListProfile: EtsiTrustListProfile,
    /** Human-readable description */
    val description: String,
) {
    PID_PROVIDER(
        loTEType = LoTEType.EU_PID_PROVIDERS,
        issuanceServiceType = LoTEServiceType.PID_ISSUANCE,
        revocationServiceType = LoTEServiceType.PID_REVOCATION,
        legacyServiceTypes = emptyList(),
        trustListProfile = EtsiTrustListProfile.TS_119_602_LOTE,
        description = "PID Provider",
    ),
    WALLET_PROVIDER(
        loTEType = LoTEType.EU_WALLET_PROVIDERS,
        issuanceServiceType = LoTEServiceType.WALLET_ISSUANCE,
        revocationServiceType = LoTEServiceType.WALLET_REVOCATION,
        legacyServiceTypes = emptyList(),
        trustListProfile = EtsiTrustListProfile.TS_119_602_LOTE,
        description = "Wallet Provider",
    ),
    QEAA_PROVIDER(
        loTEType = null,
        issuanceServiceType = LoTLServiceType.QEAA_ISSUANCE,
        revocationServiceType = null,
        legacyServiceTypes = emptyList(),
        trustListProfile = EtsiTrustListProfile.TS_119_612_MEMBER_STATE,
        description = "QEAA Provider",
    ),
    PUB_EAA_PROVIDER(
        loTEType = LoTEType.EU_PUB_EAA_PROVIDERS,
        issuanceServiceType = LoTEServiceType.PUB_EAA_ISSUANCE,
        revocationServiceType = LoTEServiceType.PUB_EAA_REVOCATION,
        legacyServiceTypes = emptyList(),
        trustListProfile = EtsiTrustListProfile.TS_119_602_LOTE,
        description = "Pub-EAA Provider",
    ),
    ACCESS_CA(
        loTEType = LoTEType.EU_WRPAC_PROVIDERS,
        issuanceServiceType = LoTEServiceType.WRPAC_ISSUANCE,
        revocationServiceType = LoTEServiceType.WRPAC_REVOCATION,
        legacyServiceTypes = emptyList(),
        trustListProfile = EtsiTrustListProfile.TS_119_602_LOTE,
        description = "Access Certificate Authority",
    ),
    REGISTRATION_CERTIFICATE_PROVIDER(
        loTEType = LoTEType.EU_WRPRC_PROVIDERS,
        issuanceServiceType = LoTEServiceType.WRPRC_ISSUANCE,
        revocationServiceType = LoTEServiceType.WRPRC_REVOCATION,
        legacyServiceTypes = emptyList(),
        trustListProfile = EtsiTrustListProfile.TS_119_602_LOTE,
        description = "Registration Certificate Provider",
    ),
    ;

    companion object {
        /**
         * Finds the EidasRole matching a given LoTEType URI.
         * Returns null if no role matches.
         */
        @JvmStatic
        fun fromLoTEType(loTEType: String): EidasRole? =
            entries.firstOrNull { it.loTEType == loTEType }

        /**
         * Finds the EidasRole matching a given service type URI
         * (checks both 602 issuance/revocation and 612 legacy types).
         * Returns null if no role matches.
         */
        @JvmStatic
        fun fromServiceType(serviceType: String): EidasRole? =
            entries.firstOrNull { role ->
                role.issuanceServiceType == serviceType ||
                    role.revocationServiceType == serviceType ||
                    role.legacyServiceTypes.contains(serviceType)
            }

        /** Source-compatible names retained for callers migrating to the split role model. */
        val PID_ISSUER: EidasRole get() = PID_PROVIDER
        val QEAA_ISSUER: EidasRole get() = QEAA_PROVIDER

        /**
         * Returns all service type URIs (issuance + revocation) for this role.
         */
        fun EidasRole.allServiceTypes(): List<String> =
            buildList {
                add(issuanceServiceType)
                revocationServiceType?.let { add(it) }
            }
    }
}
