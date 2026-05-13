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

package com.sphereon.jsonld

/**
 * Canonical IRIs and type names from the W3C VC and UN/CEFACT UNTP
 * specifications that IDK code references in multiple modules. Centralised
 * here to keep call sites consistent and refactorable.
 */
object WellKnownContexts {
    /** W3C Verifiable Credentials Data Model 2.0 base context. */
    const val VCDM_2_0: String = "https://www.w3.org/ns/credentials/v2"

    /** W3C Verifiable Credential Data Integrity 1.0 context. */
    const val VC_DATA_INTEGRITY_V1: String = "https://w3id.org/security/data-integrity/v1"

    /** W3C Verifiable Credential Data Integrity 1.1+ context. */
    const val VC_DATA_INTEGRITY_V2: String = "https://w3id.org/security/data-integrity/v2"

    /** UN/CEFACT UNTP 0.7.0 vocabulary, shared by DPP, DCC, DTE, DFR, DIA. */
    const val UNTP_VOCAB: String = "https://vocabulary.uncefact.org/untp/"
}

/**
 * Canonical credential `type` names referenced across IDK modules.
 */
object WellKnownCredentialTypes {
    /** W3C VC base type. Always present in a VC's `type` array. */
    const val VERIFIABLE_CREDENTIAL: String = "VerifiableCredential"
}
