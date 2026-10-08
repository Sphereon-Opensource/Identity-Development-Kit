package com.sphereon.mdoc.data.device

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborString
import com.sphereon.crypto.core.cose.CoseHeaderCborCodecImpl
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodecImpl
import com.sphereon.mdoc.transfer.reader.ReaderAuthentication
import java.util.Base64
import java.io.ByteArrayInputStream
import java.security.Signature
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Independent decode assertions for preserved OWF mdoc-ts and Multipaz bytes.
 * The expected fields below are hand-derived from upstream fixtures, not
 * produced by this module's fixture builders.
 */
class ExternalVectorInteropJvmTest {
    private val codec = DeviceResponseCborCodecImpl()
    private val msoCodec = MobileSecurityObjectCborCodecImpl()
    private val itemCodec = IssuerSignedItemCborCodecImpl()

    @Test
    fun ubique_device_response_decodes_and_reencodes_external_bytes() {
        val original = fixtureBytes()
        val decoded = codec.decode(original).getOrThrow()
        val response = decoded.value
        val document = assertNotNull(response.documents).single()
        val issuerSigned = assertNotNull(document.issuerSigned)

        assertEquals("1.0", response.version.toString())
        assertEquals(0u, response.status.value)
        assertEquals("org.iso.18013.5.1.mDL", document.docType.toString())
        val msoPayload = assertNotNull(issuerSigned.issuerAuth.payload).value
        val mso = msoCodec.decode(msoPayload).getOrThrow().value
        assertEquals("org.iso.18013.5.1.mDL", mso.docType.toString())
        assertEquals("SHA-256", mso.digestAlgorithm.toString())
        assertEquals(setOf(DigestID(0u), DigestID(1u), DigestID(2u)), mso.valueDigests[NameSpace("org.iso.18013.5.1")]!!.keys)
        assertEquals("2025-01-30T10:56:11Z", mso.validityInfo.signed.toString())
        assertEquals("2025-01-30T10:56:11Z", mso.validityInfo.validFrom.toString())
        assertEquals("2025-02-13T10:56:11Z", mso.validityInfo.validUntil.toString())
        val namespaces = assertNotNull(issuerSigned.nameSpaces)
        assertEquals(1, namespaces.size)
        assertTrue(namespaces.keys.any { it.toString() == "org.iso.18013.5.1" })
        assertEquals(
            setOf("family_name"),
            namespaces[NameSpace("org.iso.18013.5.1")]!!.map { it.data().elementIdentifier.toString() }.toSet(),
        )
        assertNotNull(document.deviceSigned)
        val ubiqueCertificate = certificate(hexFixture("external-vectors/owf-mdoc-ts/ubique-issuer-certificate.der.hex"))
        ubiqueCertificate.checkValidity(Date(1738368000000L))
        assertTrue(verifyCoseSign1(issuerSigned.issuerAuth, ubiqueCertificate))
        val tamperedUbiqueSignature = issuerSigned.issuerAuth.signature.value.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(verifyCoseSign1(issuerSigned.issuerAuth.copy(signature = CborByteString(tamperedUbiqueSignature)), ubiqueCertificate))
        assertTrue(response.original!!.contentEquals(original))

        val freshMsoBytes = msoCodec.encode(mso.copy(original = null)).getOrThrow()
        assertTrue(freshMsoBytes.isNotEmpty())
        val changedMso = mso.copy(docType = DocType("org.example.changed"), original = null)
        assertEquals("org.example.changed", msoCodec.decode(msoCodec.encode(changedMso).getOrThrow()).getOrThrow().value.docType.toString())
        val fresh = freshResponse(response, issuerSigned.issuerAuth.copy(payload = CborByteString(freshMsoBytes)))
        assertTrue(fresh.original == null)
        assertTrue(fresh.documents!!.single().original == null)
        assertTrue(fresh.documents!!.single().issuerSigned.original == null)
        val reencoded = codec.encode(fresh).getOrThrow()
        assertFalse(reencoded.contentEquals(original))
        val roundTrip = codec.decode(reencoded).getOrThrow().value
        assertEquals(response.version, roundTrip.version)
        assertEquals(response.status, roundTrip.status)
        val roundTripDocument = assertNotNull(roundTrip.documents).single()
        assertEquals(document.docType, roundTripDocument.docType)
        val roundTripMso = msoCodec.decode(roundTripDocument.issuerSigned.issuerAuth.payload!!.value).getOrThrow().value
        assertEquals(mso.docType, roundTripMso.docType)
        assertEquals(mso.valueDigests.keys, roundTripMso.valueDigests.keys)
        assertEquals(mso.validityInfo, roundTripMso.validityInfo)

        // Changing a decoded field must change the wire value; this catches any
        // remaining top-level or nested original-byte shortcut.
        val changed = fresh.copy(status = DeviceResponseStatus(10u), documents = null)
        val changedRoundTrip = codec.decode(codec.encode(changed).getOrThrow()).getOrThrow().value
        assertEquals(10u, changedRoundTrip.status.value)
        assertTrue(changedRoundTrip.documents.isNullOrEmpty())
    }

    @Test
    fun malformed_external_device_response_is_rejected() {
        val malformed = fixtureBytes().copyOf()
        malformed[0] = 0x01

        assertTrue(codec.decode(malformed).isErr)
    }

    @Test
    fun multipaz_annex_d_device_engagement_decodes_and_reencodes() {
        val original = hexFixture("external-vectors/multipaz/multipaz-annex-d-device-engagement.hex")
        val engagement = DeviceEngagementCborCodecImpl().decode(original).getOrThrow().value

        assertEquals("1.0", engagement.version.toString())
        assertEquals(1u, engagement.security.cipherSuite)
        assertEquals(1, engagement.deviceRetrievalMethods!!.size)
        assertTrue(engagement.deviceRetrievalMethods!!.single().type.name == "BLE")
        assertTrue(engagement.security.eDeviceKeyBytes.value.taggedItem.value.isNotEmpty())
        assertTrue(Cbor.encode(engagement.security.eDeviceKeyBytes).contentEquals(hexFixture("external-vectors/multipaz/multipaz-annex-d-e-device-key-bytes.hex")))
        assertTrue(DeviceEngagementCborCodecImpl().encode(engagement.copyWithOriginal(null)).getOrThrow().contentEquals(original))
    }

    @Test
    fun multipaz_annex_d_device_request_decodes_and_reencodes() {
        val original = hexFixture("external-vectors/multipaz/multipaz-annex-d-device-request.hex")
        val request = DeviceRequestCborCodecImpl().decode(original).getOrThrow().value

        assertEquals("1.0", request.version.toString())
        val docRequest = assertNotNull(request.docRequests).single()
        assertEquals("org.iso.18013.5.1.mDL", docRequest.itemsRequest.docType.toString())
        assertEquals(6, docRequest.itemsRequest.nameSpaces[NameSpace("org.iso.18013.5.1")]!!.size)
        assertEquals(
            setOf("family_name", "document_number", "driving_privileges", "issue_date", "expiry_date", "portrait"),
            docRequest.itemsRequest.nameSpaces[NameSpace("org.iso.18013.5.1")]!!.keys.map { it.toString() }.toSet(),
        )
        val freshRequest = request.copy(
            docRequests = request.docRequests!!.map { doc ->
                doc.copy(itemsRequest = doc.itemsRequest.copy(original = null), original = null)
            }.toTypedArray(),
            original = null,
        )
        val reencoded = DeviceRequestCborCodecImpl().encode(freshRequest).getOrThrow()
        assertTrue(reencoded.contentEquals(original))
        val roundTrip = DeviceRequestCborCodecImpl().decode(reencoded).getOrThrow().value
        assertEquals(request.version, roundTrip.version)
        assertEquals(docRequest.itemsRequest.docType, roundTrip.docRequests!!.single().itemsRequest.docType)
    }

    @Test
    fun multipaz_annex_d_reader_auth_signature_verifies_with_source_transcript_and_certificate() {
        val request = DeviceRequestCborCodecImpl().decode(hexFixture("external-vectors/multipaz/multipaz-annex-d-device-request.hex")).getOrThrow().value
        val docRequest = assertNotNull(request.docRequests).single()
        val readerAuth = assertNotNull(docRequest.readerAuth)
        assertNull(readerAuth.payload)
        assertTrue(CoseSign1CborCodecImpl().encode(readerAuth).getOrThrow().contentEquals(hexFixture("external-vectors/multipaz/multipaz-annex-d-reader-auth.hex")))
        assertTrue(docRequest.itemsRequest.original!!.contentEquals(hexFixture("external-vectors/multipaz/multipaz-annex-d-items-request.hex")))

        val transcriptBytes = hexFixture("external-vectors/multipaz/multipaz-annex-d-session-transcript-bytes.hex")
        assertEquals(582, transcriptBytes.size)
        assertEquals("f823ac566e22b2106c9b7c02fadc7482e559a1de8a809b56c828779e6d67570e", sha256Hex(transcriptBytes))

        val transcript = SessionTranscriptCborCodecImpl().decode(transcriptBytes).getOrThrow().value
        val itemsRequestBytes = CborEncodedItem(hexFixture("external-vectors/multipaz/multipaz-annex-d-items-request.hex"), docRequest.itemsRequest)
        val readerAuthentication = ReaderAuthentication(transcript, itemsRequestBytes)
        val detachedPayload = com.sphereon.mdoc.ReaderAuthenticationCborCodecImpl().encodeTag24(readerAuthentication).getOrThrow()
        val readerCertificate = certificate(hexFixture("external-vectors/multipaz/multipaz-annex-d-reader-cert.hex"))
        readerCertificate.checkValidity(Date(1609459200000L))
        assertTrue(verifyCosePayload(readerAuth, detachedPayload, readerCertificate))

        val tampered = readerAuth.signature.value.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(verifyCosePayload(readerAuth.copy(signature = CborByteString(tampered)), detachedPayload, readerCertificate))
    }

    @Test
    fun multipaz_annex_d_issuer_auth_signature_verifies_with_source_certificate_and_rejects_tampering() {
        val original = hexFixture("external-vectors/multipaz/multipaz-annex-d-device-response.hex")
        val response = codec.decode(original).getOrThrow().value
        val document = response.documents!!.single()
        val issuerSigned = document.issuerSigned
        val issuerAuth = issuerSigned.issuerAuth
        val mso = msoCodec.decode(assertNotNull(issuerAuth.payload).value).getOrThrow().value
        assertEquals(
            (0u..12u).map { DigestID(it) }.toSet(),
            mso.valueDigests[NameSpace("org.iso.18013.5.1")]!!.keys,
        )
        assertEquals("2020-10-01T13:30:02Z", mso.validityInfo.signed.toString())
        assertEquals("2020-10-01T13:30:02Z", mso.validityInfo.validFrom.toString())
        assertEquals("2021-10-01T13:30:02Z", mso.validityInfo.validUntil.toString())
        assertTrue(mso.deviceKeyInfo.deviceKey.x!!.value.contentEquals(hexBytes("96313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a")))
        assertTrue(mso.deviceKeyInfo.deviceKey.y!!.value.contentEquals(hexBytes("1fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d6")))
        assertEquals(
            setOf("family_name", "issue_date", "expiry_date", "document_number", "portrait", "driving_privileges"),
            issuerSigned.nameSpaces!![NameSpace("org.iso.18013.5.1")]!!.map { it.data().elementIdentifier.toString() }.toSet(),
        )
        assertEquals("Doe", issuerSigned.getIssuerSignedItem(NameSpace("org.iso.18013.5.1"), DataElementIdentifier("family_name"))!!.elementValue)
        assertTrue(
            issuerAuth.signature.value.contentEquals(
                hexBytes("59e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad85aa53f129134775d733754d7cb7a413766aeff13cb2e"),
            ),
        )
        val certificateBytes = hexFixture("external-vectors/multipaz/multipaz-annex-d-ds-cert.hex")
        val certificate = certificate(certificateBytes)

        // The source certificate is expired today; this is the source's controlled fixture date.
        certificate.checkValidity(Date(1609459200000L))
        assertTrue(verifyCoseSign1(issuerAuth, certificate))

        val tampered = issuerAuth.signature.value.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(verifyCoseSign1(issuerAuth.copy(signature = CborByteString(tampered)), certificate))
    }

    private fun fixtureBytes(): ByteArray {
        val stream = checkNotNull(javaClass.classLoader!!.getResourceAsStream("external-vectors/owf-mdoc-ts/ubique-device-response.cbor.b64"))
        val base64 = stream.bufferedReader().use { it.readText().filterNot(Char::isWhitespace) }
        return Base64.getUrlDecoder().decode(base64)
    }

    private fun hexFixture(path: String): ByteArray {
        val stream = checkNotNull(javaClass.classLoader!!.getResourceAsStream(path))
        val hex = stream.bufferedReader().use { it.readText().filterNot(Char::isWhitespace) }
        return hexBytes(hex)
    }

    private fun hexBytes(hex: String): ByteArray {
        val normalized = hex.filterNot(Char::isWhitespace)
        require(normalized.length % 2 == 0)
        return ByteArray(normalized.length / 2) { normalized.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun freshResponse(response: DeviceResponse, issuerAuth: CoseSign1<MobileSecurityObject>): DeviceResponse {
        val documents = response.documents!!.map { document ->
            val issuerSigned = document.issuerSigned
            val freshNameSpaces = issuerSigned.nameSpaces?.mapValues { (_, items) ->
                items.map { item -> itemCodec.encodeItem(item.data()).getOrThrow() }.toTypedArray()
            }
            document.copy(
                issuerSigned = IssuerSigned(freshNameSpaces, issuerAuth, original = null),
                deviceSigned = document.deviceSigned?.copy(original = null),
                original = null,
            )
        }.toTypedArray()
        return response.copy(documents = documents, original = null)
    }

    private fun certificate(bytes: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate

    private fun verifyCoseSign1(input: CoseSign1<*>, certificate: X509Certificate): Boolean {
        val payload = checkNotNull(input.payload).value
        return verifyCosePayload(input, payload, certificate)
    }

    private fun verifyCosePayload(input: CoseSign1<*>, payload: ByteArray, certificate: X509Certificate): Boolean {
        val protectedHeader = CoseHeaderCborCodecImpl().encode(input.protectedHeader).getOrThrow()
        val sigStructure =
            Cbor.encode(
                CborArray(
                    mutableListOf<CborItem<*>>(
                        CborString("Signature1"),
                        CborByteString(protectedHeader),
                        CborByteString(byteArrayOf()),
                        CborByteString(payload),
                    ),
                ),
            )
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(certificate.publicKey)
        signature.update(sigStructure)
        return signature.verify(coseEcdsaToDer(input.signature.value))
    }

    private fun coseEcdsaToDer(signature: ByteArray): ByteArray {
        require(signature.size == 64)
        val r = derInteger(signature.copyOfRange(0, 32))
        val s = derInteger(signature.copyOfRange(32, 64))
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    private fun derInteger(value: ByteArray): ByteArray {
        val firstNonZero = value.indexOfFirst { it != 0.toByte() }
        val unsigned = if (firstNonZero < 0) byteArrayOf(0) else value.copyOfRange(firstNonZero, value.size)
        val body = if (unsigned[0].toInt() and 0x80 != 0) byteArrayOf(0) + unsigned else unsigned
        return byteArrayOf(0x02, body.size.toByte()) + body
    }
}
