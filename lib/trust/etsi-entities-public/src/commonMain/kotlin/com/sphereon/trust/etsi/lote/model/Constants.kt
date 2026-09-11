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

/**
 * LoTE type URIs per ETSI TS 119 602 Annex C.
 */
object LoTEType {
    const val EU_PID_PROVIDERS = "http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList"
    const val EU_WALLET_PROVIDERS = "http://uri.etsi.org/19602/LoTEType/EUWalletProvidersList"
    const val EU_WRPAC_PROVIDERS = "http://uri.etsi.org/19602/LoTEType/EUWRPACProvidersList"
    const val EU_WRPRC_PROVIDERS = "http://uri.etsi.org/19602/LoTEType/EUWRPRCProvidersList"
    const val EU_PUB_EAA_PROVIDERS = "http://uri.etsi.org/19602/LoTEType/EUPubEAAProvidersList"
    const val EU_REGISTRARS = "http://uri.etsi.org/19602/LoTEType/EURegistrarsAndRegistersList"
}

/**
 * Service type URIs per ETSI TS 119 602 Annex D.
 */
object LoTEServiceType {
    const val PID_ISSUANCE = "http://uri.etsi.org/19602/SvcType/PID/Issuance"
    const val PID_REVOCATION = "http://uri.etsi.org/19602/SvcType/PID/Revocation"
    const val WALLET_ISSUANCE = "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance"
    const val WALLET_REVOCATION = "http://uri.etsi.org/19602/SvcType/WalletSolution/Revocation"
    const val WRPAC_ISSUANCE = "http://uri.etsi.org/19602/SvcType/WRPAC/Issuance"
    const val WRPAC_REVOCATION = "http://uri.etsi.org/19602/SvcType/WRPAC/Revocation"
    const val WRPRC_ISSUANCE = "http://uri.etsi.org/19602/SvcType/WRPRC/Issuance"
    const val WRPRC_REVOCATION = "http://uri.etsi.org/19602/SvcType/WRPRC/Revocation"
    const val PUB_EAA_ISSUANCE = "http://uri.etsi.org/19602/SvcType/PubEAA/Issuance"
    const val PUB_EAA_REVOCATION = "http://uri.etsi.org/19602/SvcType/PubEAA/Revocation"
    const val REGISTER = "http://uri.etsi.org/19602/SvcType/Register"
}

/**
 * Service type URIs used by the member-state trusted lists defined by ETSI
 * TS 119 612.  These values must not be treated as TS 119 602 LoTE service
 * types.
 */
object LoTLServiceType {
    const val QEAA_ISSUANCE = "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q"
}

/**
 * Service status URIs per ETSI TS 119 602.
 */
object LoTEServiceStatus {
    const val NOTIFIED = "http://uri.etsi.org/19602/PubEAAProvidersList/SvcStatus/notified"
    const val WITHDRAWN = "http://uri.etsi.org/19602/PubEAAProvidersList/SvcStatus/withdrawn"
}
