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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Ok
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseCryptoProviderToCallbackAdapter
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.core.jose.tryGenerateJwkThumbprint
import com.sphereon.crypto.kms.CertificateServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofOutput
import com.sphereon.crypto.dataintegrity.command.AddProofServiceCommand
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.MdocSignServiceImpl
import com.sphereon.mdoc.SessionTranscriptCborCodecImpl
import com.sphereon.mdoc.data.DeviceAuthValidationImpl
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceResponseCborCodecImpl
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.IssuerSignedItemCborCodecImpl
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.oid4vp.MdocOid4vpServiceImpl
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.common.vpToken
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test for the `mso_mdoc` branch of [CreateAuthorizationResponseCommandImpl].
 *
 * Issues a REAL ISO 18013-5 mdoc bound to a holder-controlled device key, hands the stored
 * `IssuerSigned` to the command, and asserts the command produces a genuine ISO 18013-7
 * `DeviceResponse` (`{version, documents, status}` with a `DeviceAuth` COSE_Sign1) — NOT the
 * stored `IssuerSigned` passed through verbatim (the prior bug). The DeviceAuth is signed over the
 * OID4VP §B.2.6 OpenID4VPHandover SessionTranscript built from `client_id` + `nonce` +
 * `response_uri`, exactly as the verifier reconstructs it.
 */
class CreateAuthorizationResponseCommandImplMdocTest {
    private val docType = DocType("org.acme.businesscard.1")
    private val docTypeNamespace = NameSpace("org.acme.businesscard.1")

    @DependencyGraph(AppScope::class)
    abstract class TestHolderMdocAppGraph : AbstractAppGraph() {
        @DependencyGraph.Factory
        fun interface Factory {
            fun create(
                @Provides application: Any,
                @Provides @Named("appId") appId: String,
                @Provides @Named("profile") profile: String,
                @Provides @Named("version") version: String,
                @Provides rootScopeProvider: RootScopeProvider,
            ): TestHolderMdocAppGraph
        }
    }

    private class Setup(
        testInstance: Any
    ) {
        val app: AbstractAppGraph =
            createGraphFactory<TestHolderMdocAppGraph.Factory>()
                .create(
                    application = testInstance,
                    appId = "test-holder-mdoc",
                    profile = "test",
                    version = "1.0.0-test",
                    rootScopeProvider = DefaultRootScopeProvider(),
                ).also { it.initRootScopeProvider() }

        val userContext = app.userContextManager.getAnonymous()
        val sessionContext = userContext.sessionContextManager.createOrGetFromId("holder-mdoc-${Uuid.v4String()}", principalType = com.sphereon.di.context.PrincipalType.USER)
        val execution: SessionExecution = sessionContext.asCoreApiServiceGraph().serviceExecution
        val kms: KeyManagerService = sessionContext.graph.asKeyManagerServiceGraph().keyManagerService
        val certificateService: CertificateServiceImpl
        val mdocSignService: MdocSignService

        init {
            val factory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
            val provider = factory.create(SoftwareKmsProviderConfig(id = "test-software"), sessionContext.sessionExecution)
            kms.registerProvider(provider, makeDefaultKms = true)
            DefaultCallbacks.setCoseCryptoDefault(
                CoseCryptoProviderToCallbackAdapter(keyManagerServiceProvider = { kms }),
            )
            certificateService = CertificateServiceImpl(keyManagerService = kms)
            mdocSignService =
                MdocSignServiceImpl(
                    coseCryptoService = CoseCryptoServiceImpl(),
                    execution = execution,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                    sessionTranscriptCborCodec = SessionTranscriptCborCodecImpl(),
                )
        }
    }

    private suspend fun issuerKeyWithCert(setup: Setup): ManagedKeyInfoType<CoseKeyType> {
        val issuerKeyPair =
            setup.kms.generateKeyAsync(
                alias = "test-mdoc-issuer-${Uuid.v4String()}",
                alg = SignatureAlgorithm.ECDSA_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
                providerId = null,
            )
        val issuerKeyInfo = issuerKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)
        val dn =
            X509DistinguishedNameElements(
                commonName = "Acme Business Card Issuer",
                organizationName = "Acme",
                country = "US",
            )
        val cert =
            setup.certificateService.createCertificate(
                issuerKeyInfo = issuerKeyInfo,
                issuer = dn,
                subjectKeyInfo = issuerKeyInfo,
                subject = dn,
                serialNumber = 1,
            )
        return ManagedKeyInfo(
            alias = issuerKeyInfo.alias,
            providerId = issuerKeyInfo.providerId,
            resolvedKeyInfo = cert.certificate.amendCoseKeyInfo(issuerKeyInfo),
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `holder builds a real mdoc DeviceResponse with DeviceAuth`() =
        runTest {
            val setup = Setup(this)
            val command =
                CreateAuthorizationResponseCommandImpl(
                    execution = setup.execution,
                    holderJwtVpSigningProvider =
                        JwtServiceHolderJwtVpSigningProvider((setup.sessionContext.graph as JwtServiceImpl.Graph).jwtService),
                    addProofServiceCommand = UnusedAddProofCommand,
                    mdocOid4vpService =
                        MdocOid4vpServiceImpl(
                            signService = setup.mdocSignService,
                            logService = setup.execution.log,
                            mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                        ),
                    issuerSignedCborCodec = IssuerSignedCborCodecImpl(),
                    deviceResponseCborCodec = DeviceResponseCborCodecImpl(),
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                )

            // 1. The holder generates a device key it controls (PRIVATE: it retains the private
            //    half so it can later sign DeviceAuth). The PUBLIC half is bound into the MSO.
            //    Faithful to the real wallet `createHolderKey()`: no explicit alias, so the KMS
            //    keys it under its kid (RFC 7638 thumbprint) and the holder references it by kid.
            val deviceKeyPair =
                setup.kms.generateKeyAsync(
                    alias = null,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                    providerId = null,
                )
            val deviceKeyInfo = deviceKeyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PUBLIC, KeyEncoding.COSE)

            // 2. The issuer signs an mdoc binding that device key into the MSO deviceKeyInfo.
            val issuerKeyInfo = issuerKeyWithCert(setup)
            val now = DateTimeUtils.DEFAULTS.dateTimeLocal()
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
            val issuerSigned =
                IssuerSigned
                    .MsoBuilder(issuerSignedItemCborCodec = IssuerSignedItemCborCodecImpl())
                    .withDocType(docType)
                    .withDeviceKeyInfo(deviceKeyInfo)
                    .withSigningKeyInfo(issuerKeyInfo)
                    .withSigned(now)
                    .withValidFrom(now)
                    .withValidUntil(now)
                    .also { it.addNameSpace(docTypeNamespace, item1 as IssuerSignedItem<Any>, item2 as IssuerSignedItem<Any>) }
                    .buildAndSign(mdocSignService = setup.mdocSignService, requireDeviceX5Chain = false)

            // OID4VCI mso_mdoc credential wire form: base64url(CBOR(IssuerSigned)).
            val storedCredential = IssuerSignedCborCodecImpl().encode(issuerSigned).getOrThrow().encodeToBase64Url()

            // 3. The verifier's request (the SAME client_id/nonce/response_uri the verifier will
            //    use to reconstruct the SessionTranscript).
            val resolvedRequest = resolvedRequest()
            val selected =
                listOf(
                    SelectedCredential(
                        credentialQueryId = "businesscard_mdoc",
                        credentialId = "mdoc-1",
                        presentation = JsonPrimitive(storedCredential),
                        credentialFormat = CredentialFormat.MSO_MDOC,
                        holderKeyRef = deviceKeyPair.kid ?: deviceKeyPair.alias,
                    ),
                )

            val result = command.execute(CreateAuthorizationResponseArgs(resolvedRequest, selected))
            if (result.isErr) error("command failed: ${result.error.message.defaultMessage}")
            assertIs<Ok<*>>(result)
            val response = result.getOrThrow()
            val vpToken = response.vpToken
            assertNotNull(vpToken)
            val wireVpToken = assertIs<JsonObject>(response.additionalParameters["vp_token"])
            assertEquals(1, assertIs<JsonArray>(wireVpToken["businesscard_mdoc"]).size)

            val presentation = vpToken.getSinglePresentation("businesscard_mdoc")
            assertNotNull(presentation)
            // The presentation must NOT be the stored IssuerSigned passed through verbatim.
            assertTrue(presentation != storedCredential, "holder must not pass the stored IssuerSigned through verbatim")

            // 4. Decode the produced vp_token entry and assert it is a real DeviceResponse with a
            //    DeviceAuth COSE_Sign1 over the device key (i.e. the holder built + signed it).
            val deviceResponseBytes = presentation.decodeFromBase64Url()
            val deviceResponse = DeviceResponseCborCodecImpl().decode(deviceResponseBytes).getOrThrow().value
            val documents = deviceResponse.documents
            assertNotNull(documents)
            assertEquals(1, documents.size)
            val doc = documents.first()
            assertEquals(docType, doc.docType)
            assertNotNull(doc.deviceSigned, "DeviceResponse document must carry deviceSigned (DeviceAuth)")
            assertNotNull(doc.deviceSigned!!.deviceAuth.deviceSignature, "DeviceAuth must carry a device signature")
            // The disclosed issuer-signed elements survive.
            assertNotNull(doc.issuerSigned.getIssuerSignedItem(docTypeNamespace, DataElementIdentifier("given_name")))

            // 5. For an encrypted response the holder must bind DeviceAuth to the exact
            // verifier JWK selected for the response JWE. This is the OpenID4VP 1.0 final
            // B.2.6 slot that used to be signed as null and caused verifier rejection.
            val verifierEncryptionJwk =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "0_3S7HedSywaxlekdt6Or8pkcR13hQaCPMqt9cuZBVc",
                    y = "ZVXSCL3HlnMQWKrwMyIAe5wsAIWd3Eu1misKFr3POdA",
                    use = "enc",
                    alg = JwaAlgorithm.ECDH_ES,
                    kid = "verifier-encryption-key",
                )
            val encryptedRequest =
                resolvedRequest(
                    responseMode = ResponseMode.DIRECT_POST_JWT,
                    clientMetadata =
                        ClientMetadata(
                            jwks = JwkSet(arrayOf(verifierEncryptionJwk)),
                            encryptedResponseEncValuesSupported = listOf("A128GCM"),
                        ),
                )
            val encryptedResult = command.execute(CreateAuthorizationResponseArgs(encryptedRequest, selected))
            if (encryptedResult.isErr) error("encrypted command failed: ${encryptedResult.error.message.defaultMessage}")
            val encryptedPresentation =
                encryptedResult
                    .getOrThrow()
                    .vpToken
                    ?.getSinglePresentation("businesscard_mdoc")
            assertNotNull(encryptedPresentation)
            val encryptedDocument =
                DeviceResponseCborCodecImpl()
                    .decode(encryptedPresentation.decodeFromBase64Url())
                    .getOrThrow()
                    .value
                    .documents
                    ?.single()
            assertNotNull(encryptedDocument)

            val verifierThumbprint =
                tryGenerateJwkThumbprint(verifierEncryptionJwk)
                    .getOrThrow()
                    .decodeFromBase64Url()
            val expectedTranscript =
                SessionTranscript.fromOid4vpClientIdAndResponseUri(
                    clientId = encryptedRequest.request.clientId,
                    nonce = encryptedRequest.request.nonce!!,
                    jwkThumbprint = verifierThumbprint,
                    responseUri = encryptedRequest.request.responseUri!!,
                )
            val deviceAuthValidation =
                DeviceAuthValidationImpl(
                    coseCryptoService = CoseCryptoServiceImpl(),
                    sessionTranscriptCborCodec = SessionTranscriptCborCodecImpl(),
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                )
            val correctBinding =
                deviceAuthValidation.verifyDeviceAuth(
                    document = encryptedDocument,
                    expectedSessionTranscript = expectedTranscript,
                )
            assertFalse(correctBinding.error, "encrypted DeviceAuth must verify with the verifier JWK thumbprint: ${correctBinding.message}")

            val oldNullBinding =
                deviceAuthValidation.verifyDeviceAuth(
                    document = encryptedDocument,
                    expectedSessionTranscript =
                        SessionTranscript.fromOid4vpClientIdAndResponseUri(
                            clientId = encryptedRequest.request.clientId,
                            nonce = encryptedRequest.request.nonce!!,
                            jwkThumbprint = null,
                            responseUri = encryptedRequest.request.responseUri!!,
                        ),
                )
            assertTrue(oldNullBinding.error, "encrypted DeviceAuth must reject the old null-thumbprint transcript")
        }

    private fun resolvedRequest(
        responseMode: ResponseMode = ResponseMode.DIRECT_POST,
        clientMetadata: ClientMetadata? = null,
    ): ResolvedOid4vpRequest {
        val authRequest =
            AuthorizationRequest(
                clientId = "https://verifier.acme.example",
                redirectUri = "https://verifier.acme.example/callback",
                responseType = "vp_token",
                responseMode = responseMode.value,
                scope = null,
                state = "test-state",
                nonce = "test-nonce",
                additionalParameters =
                    mapOf(
                        "response_uri" to JsonPrimitive("https://verifier.acme.example/oid4vp/auth/response"),
                        "response_mode" to JsonPrimitive(responseMode.value),
                    ),
            )
        return ResolvedOid4vpRequest(
            request = authRequest,
            dcqlQuery = null,
            clientMetadata = clientMetadata,
            verifierInfo =
                VerifierInfo(
                    clientId = "https://verifier.acme.example",
                    clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                    displayName = "Acme Verifier",
                ),
        )
    }

}

private object UnusedAddProofCommand : AddProofServiceCommand {
    override val inputTypeToken: TypeToken<AddProofInput> = typeToken<AddProofInput>()
    override val outputTypeToken: TypeToken<AddProofOutput> = typeToken<AddProofOutput>()
    override val isEnabled: Boolean = true
    override suspend fun execute(args: AddProofInput): IdkResult<AddProofOutput, IdkError> =
        error("Data Integrity proof creation is not used by the mdoc test")
}
