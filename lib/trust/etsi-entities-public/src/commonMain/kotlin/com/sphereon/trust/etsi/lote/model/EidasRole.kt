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
 * Each role maps to:
 * - A 602 LoTEType URI (used to navigate the LOTL and select the right LoTE)
 * - 602 service type URIs for issuance and revocation services
 * - 612 legacy service types for transitional support
 *
 * Per ETSI TS 119 602 Annex C (LoTEType) and Annex D (service types).
 */
@JsExportCompat
enum class EidasRole(
    /** 602 LoTEType URI used to identify the relevant LoTE in the LOTL */
    val loTEType: String,
    /** 602 service type URI for the issuance service */
    val issuanceServiceType: String,
    /** 602 service type URI for the revocation service (null for registrars) */
    val revocationServiceType: String?,
    /** 612 service types for transitional lookup against pre-602 trust lists */
    val legacyServiceTypes: List<String>,
    /** Human-readable description */
    val description: String,
) {
    PID_ISSUER(
        loTEType = LoTEType.EU_PID_PROVIDERS,
        issuanceServiceType = LoTEServiceType.PID_ISSUANCE,
        revocationServiceType = LoTEServiceType.PID_REVOCATION,
        legacyServiceTypes = emptyList(),
        description = "PID Provider",
    ),
    WALLET_PROVIDER(
        loTEType = LoTEType.EU_WALLET_PROVIDERS,
        issuanceServiceType = LoTEServiceType.WALLET_ISSUANCE,
        revocationServiceType = LoTEServiceType.WALLET_REVOCATION,
        legacyServiceTypes = emptyList(),
        description = "Wallet Provider",
    ),
    QEAA_ISSUER(
        loTEType = LoTEType.EU_PUB_EAA_PROVIDERS,
        issuanceServiceType = LoTEServiceType.PUB_EAA_ISSUANCE,
        revocationServiceType = LoTEServiceType.PUB_EAA_REVOCATION,
        legacyServiceTypes = listOf("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"),
        description = "QEAA Provider",
    ),
    RELYING_PARTY(
        loTEType = LoTEType.EU_WRPAC_PROVIDERS,
        issuanceServiceType = LoTEServiceType.WRPAC_ISSUANCE,
        revocationServiceType = LoTEServiceType.WRPAC_REVOCATION,
        legacyServiceTypes = emptyList(),
        description = "Relying Party",
    ),
    REGISTRAR(
        loTEType = LoTEType.EU_REGISTRARS,
        issuanceServiceType = LoTEServiceType.REGISTER,
        revocationServiceType = null,
        legacyServiceTypes = emptyList(),
        description = "Registrar",
    ),
    ;

    companion object {
        /**
         * Finds the EidasRole matching a given LoTEType URI.
         * Returns null if no role matches.
         */
        @JvmStatic
        fun fromLoTEType(loTEType: String): EidasRole? = entries.firstOrNull { it.loTEType == loTEType }

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
