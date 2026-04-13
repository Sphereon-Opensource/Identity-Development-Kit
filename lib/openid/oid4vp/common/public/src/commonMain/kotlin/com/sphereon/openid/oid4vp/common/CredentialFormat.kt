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

// Shared CredentialFormat lives in oid4vc/common; these typealiases
// maintain backward compatibility for existing OID4VP consumers.
typealias CredentialFormat = com.sphereon.openid.oid4vc.common.CredentialFormat

// Re-export extension functions so existing imports keep working
fun String.detectCredentialFormat(): CredentialFormat? =
    com.sphereon.openid.oid4vc.common.CredentialFormat
        .detectFormat(this)

fun String.matchesCredentialFormat(format: CredentialFormat): Boolean {
    val detected =
        com.sphereon.openid.oid4vc.common.CredentialFormat
            .fromValueLenient(this)
    return detected == format || (detected?.isSdJwt == true && format.isSdJwt)
}
