/*
 * Copyright 2023-2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.openid.oid4vc.common.vcdm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VcdmProfileValidationTest {
    @Test
    fun `VCDM 1_1 credential requires issuer issuanceDate and credentialSubject`() {
        val invalid = document(
            """{"@context":"${VcdmProfiles.V1_1_CONTEXT}","type":"VerifiableCredential"}""",
        )

        val result = VcdmProfiles.v1_1.validateCredential(invalid)

        assertFalse(result.valid)
        assertInvalid(result, "issuer")
        assertInvalid(result, "issuanceDate")
        assertInvalid(result, "credentialSubject")
    }

    @Test
    fun `VCDM 1_1 credential accepts URI identifiers without requiring DIDs`() {
        val valid = document(
            """{"@context":["${VcdmProfiles.V1_1_CONTEXT}",{"EmployeeCredential":"https://schema.example/EmployeeCredential"}],"id":"urn:uuid:credential-1","type":["VerifiableCredential","EmployeeCredential"],"issuer":{"id":"https://issuer.example"},"issuanceDate":"2026-08-26T09:00:00Z","credentialSubject":{"id":"https://subject.example/alice","employeeId":"42"}}""",
        )

        assertTrue(VcdmProfiles.v1_1.validateCredential(valid).valid)
    }

    @Test
    fun `VCDM 2 credential validates issuer subjects and validity ordering`() {
        val invalid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"relative-issuer","validFrom":"2026-08-27T09:00:00Z","validUntil":"2026-08-26T09:00:00Z","credentialSubject":[]}""",
        )

        val result = VcdmProfiles.v2_0.validateCredential(invalid)

        assertFalse(result.valid)
        assertInvalid(result, "issuer")
        assertInvalid(result, "credentialSubject")
        assertInvalid(result, "validUntil")
    }

    @Test
    fun `VCDM 2 presentation accepts multiple object credentials and enveloped presentations`() {
        val valid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","holder":"https://holder.example","verifiableCredential":[{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"EnvelopedVerifiableCredential","id":"data:application/vc+jwt,abc"},{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"EnvelopedVerifiablePresentation","id":"data:application/vp+jwt,def"}]}""",
        )

        assertTrue(VcdmProfiles.v2_0.validatePresentation(valid).valid)
    }

    @Test
    fun `VCDM 2 presentation rejects compact string children at the data model boundary`() {
        val invalid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":["header.payload.signature"]}""",
        )

        val result = VcdmProfiles.v2_0.validatePresentation(invalid)

        assertFalse(result.valid)
        assertInvalid(result, "verifiableCredential")
    }

    @Test
    fun `context and type reject malformed mixed values`() {
        val invalid = document(
            """{"@context":["${VcdmProfiles.V2_0_CONTEXT}",42],"type":["VerifiableCredential",false],"issuer":"https://issuer.example","credentialSubject":{"id":"https://subject.example"}}""",
        )

        val result = VcdmProfiles.v2_0.validateCredential(invalid)

        assertFalse(result.valid)
        assertInvalid(result, "@context")
        assertInvalid(result, "type")
    }

    @Test
    fun `credential subjects require a property and validate optional identifiers`() {
        val empty = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{}}""",
        )
        val identifierOnly = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"did:example:subject"}}""",
        )
        val invalidId = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"id":"relative","employeeId":"42"}}""",
        )

        assertInvalid(VcdmProfiles.v2_0.validateCredential(empty), "credentialSubject")
        assertTrue(VcdmProfiles.v2_0.validateCredential(identifierOnly).valid)
        assertInvalid(VcdmProfiles.v2_0.validateCredential(invalidId), "credentialSubject")
    }

    @Test
    fun `version profiles preserve status and refresh service differences`() {
        val v1 = document(
            """{"@context":"${VcdmProfiles.V1_1_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","issuanceDate":"2026-08-26T09:00:00Z","credentialSubject":{"employeeId":"42"},"credentialStatus":{"type":"ExampleStatus"},"refreshService":{"type":"ExampleRefresh"}}""",
        )
        val v2 = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"employeeId":"42"},"credentialStatus":{"type":"ExampleStatus"},"refreshService":{"type":"ExampleRefresh"}}""",
        )

        val v1Result = VcdmProfiles.v1_1.validateCredential(v1)
        assertInvalid(v1Result, "credentialStatus")
        assertInvalid(v1Result, "refreshService")
        assertTrue(VcdmProfiles.v2_0.validateCredential(v2).valid)
    }

    @Test
    fun `evidence requires typed objects in both profiles`() {
        val invalid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"employeeId":"42"},"evidence":{"id":"https://evidence.example/1"}}""",
        )

        assertInvalid(VcdmProfiles.v2_0.validateCredential(invalid), "evidence")
    }

    @Test
    fun `VCDM 2 names and descriptions accept strict language value objects`() {
        val valid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","name":[{"@value":"Employment credential","@language":"en"},{"@value":"Arbeidscredential","@language":"nl","@direction":"ltr"}],"description":"Employment status","credentialSubject":{"employeeId":"42"}}""",
        )
        val invalid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","name":{"@value":"Employment credential","extra":true},"description":{"@value":42},"credentialSubject":{"employeeId":"42"}}""",
        )

        assertTrue(VcdmProfiles.v2_0.validateCredential(valid).valid)
        assertInvalid(VcdmProfiles.v2_0.validateCredential(invalid), "name")
        assertInvalid(VcdmProfiles.v2_0.validateCredential(invalid), "description")
    }

    @Test
    fun `VCDM 2 related resources require unique ids and integrity digests`() {
        val valid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"employeeId":"42"},"relatedResource":[{"id":"https://resource.example/context","mediaType":"application/ld+json","digestMultibase":"uEiBZlVztZpfWHgPyslVv6-UwirFoQoRvW1htfx963sknNA"}]}""",
        )
        val invalid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiableCredential","issuer":"https://issuer.example","credentialSubject":{"employeeId":"42"},"relatedResource":[{"id":"https://resource.example/context"},{"id":"https://resource.example/context","digestSRI":"sha384-value"}]}""",
        )

        assertTrue(VcdmProfiles.v2_0.validateCredential(valid).valid)
        assertInvalid(VcdmProfiles.v2_0.validateCredential(invalid), "relatedResource")
    }

    @Test
    fun `VCDM 2 presentation children must identify credential or nested presentation shapes`() {
        val invalid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":{"id":"data:application/vc+jwt,abc"}}""",
        )

        assertInvalid(VcdmProfiles.v2_0.validatePresentation(invalid), "verifiableCredential")
    }

    @Test
    fun `VCDM 2 presentation requires well formed credential and nested presentation envelopes`() {
        val valid = document(
            """{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":[{"@context":"${VcdmProfiles.V2_0_CONTEXT}","id":"data:application/vc+jwt,aaa.bbb.ccc","type":"EnvelopedVerifiableCredential"},{"@context":"${VcdmProfiles.V2_0_CONTEXT}","id":"data:application/vp+jwt,ddd.eee.fff","type":"EnvelopedVerifiablePresentation"}]}""",
        )
        val invalid = listOf(
            document("""{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":{"id":"data:application/vc+jwt,aaa.bbb.ccc","type":"EnvelopedVerifiableCredential"}}"""),
            document("""{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":{"@context":"${VcdmProfiles.V2_0_CONTEXT}","id":"https://example.com/not-a-data-url","type":"EnvelopedVerifiableCredential"}}"""),
            document("""{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":{"@context":"${VcdmProfiles.V2_0_CONTEXT}","id":"data:application/vc+jwt,aaa.bbb.ccc","type":"EnvelopedVerifiablePresentation"}}"""),
            document("""{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation","verifiableCredential":{"@context":"${VcdmProfiles.V2_0_CONTEXT}","type":"VerifiablePresentation"}}"""),
        )

        assertTrue(VcdmProfiles.v2_0.validatePresentation(valid).valid)
        invalid.forEach { assertInvalid(VcdmProfiles.v2_0.validatePresentation(it), "verifiableCredential") }
    }

    private fun assertInvalid(result: VcdmValidationResult, property: String) {
        assertTrue(
            result.errors.any { it is VcdmError.InvalidProperty && it.property == property },
            "expected invalid property '$property', got ${result.errors}",
        )
    }

    private fun document(value: String) = Json.parseToJsonElement(value).jsonObject
}
