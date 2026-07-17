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

package com.sphereon.openid.oid4vci.holder

interface Oid4vciHolderConfig {
    val clientId: String?
    val preferredFormat: String?
    val autoRequestNonce: Boolean get() = true
    val defaultDeferredPollingInterval: Int get() = 5
    val maxDeferredPollingAttempts: Int get() = 60

    /**
     * When true, the holder requires the issuer to provide signed metadata.
     * If the issuer does not include a `signed_metadata` field in the metadata response,
     * resolution fails with SIGNED_METADATA_REQUIRED.
     *
     * Note: when the issuer DOES provide `signed_metadata`, verification failure is always
     * an error regardless of this setting — the issuer committed to signing by including the field.
     */
    val requireVerifiedSignedMetadata: Boolean get() = false

}
