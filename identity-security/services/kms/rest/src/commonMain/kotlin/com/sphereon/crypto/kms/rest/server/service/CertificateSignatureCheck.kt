/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

/** Whether the public key of [issuerDer] verifies the signature on [subjectDer]. Both are DER X.509 certificates. */
internal expect fun isCertificateSignedBy(
    subjectDer: ByteArray,
    issuerDer: ByteArray,
): Boolean
