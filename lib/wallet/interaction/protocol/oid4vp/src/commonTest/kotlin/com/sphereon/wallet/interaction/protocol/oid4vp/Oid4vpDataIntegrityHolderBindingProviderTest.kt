/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.dataintegrity.ecdsardfc2019.EcdsaRdfc2019Cryptosuite
import com.sphereon.crypto.dataintegrity.eddsajcs2022.EddsaJcs2022Cryptosuite
import com.sphereon.crypto.dataintegrity.eddsardfc2022.EddsaRdfc2022Cryptosuite
import com.sphereon.jsonld.loader.BuiltInContextLinkedDataDocumentLoader
import com.sphereon.jsonld.loader.DefaultBuiltInContextRegistry
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.VpFormatInfo
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueResult
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaClientAttestationAuthRequest
import com.sphereon.wallet.wsca.WscaClientAttestationAuthResult
import com.sphereon.wallet.wsca.WscaDpopProofRequest
import com.sphereon.wallet.wsca.WscaDpopProofResult
import com.sphereon.wallet.wsca.WscaPreparedSigning
import com.sphereon.wallet.wsca.WscaPreparedSigningFactory
import com.sphereon.wallet.wsca.WscaSigningRequest
import com.sphereon.wallet.wsca.WscaUserAuthentication
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Oid4vpDataIntegrityHolderBindingProviderTest {
    @Test
    fun jwtVpSignerUsesWscaAndAttendedOperationBindingWithoutAliasKid() = runTest {
        val wsca = RecordingWsca()
        val result = SecureComponentOid4vpJwtVpSigningProvider(wsca).sign(
            HolderJwtVpSigningRequest(
                walletUnitId = "wallet-1",
                payload = buildJsonObject { put("nonce", "request-nonce") },
                keyReference = "opaque-holder-alias",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                identifier = HolderJwtVpSigningIdentifier.JwksKid("https://holder.example/jwks#key-1"),
                protectedHeader = buildJsonObject { put("typ", "JWT") },
                operationBinding = "attended-operation-1",
            ),
        )

        assertTrue(result.isOk)
        assertEquals(listOf("opaque-holder-alias"), wsca.ensureAliases)
        assertContentEquals(listOf(SignatureAlgorithm.ECDSA_SHA256), wsca.ensureAlgorithms)
        assertEquals(1, wsca.signCalls.size)
        assertEquals("wallet-1", wsca.signCalls.single().walletUnitId)
        assertEquals("attended-operation-1", wsca.signCalls.single().operationBinding)
        val header =
            kotlinx.serialization.json.Json
                .parseToJsonElement(
                    result.value.compactJws
                        .substringBefore('.')
                        .decodeFromBase64Url()
                        .decodeToString(),
                ).toString()
        assertTrue(header.contains("https://holder.example/jwks#key-1"))
        assertTrue(!header.contains("opaque-holder-alias"))
    }

    @Test
    fun jwtVpSignerPassesAndEmitsTheNegotiatedNonDefaultAlgorithm() = runTest {
        val wsca = RecordingWsca()
        val result = SecureComponentOid4vpJwtVpSigningProvider(wsca).sign(
            HolderJwtVpSigningRequest(
                walletUnitId = "wallet-1",
                payload = buildJsonObject { put("nonce", "request-nonce") },
                keyReference = "opaque-holder-alias",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA384,
                identifier = HolderJwtVpSigningIdentifier.JwksKid("https://holder.example/jwks#key-1"),
                protectedHeader = buildJsonObject { put("typ", "JWT") },
                operationBinding = "attended-operation-1",
            ),
        )

        assertTrue(result.isOk)
        assertContentEquals(listOf(SignatureAlgorithm.ECDSA_SHA384), wsca.ensureAlgorithms)
        assertEquals(1, wsca.signCalls.size)
        assertEquals("ES384", wsca.signCalls.single().keyRef.algorithm)
        val header =
            kotlinx.serialization.json.Json.parseToJsonElement(
                result.value.compactJws.substringBefore('.').decodeFromBase64Url().decodeToString(),
            ).jsonObject
        assertEquals("ES384", header["alg"]!!.jsonPrimitive.content)
    }

    @Test
    fun jwtVpSignerRejectsWscaKeyAlgorithmMismatchBeforeSigning() = runTest {
        val wsca = RecordingWsca(reportedAlgorithm = "ES256")
        val result = SecureComponentOid4vpJwtVpSigningProvider(wsca).sign(
            HolderJwtVpSigningRequest(
                walletUnitId = "wallet-1",
                payload = buildJsonObject { put("nonce", "request-nonce") },
                keyReference = "opaque-holder-alias",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA384,
                identifier = HolderJwtVpSigningIdentifier.JwksKid("https://holder.example/jwks#key-1"),
                protectedHeader = buildJsonObject { put("typ", "JWT") },
                operationBinding = "attended-operation-1",
            ),
        )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("reports algorithm") == true)
        assertTrue(wsca.signCalls.isEmpty(), "a mismatched WSCA key must be rejected before signing")
    }

    @Test
    fun rejectsWscaKeyAlgorithmMismatchBeforeSigning() = runTest {
        val wsca = RecordingWsca(reportedAlgorithm = "ES256")
        val result =
            dataIntegrityProvider(wsca, explicitAlgorithmResolver(SignatureAlgorithm.ECDSA_SHA384)).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EcdsaRdfc2019Cryptosuite.ID),
                    credential = selectedCredential("credential-mismatch", "https://issuer.example/vc/mismatch", "v2")
                        .copy(dataIntegrityCryptosuite = EcdsaRdfc2019Cryptosuite.ID),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("reports algorithm") == true)
        assertContentEquals(listOf(SignatureAlgorithm.ECDSA_SHA384), wsca.ensureAlgorithms)
        assertTrue(wsca.signCalls.isEmpty(), "a mismatched WSCA key must be rejected before signing")
    }

    @Test
    fun bindsOneAggregateVpWithVerifierDomainAndRequestChallenge() = runTest {
        val wsca = RecordingWsca()
        val provider = dataIntegrityProvider(wsca)
        val result =
            provider.applyHolderBinding(
                Oid4vpDataIntegrityHolderBindingRequest(
                    walletUnitId = "wallet-1",
                    operationBinding = "attended-operation-1",
                    request = resolvedRequest(nonce = "request-nonce"),
                    selectedCredentials =
                        listOf(
                            selectedCredential("credential-1", "https://issuer.example/vc/1", "v2"),
                            selectedCredential("credential-2", "https://issuer.example/vc/2", "v2"),
                        ),
                ),
            )

        assertTrue(result.isOk)
        val bound = result.value.selectedCredentials
        assertEquals(2, bound.size)
        assertEquals(2, wsca.signCalls.size)
        assertContentEquals(listOf("holder-key-credential-1", "holder-key-credential-2"), wsca.ensureAliases)
        val vp = result.value.preparedPresentations.single().presentation
        bound.forEach { selected ->
            assertTrue(selected.presentation.jsonObject["type"]!!.jsonArray.any { it.jsonPrimitive.content == "VerifiableCredential" })
        }
        assertEquals("https://holder.example/id", vp["holder"]!!.jsonPrimitive.content)
        assertEquals("VerifiablePresentation", vp["type"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(2, vp["verifiableCredential"]!!.jsonArray.size)
        val proofs = vp["proof"]!!.jsonArray
        assertEquals(2, proofs.size)
        proofs.forEach { proof ->
            assertEquals("eddsa-jcs-2022", proof.jsonObject["cryptosuite"]!!.jsonPrimitive.content)
            assertEquals("https://verifier.example/client", proof.jsonObject["domain"]!!.jsonPrimitive.content)
            assertEquals("request-nonce", proof.jsonObject["challenge"]!!.jsonPrimitive.content)
            assertEquals("https://holder.example/jwks#key-1", proof.jsonObject["verificationMethod"]!!.jsonPrimitive.content)
            assertEquals(64, EddsaJcs2022Cryptosuite.decodeProofValue(proof.jsonObject["proofValue"]!!.jsonPrimitive.content).size)
        }
        assertTrue(wsca.signCalls.all { it.operationBinding == "attended-operation-1" })
        assertTrue(wsca.signCalls.all { it.walletUnitId == "wallet-1" })
        assertTrue(wsca.ensureAlgorithms.all { it == SignatureAlgorithm.ED25519 })
    }

    @Test
    fun createsOneAggregateVpAndOneProofPerDistinctResolvedHolderBinding() = runTest {
        val wsca = RecordingWsca()
        val first = selectedCredential("credential-a", "https://issuer.example/vc/a", "v2")
            .copy(
                holderKeyRef = "holder-key-a",
                holderId = "https://holder.example/a",
                holderVerificationMethod = "https://holder.example/jwks#a",
            )
        val second = selectedCredential("credential-b", "https://issuer.example/vc/b", "v2")
            .copy(
                holderKeyRef = "holder-key-b",
                holderId = "https://holder.example/b",
                holderVerificationMethod = "https://holder.example/jwks#b",
            )

        val result = dataIntegrityProvider(wsca).applyHolderBinding(
            Oid4vpDataIntegrityHolderBindingRequest(
                walletUnitId = "wallet-1",
                operationBinding = "attended-operation-1",
                request = resolvedRequest(nonce = "request-nonce"),
                selectedCredentials = listOf(first, second),
            ),
        )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertEquals(2, wsca.signCalls.size, "one proof must be signed for each distinct resolved binding")
        val bound = result.value.selectedCredentials
        assertEquals(2, bound.size)
        assertEquals(1, result.value.preparedPresentations.size)
        val vp = result.value.preparedPresentations.single().presentation
        assertTrue(vp["holder"] == null, "different resolved holders must not be collapsed into one holder")
        assertEquals(2, vp["verifiableCredential"]!!.jsonArray.size)
        val proofs = vp["proof"]!!.jsonArray
        assertEquals(2, proofs.size)
        assertEquals(
            setOf("https://holder.example/jwks#a", "https://holder.example/jwks#b"),
            proofs.map { it.jsonObject["verificationMethod"]!!.jsonPrimitive.content }.toSet(),
        )
        assertTrue(proofs.all { it.jsonObject["domain"]!!.jsonPrimitive.content == "https://verifier.example/client" })
        assertTrue(proofs.all { it.jsonObject["challenge"]!!.jsonPrimitive.content == "request-nonce" })
    }

    @Test
    fun deduplicatesOnlyCredentialsUsingTheSameResolvedKeyAndController() = runTest {
        val wsca = RecordingWsca(resolvedKeyId = "resolved-key-1")
        val first = selectedCredential("credential-a", "https://issuer.example/vc/a", "v2")
            .copy(holderKeyRef = "alias-a", holderVerificationMethod = "https://holder.example/jwks#key-1")
        val second = selectedCredential("credential-b", "https://issuer.example/vc/b", "v2")
            .copy(holderKeyRef = "alias-b", holderVerificationMethod = "https://holder.example/jwks#key-1")

        val result = dataIntegrityProvider(wsca).applyHolderBinding(
            Oid4vpDataIntegrityHolderBindingRequest(
                walletUnitId = "wallet-1",
                operationBinding = "attended-operation-1",
                request = resolvedRequest(nonce = "request-nonce"),
                selectedCredentials = listOf(first, second),
            ),
        )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertEquals(2, wsca.ensureAliases.size, "each credential still resolves its explicit alias")
        assertEquals(1, wsca.signCalls.size, "proofs dedupe by resolved key identity, not the aliases")
        assertEquals(1, result.value.preparedPresentations.single().presentation["proof"]!!.jsonArray.size)
    }

    @Test
    fun keepsMixedVcdmVersionsInSeparatePreparedPresentations() = runTest {
        val wsca = RecordingWsca()
        val result = dataIntegrityProvider(wsca).applyHolderBinding(
            Oid4vpDataIntegrityHolderBindingRequest(
                walletUnitId = "wallet-1",
                operationBinding = "attended-operation-1",
                request = resolvedRequest(nonce = "request-nonce"),
                selectedCredentials = listOf(
                    selectedCredential("credential-v1", "https://issuer.example/vc/v1", "v1"),
                    selectedCredential("credential-v2", "https://issuer.example/vc/v2", "v2"),
                ),
            ),
        )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertEquals(2, result.value.preparedPresentations.size)
        assertEquals(2, wsca.signCalls.size)
        assertEquals(1, result.value.preparedPresentations[0].presentation["verifiableCredential"]!!.jsonArray.size)
        assertEquals(1, result.value.preparedPresentations[1].presentation["verifiableCredential"]!!.jsonArray.size)
    }

    @Test
    fun selectsEddsaRdfc2022AndUsesProductionRdfcTransformWithWscaEd25519() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EddsaRdfc2022Cryptosuite.ID),
                    credential = selectedCredential("credential-rdfc", "https://issuer.example/vc/rdfc", "v2")
                        .copy(dataIntegrityCryptosuite = EddsaRdfc2022Cryptosuite.ID),
                ),
            )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertContentEquals(listOf(SignatureAlgorithm.ED25519), wsca.ensureAlgorithms)
        assertEquals(64, wsca.signCalls.single().signingInput.size)
        val proofValue = result.value.preparedPresentations.single().presentation["proof"]!!.jsonObject["proofValue"]!!.jsonPrimitive.content
        assertEquals(64, EddsaRdfc2022Cryptosuite.decodeProofValue(proofValue).size)
    }

    @Test
    fun selectsEcdsaRdfc2019P256FromExplicitKeyMetadataAndEncodesRawP1363Proof() = runTest {
        val wsca = RecordingWsca()
        val resolver = explicitAlgorithmResolver(SignatureAlgorithm.ECDSA_SHA256)
        val result =
            dataIntegrityProvider(wsca, resolver).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EcdsaRdfc2019Cryptosuite.ID),
                    credential = selectedCredential("credential-p256", "https://issuer.example/vc/p256", "v2")
                        .copy(dataIntegrityCryptosuite = EcdsaRdfc2019Cryptosuite.ID),
                ),
            )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertContentEquals(listOf(SignatureAlgorithm.ECDSA_SHA256), wsca.ensureAlgorithms)
        assertEquals(64, wsca.signCalls.single().signingInput.size)
        val proofValue = result.value.preparedPresentations.single().presentation["proof"]!!.jsonObject["proofValue"]!!.jsonPrimitive.content
        assertEquals(
            64,
            EcdsaRdfc2019Cryptosuite.decodeProofValue(
                proofValue,
                com.sphereon.crypto.core.jose.JwaCurve.P_256,
            ).size,
        )
    }

    @Test
    fun selectsEcdsaRdfc2019P384FromExplicitKeyMetadataAndEncodesRawP1363Proof() = runTest {
        val wsca = RecordingWsca()
        val resolver = explicitAlgorithmResolver(SignatureAlgorithm.ECDSA_SHA384)
        val result =
            dataIntegrityProvider(wsca, resolver).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EcdsaRdfc2019Cryptosuite.ID),
                    credential = selectedCredential("credential-p384", "https://issuer.example/vc/p384", "v2")
                        .copy(dataIntegrityCryptosuite = EcdsaRdfc2019Cryptosuite.ID),
                ),
            )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertContentEquals(listOf(SignatureAlgorithm.ECDSA_SHA384), wsca.ensureAlgorithms)
        assertEquals(96, wsca.signCalls.single().signingInput.size)
        val proofValue = result.value.preparedPresentations.single().presentation["proof"]!!.jsonObject["proofValue"]!!.jsonPrimitive.content
        assertEquals(
            96,
            EcdsaRdfc2019Cryptosuite.decodeProofValue(
                proofValue,
                com.sphereon.crypto.core.jose.JwaCurve.P_384,
            ).size,
        )
    }

    @Test
    fun productionAlgorithmResolverUsesExactSelectedWalletKeyMetadata() = runTest {
        val credential =
            selectedCredential("credential-p384", "https://issuer.example/vc/p384", "v2")
                .copy(holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA384)

        val result =
            Oid4vpDataIntegritySigningAlgorithmResolver.selectedCredentialMetadata.resolve(
                credential,
                EcdsaRdfc2019Cryptosuite.ID,
            )

        assertTrue(result.isOk)
        assertEquals(SignatureAlgorithm.ECDSA_SHA384, result.value)
    }

    @Test
    fun productionAlgorithmResolverFailsClosedWithoutSelectedWalletKeyMetadata() = runTest {
        val result =
            Oid4vpDataIntegritySigningAlgorithmResolver.selectedCredentialMetadata.resolve(
                selectedCredential("credential", "https://issuer.example/vc", "v2"),
                EcdsaRdfc2019Cryptosuite.ID,
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("explicit WSCA key algorithm metadata") == true)
    }

    @Test
    fun failsClosedWhenExplicitCredentialCryptosuiteIsAbsent() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EddsaJcs2022Cryptosuite.ID),
                    credential = selectedCredential("credential-1", "https://issuer.example/vc/1", "v2")
                        .copy(dataIntegrityCryptosuite = null),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("no explicit Data Integrity cryptosuite") == true)
        assertTrue(wsca.ensureAlgorithms.isEmpty())
        assertTrue(wsca.signCalls.isEmpty())
    }

    @Test
    fun omittedOptionalVerifierArraysUseExplicitCredentialCryptosuite() = runTest {
        val wsca = RecordingWsca()
        val credential =
            selectedCredential("credential-1", "https://issuer.example/vc/1", "v2")
                .copy(dataIntegrityCryptosuite = EddsaJcs2022Cryptosuite.ID)
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = null,
                    proofTypes = null,
                    credential = credential,
                ),
            )

        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertContentEquals(listOf(SignatureAlgorithm.ED25519), wsca.ensureAlgorithms)
        assertEquals(
            EddsaJcs2022Cryptosuite.ID,
            result.value.preparedPresentations.single().presentation["proof"]!!.jsonObject["cryptosuite"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun presentVerifierCryptosuiteAllowlistRejectsCredentialSuiteMismatch() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EddsaRdfc2022Cryptosuite.ID),
                    credential =
                        selectedCredential("credential-1", "https://issuer.example/vc/1", "v2")
                            .copy(dataIntegrityCryptosuite = EddsaJcs2022Cryptosuite.ID),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("not accepted by the verifier") == true)
        assertTrue(wsca.ensureAlgorithms.isEmpty())
    }

    @Test
    fun presentVerifierProofTypeAllowlistRejectsNonDataIntegrityProof() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EddsaJcs2022Cryptosuite.ID),
                    proofTypes = listOf("LegacySignatureProof"),
                    credential = selectedCredential("credential-1", "https://issuer.example/vc/1", "v2"),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("does not accept DataIntegrityProof") == true)
        assertTrue(wsca.ensureAlgorithms.isEmpty())
    }

    @Test
    fun failsClosedForUnsupportedCryptosuiteBeforeAnyWscaOperation() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf("unsupported-rdfc-2099"),
                    credential = selectedCredential("credential-1", "https://issuer.example/vc/1", "v2")
                        .copy(dataIntegrityCryptosuite = "unsupported-rdfc-2099"),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("Unsupported") == true)
        assertTrue(wsca.ensureAlgorithms.isEmpty())
        assertTrue(wsca.signCalls.isEmpty())
    }

    @Test
    fun failsClosedForEcdsaWithoutExplicitCurveMetadata() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                holderBindingRequest(
                    cryptosuites = listOf(EcdsaRdfc2019Cryptosuite.ID),
                    credential = selectedCredential("credential-1", "https://issuer.example/vc/1", "v2")
                        .copy(dataIntegrityCryptosuite = EcdsaRdfc2019Cryptosuite.ID),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("explicit P-256/ES256 or P-384/ES384") == true)
        assertTrue(wsca.ensureAlgorithms.isEmpty())
    }

    @Test
    fun failsClosedWhenOperationBindingIsMissing() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                Oid4vpDataIntegrityHolderBindingRequest(
                    walletUnitId = "wallet-1",
                    operationBinding = null,
                    request = resolvedRequest(nonce = "request-nonce"),
                    selectedCredentials = listOf(selectedCredential("credential-1", "https://issuer.example/vc/1", "v1")),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("operation binding") == true)
        assertTrue(wsca.signCalls.isEmpty())
    }

    @Test
    fun failsClosedWhenCredentialKeyReferenceIsMissing() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                Oid4vpDataIntegrityHolderBindingRequest(
                    walletUnitId = "wallet-1",
                    operationBinding = "attended-operation-1",
                    request = resolvedRequest(nonce = "request-nonce"),
                    selectedCredentials =
                        listOf(
                            selectedCredential("credential-1", "https://issuer.example/vc/1", "v1")
                                .copy(holderKeyRef = null),
                        ),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("holder key reference") == true)
        assertTrue(wsca.ensureAliases.isEmpty())
        assertTrue(wsca.signCalls.isEmpty())
    }

    @Test
    fun failsClosedWhenControlledVerificationMethodIsMissing() = runTest {
        val wsca = RecordingWsca()
        val result =
            dataIntegrityProvider(wsca).applyHolderBinding(
                Oid4vpDataIntegrityHolderBindingRequest(
                    walletUnitId = "wallet-1",
                    operationBinding = "attended-operation-1",
                    request = resolvedRequest(nonce = "request-nonce"),
                    selectedCredentials =
                        listOf(
                            selectedCredential("credential-1", "https://issuer.example/vc/1", "v1")
                                .copy(holderVerificationMethod = null),
                        ),
                ),
            )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("verificationMethod") == true)
        assertTrue(wsca.signCalls.isEmpty())
    }

    private fun resolvedRequest(
        nonce: String,
        cryptosuites: List<String>? = listOf(EddsaJcs2022Cryptosuite.ID),
        proofTypes: List<String>? = listOf("DataIntegrityProof"),
    ): ResolvedOid4vpRequest =
        ResolvedOid4vpRequest(
            request =
                AuthorizationRequest(
                    clientId = "https://verifier.example/client",
                    nonce = nonce,
                    responseType = "vp_token",
                ),
            verifierInfo =
                VerifierInfo(
                    clientId = "https://verifier.example/client",
                    clientIdScheme = ClientIdScheme.REDIRECT_URI,
                ),
            clientMetadata =
                ClientMetadata(
                    vpFormatsSupported =
                        mapOf(
                            CredentialFormat.LDP_VC.value to
                                VpFormatInfo(
                                    proofTypeValues = proofTypes,
                                    cryptosuiteValues = cryptosuites,
                                ),
                        ),
                ),
        )

    private fun holderBindingRequest(
        cryptosuites: List<String>?,
        credential: SelectedCredential,
        proofTypes: List<String>? = listOf("DataIntegrityProof"),
    ): Oid4vpDataIntegrityHolderBindingRequest =
        Oid4vpDataIntegrityHolderBindingRequest(
            walletUnitId = "wallet-1",
            operationBinding = "attended-operation-1",
            request =
                resolvedRequest(
                    nonce = "request-nonce",
                    cryptosuites = cryptosuites,
                    proofTypes = proofTypes,
                ),
            selectedCredentials = listOf(credential),
        )

    private fun explicitAlgorithmResolver(algorithm: SignatureAlgorithm): Oid4vpDataIntegritySigningAlgorithmResolver =
        Oid4vpDataIntegritySigningAlgorithmResolver { _, _ -> Ok(algorithm) }

    private fun dataIntegrityProvider(
        wsca: Wsca,
        algorithmResolver: Oid4vpDataIntegritySigningAlgorithmResolver =
            Oid4vpDataIntegritySigningAlgorithmResolver.strict,
    ): SecureComponentOid4vpDataIntegrityHolderBindingProvider =
        SecureComponentOid4vpDataIntegrityHolderBindingProvider(
            secureComponentCryptoSurface = wsca,
            linkedDataDocumentLoader =
                BuiltInContextLinkedDataDocumentLoader(DefaultBuiltInContextRegistry()),
            signingAlgorithmResolver = algorithmResolver,
        )

    private fun selectedCredential(id: String, issuer: String, version: String): SelectedCredential =
        SelectedCredential(
            credentialQueryId = "query-$id",
            credentialId = id,
            presentation =
                buildJsonObject {
                    putJsonArray("@context") {
                        add(
                            JsonPrimitive(
                                if (version == "v1") {
                                    "https://www.w3.org/2018/credentials/v1"
                                } else {
                                    "https://www.w3.org/ns/credentials/v2"
                                },
                            ),
                        )
                    }
                    putJsonArray("type") { add(JsonPrimitive("VerifiableCredential")) }
                    put("issuer", issuer)
                    putJsonObject("credentialSubject") { put("id", "https://subject.example/$id") }
                },
            credentialFormat = CredentialFormat.LDP_VC,
            holderKeyRef = "holder-key-$id",
            holderId = "https://holder.example/id",
            holderVerificationMethod = "https://holder.example/jwks#key-1",
            dataIntegrityCryptosuite = "eddsa-jcs-2022",
        )

    private fun SelectedCredential.proofCryptosuite(): String =
        presentation.jsonObject["proof"]!!.jsonObject["cryptosuite"]!!.jsonPrimitive.content

}

data class SignCall(
    val walletUnitId: String,
    val keyRef: WalletAttestedKeyRef,
    val signingInput: ByteArray,
    val operationBinding: String,
)

class RecordingWsca(
    private val reportedAlgorithm: String? = null,
    private val resolvedKeyId: String? = null,
    private val publicKeyJwks: Map<String, String> = emptyMap(),
) : Wsca {
    private val preparedSigningFactory = WscaPreparedSigningFactory.create()
    val ensureAliases = mutableListOf<String>()
    val ensureAlgorithms = mutableListOf<SignatureAlgorithm>()
    val signCalls = mutableListOf<SignCall>()

    override val wscdProfile: WscdProfile
        get() = WscdProfile.Software
    override val userAuthentication: WscaUserAuthentication
        get() = error("Not needed for this test")

    override suspend fun ensureKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
        keyAlias: String?,
    ): IdkResult<WalletAttestedKeyRef, IdkError> {
        val alias = keyAlias ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "key alias required"))
        ensureAliases += alias
        ensureAlgorithms += algorithm
        val joseAlgorithm = reportedAlgorithm ?: when (algorithm) {
            SignatureAlgorithm.ED25519 -> "EdDSA"
            SignatureAlgorithm.ECDSA_SHA256 -> "ES256"
            SignatureAlgorithm.ECDSA_SHA384 -> "ES384"
            else -> error("Unsupported test algorithm $algorithm")
        }
        return Ok(
            WalletAttestedKeyRef(
                keyId = resolvedKeyId ?: alias,
                algorithm = joseAlgorithm,
                publicKeyJwk = publicKeyJwks[alias],
                keyRef = alias,
                walletUnitId = walletUnitId,
            ),
        )
    }

    override suspend fun createCredentialKey(
        walletUnitId: String,
        usage: SecureComponentUsage,
        algorithm: SignatureAlgorithm,
    ): IdkResult<WalletAttestedKeyRef, IdkError> =
        error("Not needed for this test")

    override suspend fun discardCredentialKey(
        walletUnitId: String,
        keyRef: WalletAttestedKeyRef,
    ): IdkResult<Unit, IdkError> = error("Not needed for this test")

    override suspend fun prepareSign(request: WscaSigningRequest): IdkResult<WscaPreparedSigning, IdkError> =
        Ok(
            preparedSigningFactory.mint(
                walletUnitId = request.walletUnitId,
                keyRef = request.keyRef,
                walletAccountId = request.walletAccountId,
                operationBinding = request.operationBinding,
                operationType = "test.sign",
                digestBinding = "test-digest",
                nonce = request.nonce ?: "test-nonce",
                audience = request.audience ?: request.operationBinding,
                signingInput = request.signingInput,
            ),
        )

    override suspend fun sign(
        prepared: WscaPreparedSigning,
        request: WscaSigningRequest,
    ): IdkResult<ByteArray, IdkError> {
        signCalls += SignCall(request.walletUnitId, request.keyRef, request.signingInput, request.operationBinding)
        val signatureSize = if (request.keyRef.algorithm == "ES384") 96 else 64
        return Ok(ByteArray(signatureSize) { 7 })
    }

    override suspend fun createDpopProof(
        request: WscaDpopProofRequest,
    ): IdkResult<WscaDpopProofResult, IdkError> = error("Not needed for this test")

    override suspend fun createClientAttestationAuth(
        request: WscaClientAttestationAuthRequest,
    ): IdkResult<WscaClientAttestationAuthResult, IdkError> = error("Not needed for this test")

    override suspend fun attestKeys(
        request: KeyAttestationIssueRequest,
    ): IdkResult<KeyAttestationIssueResult, IdkError> = error("Not needed for this test")
}
