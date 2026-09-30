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

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.eu.impl.testutil

import com.sphereon.catalog.eu.impl.publish.CatalogueSigningKey
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.CertificateService
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private fun Any.asCertificateServiceGraph(): CertificateService.Graph = this as CertificateService.Graph

/**
 * A key generated inside the software KMS of the test session, with a self-signed certificate created by the KMS
 * certificate service. The private key is only ever used through the key manager.
 */
class KmsSigningKey(
    val signingKey: CatalogueSigningKey,
    val alias: String,
    val certificateDer: ByteArray,
)

suspend fun CatalogueTestContext.newKmsSigningKey(
    algorithm: SignatureAlgorithm,
    commonName: String,
): KmsSigningKey {
    val keyManager = session.graph.asKeyManagerServiceGraph().keyManagerService
    val certificates = session.graph.asCertificateServiceGraph().certificateService
    val pair = keyManager.generateKey(alg = algorithm)
    val managed = pair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
    val dn = X509DistinguishedNameElements(commonName = commonName, organizationName = "Catalogue test", country = "NL")
    val notAfter = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC).toLocalDateTime(TimeZone.UTC).toString()
    val certificate =
        certificates.createCertificate(
            issuerKeyInfo = managed,
            issuer = dn,
            subjectKeyInfo = managed,
            subject = dn,
            serialNumber = 1,
            notBefore = LocalDateTimeKMP.now(),
            notAfter = LocalDateTimeKMP.fromString(notAfter),
        ).certificate.der
    val keyInfo = KeyInfo<KeyType>(alias = managed.alias, signatureAlgorithm = algorithm)
    return KmsSigningKey(CatalogueSigningKey(keyInfo, listOf(certificate), algorithm), managed.alias, certificate)
}
