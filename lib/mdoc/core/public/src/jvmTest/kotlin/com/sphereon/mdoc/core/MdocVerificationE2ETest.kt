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

package com.sphereon.mdoc.core

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.compat.Uuid
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.toCborItem
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseMac0InputCbor
import com.sphereon.crypto.core.defaultCreateMac0
import com.sphereon.crypto.core.defaultCreateMac0UsingKeys
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.x509.X509VerifyServiceImpl
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.core.testutil.MdocTestContext
import com.sphereon.mdoc.data.DeviceAuthValidationImpl
import com.sphereon.mdoc.data.IssuerAuthValidationImpl
import com.sphereon.mdoc.data.MdocValidationsImpl
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceAuth
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceSigned
import com.sphereon.mdoc.data.device.DeviceSignedItems
import com.sphereon.mdoc.data.device.DeviceMac
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.IssuerSignedItemCborCodecImpl
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end regression coverage for the V-P0-11 fix (real mDoc DeviceAuth +
 * SessionTranscript verification on the OID4VP path).
 *
 * Round-trip:
 *
 * 1. Issue an mdoc Document by signing an MSO with a self-signed issuer cert.
 * 2. Holder-side: build the OID4VP `DeviceAuthentication` and call
 *    [MdocSignService.deviceSignDocument] to produce a fully presented Document.
 * 3. Verifier-side: run [MdocValidationsImpl] (cert chain + IssuerAuth COSE_Sign1
 *    + validity + docType + IssuerSignedItem digest match) and the new
 *    [DeviceAuthValidationImpl] against the same Document with the verifier-side
 *    SessionTranscript reconstruction.
 *
 * Then the negatives — each one should make the verifier fail with a critical
 * VerifyResult, mirroring what the OIDF conformance suite would observe:
 *
 * - **Wrong client_id** in the verifier's reconstructed SessionTranscript.
 * - **Wrong response_uri** ditto.
 * - **Wrong mdoc_generated_nonce** ditto.
 * - **Wrong authorization-request nonce** ditto.
 * - **Tampered issuer-signed item** (mismatched digest in MSO).
 *
 * The X.509 service is set to trust the test issuer's self-signed cert so the
 * cert-chain step validates against a known anchor.
 */
class MdocVerificationE2ETest {
    private lateinit var ctx: MdocTestContext
    private lateinit var mdocSignService: MdocSignService

    private val mdlDocType = DocType("org.iso.18013.5.1.mDL")
    private val mdlNamespace = NameSpace("org.iso.18013.5.1")

    // OID4VP context the verifier and holder agree on for the happy-path round trip.
    private val verifierClientId = "x509_san_dns:verifier.example.com"
    private val verifierResponseUri = "https://verifier.example.com/oid4vp/auth/response"
    private val authRequestNonce = "verifier-nonce-12345678"

    @BeforeTest
    fun setup() {
        ctx = MdocTestContext(this)
        mdocSignService = ctx.mdocSignService
    }

    @Test
    fun roundTripSucceedsForValidPresentation() =
        runTest {
            val signed = buildSignedHolderDocument()
            val signedDocument = signed.document

            val validators = buildValidators(signed.issuerCertChain)

            val expectedTranscript =
                SessionTranscript.fromOid4vpClientIdAndResponseUri(
                    clientId = verifierClientId,
                    nonce = authRequestNonce,
                    jwkThumbprint = null,
                    responseUri = verifierResponseUri,
                )

            val mdocResults = validators.mdoc.fromDocument(document = signedDocument)
            assertFalse(
                mdocResults.error,
                "MdocValidations.fromDocument should accept a freshly-signed valid document. " +
                    describeFailures(mdocResults.verifications),
            )

            val deviceAuthResult =
                validators.deviceAuth.verifyDeviceAuth(
                    document = signedDocument,
                    expectedSessionTranscript = expectedTranscript,
                )
            assertFalse(
                deviceAuthResult.error,
                "DeviceAuth verification should succeed for a freshly-signed valid document: ${deviceAuthResult.message}",
            )
        }

    @Test
    fun deviceAuthRejectsWrongClientId() =
        runTest {
            assertSessionTranscriptMismatchFails(
                clientId = "x509_san_dns:imposter.example.com",
                responseUri = verifierResponseUri,
                authorizationRequestNonce = authRequestNonce,
                description = "wrong client_id",
            )
        }

    @Test
    fun deviceMacVerificationUsesTheReconstructedSessionTranscript() =
        runTest {
            val signed = buildSignedHolderDocument()
            val expectedTranscript =
                SessionTranscript.fromOid4vpClientIdAndResponseUri(
                    clientId = verifierClientId,
                    nonce = authRequestNonce,
                    jwkThumbprint = null,
                    responseUri = verifierResponseUri,
                )
            val macKey = "01234567890123456789012345678901".encodeToByteArray()
            val deviceSigned = requireNotNull(signed.document.deviceSigned)
            val macPayload =
                CborEncodedItem<Any>(
                    encodeExpectedDeviceAuthenticationPayload(
                        sessionTranscript = expectedTranscript,
                        docType = signed.document.docType.toString(),
                        deviceNamespaces = deviceSigned.nameSpaces,
                    ),
                ).value.toBstr()
            val mac =
                defaultCreateMac0(
                    input =
                        CoseMac0InputCbor(
                            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
                            detachedPayload = macPayload.value,
                        ),
                    sharedSecret = macKey,
                ).coseMac0
            val macDocument =
                signed.document.copy(
                    deviceSigned =
                        deviceSigned.copy(
                            deviceAuth = DeviceAuth(deviceMac = DeviceMac.fromCoseMac0(mac), original = null),
                            original = null,
                        ),
                    original = null,
                )

            val result =
                buildValidators(signed.issuerCertChain).deviceAuth.verifyDeviceAuthWithMac(
                    document = macDocument,
                    expectedSessionTranscript = expectedTranscript,
                    macKey = macKey,
                )
            assertFalse(result.error, "A valid COSE_Mac0 should verify: ${result.message}")

            val wrongTranscript =
                SessionTranscript.fromOid4vpClientIdAndResponseUri(
                    clientId = "x509_san_dns:imposter.example.com",
                    nonce = authRequestNonce,
                    jwkThumbprint = null,
                    responseUri = verifierResponseUri,
                )
            val wrongResult =
                buildValidators(signed.issuerCertChain).deviceAuth.verifyDeviceAuthWithMac(
                    document = macDocument,
                    expectedSessionTranscript = wrongTranscript,
                    macKey = macKey,
                )
            assertTrue(wrongResult.error && wrongResult.critical, "A transcript mismatch must fail closed.")
        }

    @Test
    fun deviceSigningUsesAnAdvertisedReaderMacKey() =
        runTest {
            val signed = buildSignedHolderDocument()
            val readerKeyPair =
                ctx.kms.generateKeyAsync(
                    alias = "test-reader-mac-key-${Uuid.v4String()}",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                    providerId = null,
                )
            val readerPrivateKeyInfo = readerKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)
            val readerPublicKey = requireNotNull(readerPrivateKeyInfo.key).toPublicKey() as CoseKey
            val holderPublicKey = requireNotNull(signed.deviceKeyInfo.key).toPublicKey() as CoseKey
            val readerPrivateResolved =
                CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
                    CoseJoseKeyMappingService.toResolvedKeyInfo(readerPrivateKeyInfo, readerPrivateKeyInfo.key),
                )

            val expectedTranscript =
                SessionTranscript.fromOid4vpClientIdAndResponseUri(
                    clientId = verifierClientId,
                    nonce = authRequestNonce,
                    jwkThumbprint = null,
                    responseUri = verifierResponseUri,
                )
            val request =
                DocRequest(
                    itemsRequest = DeviceItemsRequest(docType = mdlDocType, nameSpaces = emptyMap()),
                )
            val deviceAuthentication =
                com.sphereon.mdoc.data.device.DeviceAuthentication(
                    sessionTranscript = expectedTranscript,
                    docType = mdlDocType,
                    deviceNamespaces = DeviceNameSpaces(mapOf()),
                    original = null,
                )

            val macDocument =
                mdocSignService.deviceSignDocument(
                    request = request,
                    document = signed.document,
                    deviceAuthentication = deviceAuthentication,
                    deviceKeyInfo = signed.deviceKeyInfo,
                    macKeys = arrayOf(readerPublicKey),
                )
            val macAuth = requireNotNull(macDocument.deviceSigned).deviceAuth
            assertTrue(macAuth.deviceMac?.isCoseMac0() == true, "An advertised MAC key must select COSE_Mac0 authentication.")

            var verifierMacKey: ByteArray? = null
            defaultCreateMac0UsingKeys(
                provider = dev.whyoleg.cryptography.CryptographyProvider.Default,
                input =
                    CoseMac0InputCbor(
                        protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
                        detachedPayload = byteArrayOf(0),
                    ),
                selfPrivateKey = readerPrivateResolved,
                otherPublicKey = ResolvedKeyInfo(key = holderPublicKey),
            ) { provider, input, sharedSecret, alg ->
                verifierMacKey = sharedSecret
                defaultCreateMac0(input = input, sharedSecret = sharedSecret, alg = alg, provider = provider)
            }

            val result =
                buildValidators(signed.issuerCertChain).deviceAuth.verifyDeviceAuthWithMac(
                    document = macDocument,
                    expectedSessionTranscript = expectedTranscript,
                    macKey = requireNotNull(verifierMacKey),
                )
            assertFalse(result.error, "Holder-generated COSE_Mac0 should verify with the reader-derived EMacKey: ${result.message}")
        }

    @Test
    fun deviceAuthRejectsWrongResponseUri() =
        runTest {
            assertSessionTranscriptMismatchFails(
                clientId = verifierClientId,
                responseUri = "https://imposter.example.com/oid4vp/auth/response",
                authorizationRequestNonce = authRequestNonce,
                description = "wrong response_uri",
            )
        }

    @Test
    fun deviceAuthRejectsWrongAuthRequestNonce() =
        runTest {
            assertSessionTranscriptMismatchFails(
                clientId = verifierClientId,
                responseUri = verifierResponseUri,
                authorizationRequestNonce = "different-auth-nonce-00000000",
                description = "wrong authorization-request nonce",
            )
        }

    @Test
    fun digestVerificationRejectsTamperedIssuerSignedItem() =
        runTest {
            val signed = buildSignedHolderDocument()

            // Tamper: replace the issuer-signed item under a known digestID with a freshly
            // built one that hashes differently. The MSO still references the original
            // digest so the verifier's hash-vs-MSO check must fail.
            val tamperedDocument =
                tamperFirstIssuerSignedItem(signed.document)

            val validators = buildValidators(signed.issuerCertChain)
            val mdocResults = validators.mdoc.fromDocument(document = tamperedDocument)

            assertTrue(
                mdocResults.error,
                "Tampered IssuerSignedItem should fail digest verification under MdocValidations.",
            )
            val digestFailure =
                mdocResults.verifications.firstOrNull { it.error && it.critical && (it.message?.contains("digest") == true) }
            assertTrue(
                digestFailure != null,
                "Expected a digest-related critical failure. " + describeFailures(mdocResults.verifications),
            )
        }

    // ====================================================================
    // Test fixtures
    // ====================================================================

    private suspend fun assertSessionTranscriptMismatchFails(
        clientId: String,
        responseUri: String,
        authorizationRequestNonce: String,
        description: String,
    ) {
        val signed = buildSignedHolderDocument()
        val signedDocument = signed.document
        val validators = buildValidators(signed.issuerCertChain)

        val tamperedTranscript =
            SessionTranscript.fromOid4vpClientIdAndResponseUri(
                clientId = clientId,
                nonce = authorizationRequestNonce,
                jwkThumbprint = null,
                responseUri = responseUri,
            )

        val result =
            validators.deviceAuth.verifyDeviceAuth(
                document = signedDocument,
                expectedSessionTranscript = tamperedTranscript,
            )

        assertTrue(
            result.error && result.critical,
            "DeviceAuth verification should fail for $description, got: error=${result.error} critical=${result.critical} message=${result.message}",
        )
    }

    private data class Validators(
        val mdoc: MdocValidationsImpl,
        val deviceAuth: DeviceAuthValidationImpl,
    )

    private fun buildValidators(issuerCertChain: Array<String>): Validators {
        // Trust the issuer's self-signed cert so the X509 chain step accepts it. Without this
        // the cert-chain check would correctly reject a self-signed CA, masking everything else.
        val x509Service = X509VerifyServiceImpl()
        x509Service.setTrustedCerts(issuerCertChain)

        val mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl()
        val sessionTranscriptCborCodec = SessionTranscriptCborCodecImpl()
        val coseCryptoService = CoseCryptoServiceImpl()

        val issuerAuthValidation =
            IssuerAuthValidationImpl(
                x509VerifyService = x509Service,
                coseCryptoService = coseCryptoService,
                mobileSecurityObjectCborCodec = mobileSecurityObjectCborCodec,
            )

        val mdocValidations = MdocValidationsImpl(issuerAuthValidation = issuerAuthValidation)

        val deviceAuthValidation =
            DeviceAuthValidationImpl(
                coseCryptoService = coseCryptoService,
                sessionTranscriptCborCodec = sessionTranscriptCborCodec,
                mobileSecurityObjectCborCodec = mobileSecurityObjectCborCodec,
            )

        return Validators(mdoc = mdocValidations, deviceAuth = deviceAuthValidation)
    }

    private data class SignedHolderDocument(
        val document: com.sphereon.mdoc.data.device.Document,
        val issuerCertChain: Array<String>,
        val deviceKeyInfo: ManagedKeyInfoType<CoseKeyType>,
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun buildSignedHolderDocument(): SignedHolderDocument {
        val issued = createIssuerKeyWithCertificate()
        val issuerKeyInfo = issued.keyInfo
        val deviceKeyInfo = createDeviceKey()

        val now: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        val item1 =
            IssuerSignedItem.create(
                digestID = DigestID(1u),
                elementIdentifier = DataElementIdentifier("given_name"),
                elementValue = "Jane",
            )
        val item2 =
            IssuerSignedItem.create(
                digestID = DigestID(2u),
                elementIdentifier = DataElementIdentifier("family_name"),
                elementValue = "Doe",
            )

        val builder =
            IssuerSigned
                .MsoBuilder(issuerSignedItemCborCodec = IssuerSignedItemCborCodecImpl())
                .withDocType(mdlDocType)
                .withDeviceKeyInfo(deviceKeyInfo)
                .withSigningKeyInfo(issuerKeyInfo)
                .withSigned(now)
                .withValidFrom(now)
                .withValidUntil(validUntil)

        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>, item2 as IssuerSignedItem<Any>)

        val issuedDocument =
            builder.buildAndSignMdoc(
                mdocSignService = mdocSignService,
                requireDeviceX5Chain = false,
            )

        // Holder side: build the OID4VP DeviceAuthentication and sign with the device key.
        val deviceNamespaces =
            DeviceNameSpaces(
                mapOf(
                    mdlNamespace to DeviceSignedItems(emptyMap()),
                ),
            )

        val deviceAuthentication =
            com.sphereon.mdoc.data.device.DeviceAuthentication.fromOid4vp(
                clientId = verifierClientId,
                nonce = authRequestNonce,
                jwkThumbprint = null,
                responseUri = verifierResponseUri,
                docType = mdlDocType,
                deviceNamespaces = deviceNamespaces,
            )

        val docRequest =
            DocRequest(
                itemsRequest =
                    DeviceItemsRequest(
                        docType = mdlDocType,
                        nameSpaces =
                            mapOf(
                                mdlNamespace to
                                    mapOf(
                                        DataElementIdentifier("given_name") to IntentToRetain(false),
                                        DataElementIdentifier("family_name") to IntentToRetain(false),
                                    ),
                            ),
                    ),
            )

        val signedDocument =
            mdocSignService.deviceSignDocument(
                request = docRequest,
                document = issuedDocument,
                deviceAuthentication = deviceAuthentication,
                deviceKeyInfo = deviceKeyInfo,
                requireDeviceX5Chain = false,
            )

        return SignedHolderDocument(document = signedDocument, issuerCertChain = issued.certChain, deviceKeyInfo = deviceKeyInfo)
    }

    private fun encodeExpectedDeviceAuthenticationPayload(
        sessionTranscript: SessionTranscript,
        docType: String,
        deviceNamespaces: DeviceNameSpaces,
    ): ByteArray {
        val sessionTranscriptItem: CborItem<*> =
            Cbor.tryDecode(SessionTranscriptCborCodecImpl().encode(sessionTranscript).getOrThrow()).getOrThrow()
        return Cbor.encode(
            CborArray(
                mutableListOf(
                    CborString("DeviceAuthentication"),
                    sessionTranscriptItem,
                    CborString(docType),
                    CborEncodedItem<CborMap<CborString, CborMap<CborString, CborItem<*>>>>(
                        Cbor.encode(
                            CborMap(
                                deviceNamespaces.value.entries
                                    .associate { (namespace, items) ->
                                        CborString(namespace.toString()) to
                                            CborMap(
                                                items.value.entries
                                                    .associate { (identifier, value) ->
                                                        CborString(identifier.toString()) to value.toCborItem()
                                                    }.toMutableMap(),
                                            )
                                    }.toMutableMap(),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private data class IssuerKeyMaterial(
        val keyInfo: ManagedKeyInfoType<CoseKeyType>,
        val certChain: Array<String>,
    )

    private suspend fun createIssuerKeyWithCertificate(): IssuerKeyMaterial {
        val issuerKeyPair =
            ctx.kms.generateKeyAsync(
                alias = "test-issuer-key-${Uuid.v4String()}",
                alg = SignatureAlgorithm.ECDSA_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
                providerId = null,
            )
        val issuerKeyInfo = issuerKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)

        val issuerDn =
            X509DistinguishedNameElements(
                commonName = "Test mDL Issuer",
                organizationName = "Test DMV",
                organizationUnit = "Driver Licensing",
                country = "US",
            )

        val certResult =
            ctx.certificateService.createCertificate(
                issuerKeyInfo = issuerKeyInfo,
                issuer = issuerDn,
                subjectKeyInfo = issuerKeyInfo,
                subject = issuerDn,
                serialNumber = 1,
            )

        val managed =
            ManagedKeyInfo(
                alias = issuerKeyInfo.alias,
                providerId = issuerKeyInfo.providerId,
                resolvedKeyInfo = certResult.certificate.amendCoseKeyInfo(issuerKeyInfo),
            )
        // The signing path picks up the chain from `key.getX509CertificateChain()`; the
        // verifier's trust anchor comparison wants the same base64 cert. Pull it directly
        // from the cert result so the test doesn't depend on x5c being delegated up to the
        // ManagedKeyInfo wrapper.
        val certChain = arrayOf(certResult.certificate.derToBase64())
        return IssuerKeyMaterial(keyInfo = managed, certChain = certChain)
    }

    private suspend fun createDeviceKey(): ManagedKeyInfoType<CoseKeyType> {
        val devicePair =
            ctx.kms.generateKeyAsync(
                alias = "test-device-key-${Uuid.v4String()}",
                alg = SignatureAlgorithm.ECDSA_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
                providerId = null,
            )
        return devicePair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)
    }

    @Suppress("UNCHECKED_CAST")
    private fun tamperFirstIssuerSignedItem(document: com.sphereon.mdoc.data.device.Document): com.sphereon.mdoc.data.device.Document {
        val nameSpaces =
            document.issuerSigned.nameSpaces
                ?: error("Document has no issuer-signed namespaces; cannot tamper")
        val (firstNs, firstItems) = nameSpaces.entries.first()
        require(firstItems.isNotEmpty()) { "Namespace '$firstNs' has no items to tamper with" }

        val originalItem = firstItems[0].data() as IssuerSignedItem<Any>

        // Swap the disclosed value to something different. The MSO still has the original
        // digest, so the verifier's hash-vs-MSO check must fail.
        val tamperedItem =
            IssuerSignedItem.create(
                digestID = originalItem.digestID,
                elementIdentifier = originalItem.elementIdentifier,
                elementValue = "TAMPERED-${originalItem.elementValue}",
            )

        val codec = IssuerSignedItemCborCodecImpl()
        val tamperedEncoded = codec.encodeItem(tamperedItem as IssuerSignedItem<Any>).getOrThrow()

        val newFirstItems = firstItems.copyOf()
        newFirstItems[0] = tamperedEncoded
        val tamperedNamespaces = nameSpaces.toMutableMap()
        tamperedNamespaces[firstNs] = newFirstItems

        return document.copy(
            issuerSigned =
                document.issuerSigned.copy(
                    nameSpaces = tamperedNamespaces,
                    original = null,
                ),
        )
    }

    private fun describeFailures(verifications: Array<out com.sphereon.crypto.core.generic.VerifyResultType>): String {
        val failures =
            verifications
                .filter { it.error && it.critical }
                .joinToString("; ") { "${it.name}: ${it.message ?: "(no message)"}" }
        return if (failures.isEmpty()) "(no critical failures reported)" else "Failures: $failures"
    }
}
