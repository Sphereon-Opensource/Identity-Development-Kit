/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Interface for checking certificate revocation status.
 *
 * Supports OCSP, CRL, and OCSP Stapling.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RevocationChecker", exact = true)
interface RevocationChecker {
    suspend fun checkRevocation(
        certificate: ByteArray,
        issuerCertificate: ByteArray? = null,
        options: RevocationCheckOptions = RevocationCheckOptions(),
    ): RevocationCheckResult
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RevocationCheckOptions", exact = true)
@JsExportCompat
data class RevocationCheckOptions(
    val checkOCSP: Boolean = true,
    val checkCRL: Boolean = true,
    val preferOCSP: Boolean = true,
    val timeoutMs: Long = 10000,
    val useCache: Boolean = true,
    val maxCacheAgeMs: Long = 3600000,
    val ocspResponderUrl: String? = null,
    val crlDistributionPoint: String? = null,
    val failOnUnknown: Boolean = false,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("RevocationCheckResult", exact = true)
@JsExportCompat
data class RevocationCheckResult(
    val status: RevocationStatus,
    val method: RevocationCheckMethod,
    val checkedAt: Long,
    val fromCache: Boolean = false,
    val revocationTime: Long? = null,
    val revocationReason: RevocationReason? = null,
    val errorMessage: String? = null,
    @JsExportIgnoreCompat
    val details: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("RevocationStatus", exact = true)
@JsExportCompat
enum class RevocationStatus {
    GOOD,
    REVOKED,
    UNKNOWN,
    UNAVAILABLE,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RevocationCheckMethod", exact = true)
@JsExportCompat
enum class RevocationCheckMethod {
    OCSP,
    CRL,
    OCSP_STAPLING,
    NONE,
    MULTIPLE,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RevocationReason", exact = true)
@JsExportCompat
enum class RevocationReason(
    val code: Int,
) {
    UNSPECIFIED(0),
    KEY_COMPROMISE(1),
    CA_COMPROMISE(2),
    AFFILIATION_CHANGED(3),
    SUPERSEDED(4),
    CESSATION_OF_OPERATION(5),
    CERTIFICATE_HOLD(6),
    REMOVE_FROM_CRL(8),
    PRIVILEGE_WITHDRAWN(9),
    AA_COMPROMISE(10),
    ;

    companion object {
        @JvmStatic
        fun fromCode(code: Int): RevocationReason? = RevocationReason.entries.firstOrNull { it.code == code }
    }
}

class RevocationCheckException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Interface for OCSP checking.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OCSPChecker", exact = true)
interface OCSPChecker {
    suspend fun checkOCSP(
        certificate: ByteArray,
        issuerCertificate: ByteArray?,
        options: RevocationCheckOptions,
    ): RevocationCheckResult
}

/**
 * Interface for CRL checking.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CRLChecker", exact = true)
interface CRLChecker {
    suspend fun checkCRL(
        certificate: ByteArray,
        options: RevocationCheckOptions,
    ): RevocationCheckResult
}
