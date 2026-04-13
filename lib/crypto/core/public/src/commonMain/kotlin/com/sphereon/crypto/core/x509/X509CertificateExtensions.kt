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
 *
 */

package com.sphereon.crypto.core.x509

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1EncapsulatingOctetString
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.crypto.pki.X509Certificate
import at.asitplus.awesn1.encoding.parse
import com.sphereon.crypto.core.interop.toCertificateDto
import com.sphereon.crypto.core.interop.toX509Certificate
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.readRawBytes
import kotlinx.io.IOException

suspend fun Certificate.downloadCertificateChain(httpClient: HttpClient): List<Certificate> = downloadExtraCertificatesInternal(this, httpClient, mutableSetOf())

suspend fun X509Certificate.downloadCertificateChain(httpClient: HttpClient): List<X509Certificate> =
    downloadExtraCertificatesInternal(this.toCertificateDto(), httpClient, mutableSetOf()).map { it.toX509Certificate() }

private suspend fun downloadExtraCertificatesInternal(
    certificate: Certificate,
    httpClient: HttpClient,
    seen: MutableSet<Certificate>,
): List<Certificate> {
    check(certificate !in seen) { "Circular Authority Information Access (AIA) reference detected for certificate: ${certificate.subjectDN}" }
    seen.add(certificate)
    val signumCertificate = certificate.toX509Certificate()

    val exts = signumCertificate.tbsCertificate.extensions ?: return emptyList()
    val aiaExt =
        exts.firstOrNull {
            it.oid.toString() == X509ExtensionOids.AUTHORITY_INFORMATION_ACCESS
        } ?: return emptyList()

    val octet = aiaExt.value as? Asn1EncapsulatingOctetString ?: error("AuthorityInfoAccess extension is not an OCTET STRING")

    val seqElement = Asn1Element.parse(octet.content) as? Asn1Sequence ?: error("AuthorityInfoAccess content is not a valid ASN.1 sequence")

    val downloaded = mutableListOf<Certificate>()

    for (accessDesc in seqElement.children) {
        val adSeq = accessDesc as? Asn1Sequence ?: continue
        if (adSeq.children.size < 2) {
            continue
        }

        val firstChild = adSeq.children.first()
        if (!isAccessMethodOid(
                firstChild,
                listOf(
                    X509ExtensionOids.AUTHORITY_INFORMATION_ACCESS,
                    X509ExtensionOids.CA_ISSUERS,
                ),
            )
        ) {
            continue
        }

        val uri = extractUriFromTaggedObject(adSeq.children[1]) ?: continue

        val response = httpClient.prepareGet(uri).execute()
        if (response.status.value !in 200..299) {
            throw IOException("Failed to download one of the certificates in the chain from $uri: ${response.status}")
        }

        val raw = response.readRawBytes()
        val cert = certificateFromDer(raw)
        downloaded += cert
        downloaded += downloadExtraCertificatesInternal(cert, httpClient, seen)
    }

    return downloaded
}
