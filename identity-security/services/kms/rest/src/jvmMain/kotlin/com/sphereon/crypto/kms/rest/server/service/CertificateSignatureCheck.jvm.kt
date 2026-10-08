/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal actual fun isCertificateSignedBy(
    subjectDer: ByteArray,
    issuerDer: ByteArray,
): Boolean =
    try {
        val factory = CertificateFactory.getInstance("X.509")
        val subject = factory.generateCertificate(ByteArrayInputStream(subjectDer)) as X509Certificate
        val issuer = factory.generateCertificate(ByteArrayInputStream(issuerDer)) as X509Certificate
        subject.verify(issuer.publicKey)
        true
    } catch (_: GeneralSecurityException) {
        false
    }
