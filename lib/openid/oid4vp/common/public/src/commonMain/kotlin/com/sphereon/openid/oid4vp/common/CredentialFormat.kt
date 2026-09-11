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

package com.sphereon.openid.oid4vp.common

// OID4VP uses the protocol-neutral CredentialFormat model from oid4vc/common.
typealias CredentialFormat = com.sphereon.openid.oid4vc.common.CredentialFormat

// Protocol-local convenience functions delegate to that single shared model.
fun String.detectCredentialFormat(): CredentialFormat? =
    com.sphereon.openid.oid4vc.common.CredentialFormatDetector.detect(this)

fun String.matchesCredentialFormat(format: CredentialFormat): Boolean {
    val detected =
        com.sphereon.openid.oid4vc.common.CredentialFormat
            .fromValueLenient(this)
    return detected == format
}
