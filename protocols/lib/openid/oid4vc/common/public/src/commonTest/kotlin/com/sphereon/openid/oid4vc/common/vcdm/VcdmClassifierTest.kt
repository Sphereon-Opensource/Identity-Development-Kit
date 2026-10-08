/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common.vcdm

import com.sphereon.core.api.error.sourceAs
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.PresentationFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VcdmClassifierTest {
    @Test
    fun classificationKeepsCredentialAndPresentationFormatsDisjoint() {
        val credential = VcdmClassifier.classifyCompactJws("$V1_HEADER.$V1_CREDENTIAL_PAYLOAD.$SIGNATURE")
        val presentation = VcdmClassifier.classifyCompactJws("$V1_HEADER.$V1_PRESENTATION_PAYLOAD.$SIGNATURE")

        assertTrue(credential.isOk)
        assertEquals(CredentialFormat.JWT_VC_JSON, credential.value.credentialFormat)
        assertEquals(null, credential.value.presentationFormat)

        assertTrue(presentation.isOk)
        assertEquals(null, presentation.value.credentialFormat)
        assertEquals(PresentationFormat.JWT_VP_JSON, presentation.value.presentationFormat)
    }

    @Test
    fun classifiesBareVcdm11DataIntegrityCredential() {
        val document = Json.parseToJsonElement(
            """{"@context":["$V1_CONTEXT","https://example.com/contexts/claims"],"type":["VerifiableCredential","ExampleCredential"],"issuer":"https://issuer.example","credentialSubject":{"id":"urn:example:subject"}}""",
        ).jsonObject

        val result = VcdmClassifier.classifyDocument(document)

        assertTrue(result.isOk)
        assertEquals(VcdmVersion.V1_1, result.value.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, result.value.kind)
        assertEquals(document, result.value.json)
    }

    @Test
    fun classifiesBareVcdm20DataIntegrityPresentation() {
        val document = Json.parseToJsonElement(
            """{"@context":"$V2_CONTEXT","type":["VerifiablePresentation"],"holder":"https://holder.example","verifiableCredential":[]}""",
        ).jsonObject

        val result = VcdmClassifier.classifyDocument(document)

        assertTrue(result.isOk)
        assertEquals(VcdmVersion.V2_0, result.value.version)
        assertEquals(VcdmDocumentKind.PRESENTATION, result.value.kind)
        assertEquals(document, result.value.json)
    }

    @Test
    fun bareDocumentClassifierRejectsAmbiguousVersions() {
        val document = Json.parseToJsonElement(
            """{"@context":["$V1_CONTEXT","$V2_CONTEXT"],"type":["VerifiableCredential"]}""",
        ).jsonObject

        assertTypedError<VcdmError.AmbiguousVersion>(VcdmClassifier.classifyDocument(document))
    }

    @Test
    fun bareDocumentClassifierRejectsJwtEnvelopeClaims() {
        val document = Json.parseToJsonElement(
            """{"vc":{"@context":"$V1_CONTEXT","type":["VerifiableCredential"]}}""",
        ).jsonObject

        assertTypedError<VcdmError.ContradictoryDocumentShape>(VcdmClassifier.classifyDocument(document))
    }

    @Test
    fun classifiesVcdm11CredentialAndPreservesInnerUnknownJson() {
        val result = VcdmClassifier.classifyCompactJws("$V1_HEADER.$V1_CREDENTIAL_PAYLOAD.$SIGNATURE")

        assertTrue(result.isOk)
        val classification = result.value
        assertEquals(VcdmVersion.V1_1, classification.document.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, classification.document.kind)
        assertEquals(CredentialFormat.JWT_VC_JSON, classification.credentialFormat)
        assertEquals(null, classification.presentationFormat)
        assertEquals("compact-jws", classification.securingMechanism)
        assertProtectedHeaderPreserved(classification, V1_HEADER)
        assertTrue(classification.rawPayload.containsKey("vc"))
        assertEquals("https://issuer.example/keys/1", classification.document.json["issuer"]?.jsonPrimitive?.content)
        assertEquals("2023-11-14T22:13:20Z", classification.document.json["issuanceDate"]?.jsonPrimitive?.content)
        assertEquals(42, classification.document.json["credentialSubject"]?.jsonObject?.get("nested")?.jsonObject?.get("answer")?.toString()?.toInt())
        assertValidationIsClean(VcdmProfiles.v1_1.validateCredential(classification.document.json))
    }

    @Test
    fun classifiesVcdm11Presentation() {
        val result = VcdmClassifier.classifyCompactJws("$V1_HEADER.$V1_PRESENTATION_PAYLOAD.$SIGNATURE")

        assertTrue(result.isOk)
        val classification = result.value
        assertEquals(VcdmVersion.V1_1, classification.document.version)
        assertEquals(VcdmDocumentKind.PRESENTATION, classification.document.kind)
        assertEquals(null, classification.credentialFormat)
        assertEquals(PresentationFormat.JWT_VP_JSON, classification.presentationFormat)
        assertProtectedHeaderPreserved(classification, V1_HEADER)
        assertTrue(classification.rawPayload.containsKey("vp"))
        assertEquals("https://holder.example/keys/1", classification.document.json["holder"]?.jsonPrimitive?.content)
        assertEquals(42, classification.document.json["proof"]?.jsonObject?.get("nested")?.jsonObject?.get("answer")?.toString()?.toInt())
        assertValidationIsClean(VcdmProfiles.v1_1.validatePresentation(classification.document.json))
    }

    @Test
    fun classifiesVcdm20CredentialAsDirectJosePayload() {
        val result = VcdmClassifier.classifyCompactJws("$HEADER.$V2_CREDENTIAL_PAYLOAD.$SIGNATURE")

        assertTrue(result.isOk)
        val classification = result.value
        assertEquals(VcdmVersion.V2_0, classification.document.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, classification.document.kind)
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, classification.credentialFormat)
        assertEquals(null, classification.presentationFormat)
        assertProtectedHeaderPreserved(classification)
        assertEquals(42, classification.document.json["credentialSubject"]?.jsonObject?.get("nested")?.jsonObject?.get("answer")?.toString()?.toInt())
        assertValidationIsClean(VcdmProfiles.v2_0.validateCredential(classification.document.json))
    }

    @Test
    fun acceptsVcdm20JwtWithIndependentDataAndSignatureTimelines() {
        val result = classifyPayload(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","validFrom":"2099-01-01T00:00:00Z","validUntil":"2100-01-01T00:00:00Z","credentialSubject":{"id":"did:example:subject"},"iss":"https://issuer.example","iat":1700000000.5,"exp":1700003600.5}""",
        )

        assertTrue(result.isOk, "signature timing must not be equated with VCDM data validity")
        assertEquals("2099-01-01T00:00:00Z", result.value.document.json["validFrom"]?.jsonPrimitive?.content)
        assertEquals(1700000000.5, result.value.rawPayload["iat"]?.jsonPrimitive?.doubleOrNull)
        assertEquals(1700003600.5, result.value.rawPayload["exp"]?.jsonPrimitive?.doubleOrNull)
    }

    @Test
    fun rejectsVcdm20JwtWithMalformedNumericDateClaims() {
        val payloads = listOf(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"iat":"1700000000"}""",
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"nbf":true}""",
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"exp":{"seconds":1700000000}}""",
        )

        payloads.forEach { payload ->
            val result = classifyPayload(payload)
            assertTrue(result.isErr, payload)
            assertIs<VcdmError.InvalidJwtClaim>(result.error.sourceAs())
        }
    }

    @Test
    fun rejectsVcdm20JwtWithInconsistentSignatureTemporalOrdering() {
        val payloads = listOf(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"iat":200,"exp":100}""",
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"iat":100,"nbf":200,"exp":150}""",
        )

        payloads.forEach { payload ->
            val result = classifyPayload(payload)
            assertTrue(result.isErr, payload)
            assertIs<VcdmError.InvalidJwtClaim>(result.error.sourceAs())
        }
    }

    @Test
    fun acceptsVcdm20JwtWithEarlierOrFutureNotBeforeClaim() {
        val payloads = listOf(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"iat":200,"nbf":100,"exp":300}""",
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"},"iat":100,"nbf":4102444800,"exp":4102448400}""",
        )

        payloads.forEach { payload ->
            assertTrue(classifyPayload(payload).isOk, payload)
        }
    }

    @Test
    fun rejectsVcdm20JwtWithReversedDataValidityPeriod() {
        val result = classifyPayload(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","validFrom":"2027-01-01T00:00:00Z","validUntil":"2026-01-01T00:00:00Z","credentialSubject":{"id":"did:example:subject"}}""",
        )

        assertTrue(result.isErr)
        val error = assertIs<VcdmError.InvalidProperty>(result.error.sourceAs())
        assertEquals("validUntil", error.property)
    }

    @Test
    fun acceptsVcdm20JwtWithPastDataValidityPeriod() {
        val result = classifyPayload(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","validFrom":"2020-01-01T00:00:00Z","validUntil":"2021-01-01T00:00:00Z","credentialSubject":{"id":"did:example:subject"},"iat":1700000000,"exp":1700003600}""",
        )

        assertTrue(result.isOk, "expiry relative to the current clock is a runtime policy, not shape validation")
    }

    @Test
    fun classifiesVcdm20PresentationAsDirectJosePayload() {
        val result = VcdmClassifier.classifyCompactJws("$V2_PRESENTATION_HEADER.$V2_PRESENTATION_PAYLOAD.$SIGNATURE")

        assertTrue(result.isOk)
        val classification = result.value
        assertEquals(VcdmVersion.V2_0, classification.document.version)
        assertEquals(VcdmDocumentKind.PRESENTATION, classification.document.kind)
        assertEquals(null, classification.credentialFormat)
        assertEquals(PresentationFormat.JWT_VP_JSON, classification.presentationFormat)
        assertProtectedHeaderPreserved(classification, V2_PRESENTATION_HEADER)
        assertEquals(42, classification.document.json["proof"]?.jsonObject?.get("nested")?.jsonObject?.get("answer")?.toString()?.toInt())
        assertValidationIsClean(VcdmProfiles.v2_0.validatePresentation(classification.document.json))
    }

    @Test
    fun rejectsAmbiguousBaseContexts() {
        val result = classifyPayload("""{"@context":["$V1_CONTEXT","$V2_CONTEXT"],"type":["VerifiableCredential"]}""")

        assertTypedError<VcdmError.AmbiguousVersion>(result)
    }

    @Test
    fun rejectsMissingBaseContext() {
        val result = classifyPayload("""{"type":["VerifiableCredential"]}""")

        assertTypedError<VcdmError.MissingBaseContext>(result)
    }

    @Test
    fun rejectsUnknownBaseContextAsUnsupportedVersionWithExactContexts() {
        val contexts = listOf("https://example.com/contexts/unknown")
        val result = classifyPayload("""{"@context":"${contexts.single()}","type":["VerifiableCredential"]}""")

        assertTrue(result.isErr)
        val error = assertIs<VcdmError.UnsupportedVersion>(result.error.sourceAs())
        assertEquals(contexts, error.contexts)
    }

    @Test
    fun rejectsVcdm20ContextInsideVcdm11JwtVcEnvelope() {
        val result = classifyPayload("""{"vc":{"@context":"$V2_CONTEXT","type":["VerifiableCredential"]}}""")

        assertTypedError<VcdmError.ContradictoryDocumentShape>(result)
    }

    @Test
    fun rejectsVcdm20ContextInsideVcdm11JwtVpEnvelope() {
        val result = classifyPayload("""{"vp":{"@context":"$V2_CONTEXT","type":["VerifiablePresentation"]}}""")

        assertTypedError<VcdmError.ContradictoryDocumentShape>(result)
    }

    @Test
    fun rejectsVcdm11JwtCredentialWithoutVcEnvelopeClaim() {
        val result = classifyPayload("""{"@context":"$V1_CONTEXT","type":["VerifiableCredential"]}""")

        assertTypedError<VcdmError.ContradictoryDocumentShape>(result)
    }

    @Test
    fun rejectsBothVcdm11JwtEnvelopeClaims() {
        val result = classifyPayload("""{"vc":{"@context":"$V1_CONTEXT","type":["VerifiableCredential"]},"vp":{"@context":"$V1_CONTEXT","type":["VerifiablePresentation"]}}""")

        assertTypedError<VcdmError.ContradictoryDocumentShape>(result)
    }

    @Test
    fun rejectsBothRecognizedDocumentTypes() {
        val result = classifyPayload("""{"@context":"$V2_CONTEXT","type":["VerifiableCredential","VerifiablePresentation"]}""")

        assertTypedError<VcdmError.ContradictoryDocumentShape>(result)
    }

    @Test
    fun rejectsUnrecognizedDocumentTypes() {
        val result = classifyPayload("""{"@context":"$V2_CONTEXT","type":["ExampleDocument"]}""")

        assertTypedError<VcdmError.UnsupportedDocumentKind>(result)
    }

    @Test
    fun forwardsStrictCompactJwsErrorsWithoutReclassification() {
        val result = VcdmClassifier.classifyCompactJws("bad")

        assertTrue(result.isErr)
        assertIs<com.sphereon.crypto.jose.jws.StrictCompactJwsError.InvalidSegmentCount>(result.error.sourceAs())
    }

    @Test
    fun rejectsMissingSignatureThroughStrictParser() {
        val result = VcdmClassifier.classifyCompactJws("$HEADER.$V2_CREDENTIAL_PAYLOAD.")

        assertTrue(result.isErr)
        assertIs<com.sphereon.crypto.jose.jws.StrictCompactJwsError.EmptySegment>(result.error.sourceAs())
    }

    @Test
    fun rejectsAlgNoneAtSecurityClassificationBoundary() {
        val result = VcdmClassifier.classifyCompactJws("$NONE_HEADER.$V2_CREDENTIAL_PAYLOAD.$SIGNATURE")

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("alg:none", ignoreCase = true))
    }

    @Test
    fun rejectsNonStringMissingOrBlankAlgorithmAtSecurityClassificationBoundary() {
        val invalidHeaders = listOf(
            "{}",
            "{\"alg\":\"\"}",
            "{\"alg\":123}",
            "{\"alg\":true}",
            "{\"alg\":{\"name\":\"ES256\"}}",
        )

        invalidHeaders.forEach { header ->
            val result = classifyWithHeader(header, V2_CREDENTIAL_PAYLOAD)
            assertTrue(result.isErr, "header=$header must fail closed")
        }
    }

    @Test
    fun rejectsNonJwtTypForVcdm11ButAllowsTypToBeAbsent() {
        val wrong = classifyEncodedPayloadWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vc+jwt\"}", V1_CREDENTIAL_PAYLOAD)
        val missing = classifyEncodedPayloadWithHeader("{\"alg\":\"EdDSA\"}", V1_CREDENTIAL_PAYLOAD)

        assertIs<VcdmError.InvalidJoseHeader>(wrong.error.sourceAs())
        assertTrue(missing.isOk)
    }

    @Test
    fun rejectsWrongVcdm20JoseMediaTypesButAllowsOptionalHeadersToBeAbsent() {
        val credential =
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"https://subject.example"}}"""
        val presentation =
            """{"@context":"$V2_CONTEXT","type":"VerifiablePresentation","holder":"https://holder.example"}"""

        listOf(
            classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"bad+typ\",\"cty\":\"vc\"}", credential),
            classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vp+jwt\",\"cty\":\"vc\"}", credential),
            classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vc+jwt\",\"cty\":\"bad+cty\"}", credential),
            classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vc+jwt\",\"cty\":\"vp\"}", credential),
            classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vc+jwt\",\"cty\":\"vp\"}", presentation),
        ).forEach { result ->
            assertIs<VcdmError.InvalidJoseHeader>(result.error.sourceAs())
        }

        assertTrue(classifyWithHeader("{\"alg\":\"EdDSA\"}", credential).isOk)
        assertTrue(classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vc+jwt\",\"cty\":\"vc\"}", credential).isOk)
        assertTrue(classifyWithHeader("{\"alg\":\"EdDSA\",\"typ\":\"vp+jwt\",\"cty\":\"vp\"}", presentation).isOk)
    }

    @Test
    fun preservesUnknownJoseAndVcdmExtensionsWithoutTreatingThemAsRegisteredClaims() {
        val credential =
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"https://subject.example"},"x-example-extension":{"flag":true}}"""
        val result =
            classifyWithHeader(
                """{"alg":"EdDSA","typ":"vc+jwt","cty":"vc","x-example-header":"preserved"}""",
                credential,
            )

        assertTrue(result.isOk)
        assertEquals("preserved", result.value.protectedHeader["x-example-header"]?.jsonPrimitive?.content)
        assertEquals(
            true,
            result.value.rawPayload["x-example-extension"]?.jsonObject?.get("flag")?.jsonPrimitive?.content?.toBooleanStrict(),
        )
        assertEquals(result.value.rawPayload["x-example-extension"], result.value.document.json["x-example-extension"])
    }

    @Test
    fun rejectsContradictoryVcdm20RegisteredClaimsWithoutRewritingPayload() {
        val payloads = listOf(
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example/right","iss":"https://issuer.example/wrong","credentialSubject":{"employeeId":"42"}}""",
            """{"@context":"$V2_CONTEXT","id":"urn:uuid:right","jti":"urn:uuid:wrong","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"employeeId":"42"}}""",
            """{"@context":"$V2_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example","sub":"https://subject.example/right","credentialSubject":{"id":"https://subject.example/wrong","employeeId":"42"}}""",
        )

        payloads.forEach { payload ->
            val result = classifyPayload(payload)
            assertTrue(result.isErr, payload)
            assertIs<VcdmError.InconsistentJwtClaim>(result.error.sourceAs())
        }
    }

    @Test
    fun decodesAllVcdm11RegisteredClaimsIntoSemanticCredentialWithoutMutatingRawPayload() {
        val result = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000.25,"exp":1700003600.75,"jti":"urn:uuid:credential-1","sub":"https://subject.example/alice","vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":{"employeeId":"42"}}}""",
        )

        assertTrue(result.isOk)
        val classification = result.value
        assertEquals("https://issuer.example/keys/1", classification.document.json["issuer"]?.jsonPrimitive?.content)
        assertEquals("2023-11-14T22:13:20.250Z", classification.document.json["issuanceDate"]?.jsonPrimitive?.content)
        assertEquals("2023-11-14T23:13:20.750Z", classification.document.json["expirationDate"]?.jsonPrimitive?.content)
        assertEquals("urn:uuid:credential-1", classification.document.json["id"]?.jsonPrimitive?.content)
        assertEquals(
            "https://subject.example/alice",
            classification.document.json["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.content,
        )
        assertTrue(classification.rawPayload.containsKey("vc"))
        assertTrue(classification.rawPayload["vc"]?.jsonObject?.containsKey("issuer") == false)
        assertValidationIsClean(VcdmProfiles.v1_1.validateCredential(classification.document.json))
    }

    @Test
    fun acceptsSemanticallyEqualDuplicatedVcdm11RegisteredClaims() {
        val result = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"exp":1700003600,"jti":"urn:uuid:credential-1","sub":"https://subject.example/alice","vc":{"@context":"$V1_CONTEXT","id":"urn:uuid:credential-1","type":"VerifiableCredential","issuer":{"id":"https://issuer.example/keys/1"},"issuanceDate":"2023-11-14T22:13:20Z","expirationDate":"2023-11-14T23:13:20Z","credentialSubject":{"id":"https://subject.example/alice","employeeId":"42"}}}""",
        )

        assertTrue(result.isOk)
        assertValidationIsClean(VcdmProfiles.v1_1.validateCredential(result.value.document.json))
    }

    @Test
    fun rejectsContradictoryVcdm11RegisteredClaims() {
        val payloads = listOf(
            """{"iss":"https://issuer.example/right","nbf":1700000000,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","issuer":"https://issuer.example/wrong","credentialSubject":{"employeeId":"42"}}}""",
            """{"iss":"https://issuer.example/right","nbf":1700000000,"jti":"urn:uuid:right","vc":{"@context":"$V1_CONTEXT","id":"urn:uuid:wrong","type":"VerifiableCredential","credentialSubject":{"employeeId":"42"}}}""",
            """{"iss":"https://issuer.example/right","nbf":1700000000,"sub":"https://subject.example/right","vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":{"id":"https://subject.example/wrong","employeeId":"42"}}}""",
        )

        payloads.forEach { payload ->
            val result = classifyPayload(payload)
            assertTrue(result.isErr, payload)
            assertIs<VcdmError.InconsistentJwtClaim>(result.error.sourceAs())
        }
    }

    @Test
    fun enforcesVcdm11ExpirationDateToExpMapping() {
        val missingExp = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","expirationDate":"2023-11-14T23:13:20Z","credentialSubject":{"employeeId":"42"}}}""",
        )
        val mismatchedExp = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"exp":1700000001,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","expirationDate":"2023-11-14T23:13:20Z","credentialSubject":{"employeeId":"42"}}}""",
        )
        val matchingExp = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"exp":1700003600,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","expirationDate":"2023-11-14T23:13:20Z","credentialSubject":{"employeeId":"42"}}}""",
        )

        assertIs<VcdmError.InvalidJwtClaim>(missingExp.error.sourceAs())
        assertIs<VcdmError.InconsistentJwtClaim>(mismatchedExp.error.sourceAs())
        assertTrue(matchingExp.isOk)
    }

    @Test
    fun enforcesVcdm11IdAndSingleSubjectMappings() {
        val missingJti = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"vc":{"@context":"$V1_CONTEXT","id":"urn:uuid:credential-1","type":"VerifiableCredential","credentialSubject":{"employeeId":"42"}}}""",
        )
        val missingSub = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":{"id":"https://subject.example/alice","employeeId":"42"}}}""",
        )
        val missingPresentationJti = classifyPayload(
            """{"iss":"https://holder.example/keys/1","aud":"https://verifier.example/client","vp":{"@context":"$V1_CONTEXT","id":"urn:uuid:presentation-1","type":"VerifiablePresentation"}}""",
        )
        val matchingPresentationJti = classifyPayload(
            """{"iss":"https://holder.example/keys/1","aud":"https://verifier.example/client","jti":"urn:uuid:presentation-1","vp":{"@context":"$V1_CONTEXT","id":"urn:uuid:presentation-1","type":"VerifiablePresentation"}}""",
        )

        assertIs<VcdmError.InvalidJwtClaim>(missingJti.error.sourceAs())
        assertIs<VcdmError.InvalidJwtClaim>(missingSub.error.sourceAs())
        assertIs<VcdmError.InvalidJwtClaim>(missingPresentationJti.error.sourceAs())
        assertTrue(matchingPresentationJti.isOk)
    }

    @Test
    fun rejectsVcdm11JwtCredentialWithoutMandatoryRegisteredIssuerOrIssuanceClaims() {
        val missingIss = classifyPayload(
            """{"nbf":1700000000,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":{"employeeId":"42"}}}""",
        )
        val missingNbf = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":{"employeeId":"42"}}}""",
        )

        assertIs<VcdmError.InvalidJwtClaim>(missingIss.error.sourceAs())
        assertIs<VcdmError.InvalidJwtClaim>(missingNbf.error.sourceAs())
    }

    @Test
    fun rejectsVcdm11JwtCredentialWithMultipleSubjectsEvenWhenSubIsAbsent() {
        val result = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":[{"id":"https://subject.example/one"},{"id":"https://subject.example/two"}]}}""",
        )

        assertTrue(result.isErr)
        assertIs<VcdmError.InvalidJwtClaim>(result.error.sourceAs())
    }

    @Test
    fun rejectsVcdm11SubClaimForCredentialWithMultipleSubjects() {
        val result = classifyPayload(
            """{"iss":"https://issuer.example/keys/1","nbf":1700000000,"sub":"https://subject.example/one","vc":{"@context":"$V1_CONTEXT","type":"VerifiableCredential","credentialSubject":[{"id":"https://subject.example/one"},{"id":"https://subject.example/two"}]}}""",
        )

        assertTrue(result.isErr)
        assertIs<VcdmError.InvalidJwtClaim>(result.error.sourceAs())
    }

    @Test
    fun rejectsVcdm11JwtPresentationWithoutAudienceBindingOrWithSubjectClaim() {
        val missingAudience = classifyPayload(
            """{"iss":"https://holder.example/keys/1","vp":{"@context":"$V1_CONTEXT","type":"VerifiablePresentation"}}""",
        )
        val subjectClaim = classifyPayload(
            """{"iss":"https://holder.example/keys/1","aud":"https://verifier.example/client","sub":"https://subject.example/alice","vp":{"@context":"$V1_CONTEXT","type":"VerifiablePresentation"}}""",
        )

        assertIs<VcdmError.InvalidJwtClaim>(missingAudience.error.sourceAs())
        assertIs<VcdmError.InvalidJwtClaim>(subjectClaim.error.sourceAs())
    }

    private fun classifyPayload(payload: String): com.sphereon.core.api.IdkResult<VcdmClassification, com.sphereon.core.api.error.IdkError> {
        val header = if (payload.contains(V1_CONTEXT)) V1_HEADER else HEADER
        return VcdmClassifier.classifyCompactJws("$header.${JwsUtils.encodeBytesToBase64Url(payload.encodeToByteArray())}.$SIGNATURE")
    }

    private fun classifyWithHeader(header: String, payload: String) =
        VcdmClassifier.classifyCompactJws(
            "${JwsUtils.encodeBytesToBase64Url(header.encodeToByteArray())}.${JwsUtils.encodeBytesToBase64Url(payload.encodeToByteArray())}.$SIGNATURE",
        )

    private fun classifyEncodedPayloadWithHeader(header: String, encodedPayload: String) =
        VcdmClassifier.classifyCompactJws(
            "${JwsUtils.encodeBytesToBase64Url(header.encodeToByteArray())}.$encodedPayload.$SIGNATURE",
        )

    private fun assertProtectedHeaderPreserved(classification: VcdmClassification, header: String = HEADER) {
        assertEquals(JwsUtils.decodeBase64UrlToJson(header), classification.protectedHeader)
    }

    private fun assertValidationIsClean(validation: VcdmValidationResult) {
        assertTrue(validation.valid)
        assertTrue(validation.errors.isEmpty())
    }

    private inline fun <reified T : com.sphereon.core.api.error.IdkErrorType> assertTypedError(result: com.sphereon.core.api.IdkResult<*, com.sphereon.core.api.error.IdkError>) {
        assertTrue(result.isErr)
        assertIs<T>(result.error.sourceAs())
    }

    private companion object {
        const val HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6InZjK2p3dCJ9"
        const val V2_PRESENTATION_HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6InZwK2p3dCJ9"
        const val V1_HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9"
        const val NONE_HEADER = "eyJhbGciOiJub25lIiwidHlwIjoidmMrand0In0"
        const val SIGNATURE = "AQID"
        const val V1_CONTEXT = "https://www.w3.org/2018/credentials/v1"
        const val V2_CONTEXT = "https://www.w3.org/ns/credentials/v2"
        const val V1_CREDENTIAL_PAYLOAD = "eyJpc3MiOiJodHRwczovL2lzc3Vlci5leGFtcGxlL2tleXMvMSIsIm5iZiI6MTcwMDAwMDAwMCwic3ViIjoidXJuOmV4YW1wbGU6c3ViamVjdCIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIiwiaHR0cHM6Ly9leGFtcGxlLmNvbS9leHQiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIkV4YW1wbGVDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoidXJuOmV4YW1wbGU6c3ViamVjdCIsIm5lc3RlZCI6eyJhbnN3ZXIiOjQyfX19fQ"
        const val V1_PRESENTATION_PAYLOAD = "eyJpc3MiOiJodHRwczovL2hvbGRlci5leGFtcGxlL2tleXMvMSIsImF1ZCI6Imh0dHBzOi8vdmVyaWZpZXIuZXhhbXBsZS9jbGllbnQiLCJub25jZSI6Im4tMFM2X1d6QTJNaiIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIiwiaHR0cHM6Ly9leGFtcGxlLmNvbS9leHQiXSwidHlwZSI6WyJWZXJpZmlhYmxlUHJlc2VudGF0aW9uIiwiRXhhbXBsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJ1cm46ZXhhbXBsZTpjcmVkZW50aWFsIl0sInByb29mIjp7Im5lc3RlZCI6eyJhbnN3ZXIiOjQyfX19fQ"
        const val V2_CREDENTIAL_PAYLOAD = "eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvdjIiLCJodHRwczovL2V4YW1wbGUuY29tL2V4dCJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiRXhhbXBsZUNyZWRlbnRpYWwiXSwiaXNzdWVyIjoiaHR0cHM6Ly9pc3N1ZXIuZXhhbXBsZS9rZXlzLzEiLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6Imh0dHBzOi8vc3ViamVjdC5leGFtcGxlL2FsaWNlIiwibmVzdGVkIjp7ImFuc3dlciI6NDJ9fX0"
        const val V2_PRESENTATION_PAYLOAD = "eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvbnMvY3JlZGVudGlhbHMvdjIiLCJodHRwczovL2V4YW1wbGUuY29tL2V4dCJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iLCJFeGFtcGxlUHJlc2VudGF0aW9uIl0sImhvbGRlciI6Imh0dHBzOi8vaG9sZGVyLmV4YW1wbGUva2V5cy8xIiwidmVyaWZpYWJsZUNyZWRlbnRpYWwiOlt7IkBjb250ZXh0IjoiaHR0cHM6Ly93d3cudzMub3JnL25zL2NyZWRlbnRpYWxzL3YyIiwiaWQiOiJkYXRhOmFwcGxpY2F0aW9uL3ZjK2p3dCxleUpoYkdjaU9pSkZaRVJUUVNKOS5lMzAuQVFJRCIsInR5cGUiOiJFbnZlbG9wZWRWZXJpZmlhYmxlQ3JlZGVudGlhbCJ9XSwicHJvb2YiOnsibmVzdGVkIjp7ImFuc3dlciI6NDJ9fX0"
    }
}
