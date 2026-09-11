/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common

import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassifier
import com.sphereon.openid.oid4vc.common.vcdm.VcdmDocumentKind

/**
 * Detects a credential representation without treating a presentation as a credential.
 *
 * Compact JWTs are classified as VCDM documents before a format is returned. A valid JWT VP
 * therefore returns null here; callers that need its representation must use
 * [PresentationFormatDetector].
 */
object CredentialFormatDetector {
    fun detect(value: String): CredentialFormat? {
        if (value.contains('~')) {
            return if (value.substringBefore('~').count { it == '.' } == 2) {
                CredentialFormat.SD_JWT_VC
            } else {
                null
            }
        }
        if (value.contains('.')) {
            val classification = VcdmClassifier.classifyCompactJws(value).getOrNull() ?: return null
            return classification.credentialFormat.takeIf {
                classification.document.kind == VcdmDocumentKind.CREDENTIAL
            }
        }
        return if (value.length > MDOC_MIN_LENGTH) CredentialFormat.MSO_MDOC else null
    }

    private const val MDOC_MIN_LENGTH = 20
}

/** Detect a credential representation. JWT VPs are deliberately not credentials. */
fun String.detectCredentialFormat(): CredentialFormat? = CredentialFormatDetector.detect(this)
