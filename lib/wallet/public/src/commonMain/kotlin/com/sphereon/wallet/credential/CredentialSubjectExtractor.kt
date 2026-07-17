/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential

/**
 * Extracts the credential subject identifier(s) from an issued credential's raw bytes.
 *
 * Returns a list because W3C VCs allow multiple credential subjects. No cryptographic
 * verification is performed - the payload is already issuer-signed and trusted at this
 * point in the issuance flow.
 *
 * Lives in lib-wallet-public so storage-seam consumers such as the OID4VCI interaction-engine
 * receiver can depend on the interface without pulling in a production dependency on
 * lib-wallet-impl. The default implementation
 * ([com.sphereon.wallet.impl.CredentialSubjectExtractorImpl]) stays in lib-wallet-impl and is
 * contributed via Metro [dev.zacsweers.metro.ContributesBinding].
 */
interface CredentialSubjectExtractor {
    /**
     * Extract subject [IdentifierRef]s from [raw] according to [format].
     *
     * Returns an empty list when the format carries no standard subject identifier
     * (e.g. mso_mdoc) or when no subject is present in the payload. Never throws.
     */
    fun extractSubjects(
        format: CredentialFormat,
        raw: String
    ): List<IdentifierRef>
}
