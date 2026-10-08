/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic

/**
 * Format of a verifiable presentation artifact.
 *
 * This vocabulary is intentionally separate from [CredentialFormat]. In particular,
 * `jwt_vp_json` describes a JWT-encoded VP and can never be parsed as a credential format.
 * VCDM 1.1 and VCDM 2.0 JWT VPs use the same OID4VP presentation identifier; their VCDM
 * version and JOSE profile remain properties of the classified document.
 */
@JsExportCompat
@Serializable
enum class PresentationFormat(
    val value: String,
) {
    /** JWT-secured Verifiable Presentation (`jwt_vp_json`). */
    @SerialName("jwt_vp_json")
    JWT_VP_JSON("jwt_vp_json"),

    /** Data Integrity / Linked Data Proof Verifiable Presentation (`ldp_vp`). */
    @SerialName("ldp_vp")
    LDP_VP("ldp_vp"),
    ;

    val isJwt: Boolean
        get() = this == JWT_VP_JSON

    val isDataIntegrity: Boolean
        get() = this == LDP_VP

    companion object {
        @JvmStatic
        fun fromValue(value: String): PresentationFormat? = entries.find { it.value == value }

        @JvmStatic
        fun fromValueLenient(value: String): PresentationFormat? {
            fromValue(value)?.let { return it }
            return when (value.lowercase()) {
                "application/vp+jwt", "vp+jwt" -> JWT_VP_JSON
                else -> null
            }
        }
    }
}
