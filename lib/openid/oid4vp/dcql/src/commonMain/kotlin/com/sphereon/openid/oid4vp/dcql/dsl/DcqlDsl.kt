/*
 * Copyright (c) 2026 Sphereon B.V.
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

/**
 * DSL marker annotation to prevent scope pollution in nested DCQL builders.
 *
 * This annotation ensures that methods from outer scopes are not accidentally
 * called in nested DSL blocks. For example, when building claim queries inside
 * a credential query, you won't accidentally call credential-level methods.
 *
 * Example:
 * ```kotlin
 * dcqlQuery {
 *     credential("identity") {
 *         sdJwtVc {
 *             // Only sdJwtVc methods are available here
 *             vctValues("https://credentials.example.com/identity")
 *         }
 *         claim("given_name")
 *         claim("family_name")
 *     }
 * }
 * ```
 */
@DslMarker
annotation class DcqlDslMarker

/**
 * Well-known credential formats per OpenID4VP 1.0
 */
object DcqlFormats {
    /** SD-JWT Verifiable Credential format */
    const val SD_JWT_VC = "dc+sd-jwt"

    /** ISO mDoc format */
    const val MSO_MDOC = "mso_mdoc"

    /** W3C JWT VC JSON format */
    const val JWT_VC_JSON = "jwt_vc_json"

    /** W3C Linked Data Proof VC format */
    const val LDP_VC = "ldp_vc"

    /** JWT VP format */
    const val JWT_VP = "jwt_vp"

    /** LDP VP format */
    const val LDP_VP = "ldp_vp"
}

/**
 * Well-known mDoc doctypes
 */
object MdocDoctypes {
    /** ISO/IEC 18013-5 Mobile Driving License */
    const val MDL = "org.iso.18013.5.1.mDL"
}

/**
 * Well-known mDoc namespaces
 */
object MdocNamespaces {
    /** ISO/IEC 18013-5 core namespace */
    const val ISO_18013_5_1 = "org.iso.18013.5.1"

    /** AAMVA namespace */
    const val AAMVA = "org.iso.18013.5.1.aamva"
}
