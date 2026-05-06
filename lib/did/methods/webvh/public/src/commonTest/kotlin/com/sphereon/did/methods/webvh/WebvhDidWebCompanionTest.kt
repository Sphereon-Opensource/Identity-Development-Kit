/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh

import com.sphereon.did.methods.webvh.companion.WebvhDidWebCompanion
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spec: webvh v1.0 "Publishing a Parallel did:web DID".
 *
 * Tests the pure companion-builder utility — DID id rewriting, verification
 * method rewriting (id + controller + verification-relationship references),
 * service id rewriting, and `alsoKnownAs` binding.
 */
class WebvhDidWebCompanionTest {
    private val webvhDid = "did:webvh:QmExampleSCID:example.com"
    private val webDid = "did:web:example.com"

    private fun sampleWebvhDoc(): DidDocument {
        val vmId = "$webvhDid#key-1"
        val vm =
            VerificationMethod(
                id = vmId,
                type = VerificationMethodType.MULTIKEY.value,
                controller = webvhDid,
                publicKeyMultibase = "z6MkExample",
            )
        val ref = VerificationMethodOrReference.fromReference(vmId)
        return DidDocument(
            id = webvhDid,
            verificationMethod = listOf(vm),
            authentication = listOf(ref),
            assertionMethod = listOf(ref),
            service =
                listOf(
                    com.sphereon.did.models.DidService(
                        id = "$webvhDid#domain",
                        type = "LinkedDomains",
                        serviceEndpoint = "https://example.com",
                    ),
                ),
        )
    }

    @Test
    fun rewritesDocumentIdToDidWeb() {
        val result = WebvhDidWebCompanion.build(webvhDid, sampleWebvhDoc())
        assertTrue(result.isOk)
        assertEquals(webDid, result.value.id)
    }

    @Test
    fun rewritesVerificationMethodIdAndController() {
        val result = WebvhDidWebCompanion.build(webvhDid, sampleWebvhDoc())
        assertTrue(result.isOk)
        val vm = result.value.verificationMethod?.firstOrNull()
        assertNotNull(vm)
        assertEquals("$webDid#key-1", vm.id, "VM id must be rewritten to did:web")
        assertEquals(webDid, vm.controller, "VM controller must be rewritten to did:web")
        // Public key material is preserved (same multikey backs both DIDs).
        assertEquals("z6MkExample", vm.publicKeyMultibase)
    }

    @Test
    fun rewritesAuthenticationAndAssertionReferences() {
        val result = WebvhDidWebCompanion.build(webvhDid, sampleWebvhDoc())
        assertTrue(result.isOk)
        assertContentEquals(
            listOf("$webDid#key-1"),
            result.value.authentication?.map { it.reference },
            "authentication references must be rewritten",
        )
        assertContentEquals(
            listOf("$webDid#key-1"),
            result.value.assertionMethod?.map { it.reference },
            "assertionMethod references must be rewritten",
        )
    }

    @Test
    fun rewritesServiceIdAndPreservesEndpoint() {
        val result = WebvhDidWebCompanion.build(webvhDid, sampleWebvhDoc())
        assertTrue(result.isOk)
        val svc = result.value.service?.firstOrNull()
        assertNotNull(svc)
        assertEquals("$webDid#domain", svc.id, "service id must be rewritten")
        assertEquals("https://example.com", svc.serviceEndpoint)
    }

    @Test
    fun bindsAlsoKnownAsToOriginalWebvhDid() {
        val result = WebvhDidWebCompanion.build(webvhDid, sampleWebvhDoc())
        assertTrue(result.isOk)
        val aka = result.value.alsoKnownAs
        assertNotNull(aka)
        assertTrue(webvhDid in aka, "companion's alsoKnownAs must point at the webvh DID")
        assertTrue(webDid !in aka, "companion's alsoKnownAs must NOT include its own id")
    }

    @Test
    fun preservesPriorAlsoKnownAs() {
        val withAka =
            sampleWebvhDoc().copy(alsoKnownAs = listOf("did:example:legacy"))
        val result = WebvhDidWebCompanion.build(webvhDid, withAka)
        assertTrue(result.isOk)
        val aka = result.value.alsoKnownAs
        assertNotNull(aka)
        assertTrue("did:example:legacy" in aka, "prior alsoKnownAs must be preserved")
        assertTrue(webvhDid in aka, "webvh DID must be added to alsoKnownAs")
    }

    @Test
    fun handlesPathSegmentsInWebvhDid() {
        val pathDid = "did:webvh:QmExampleSCID:example.com:tenants:acme"
        val pathWebDid = "did:web:example.com:tenants:acme"
        val doc = DidDocument(id = pathDid)
        val result = WebvhDidWebCompanion.build(pathDid, doc)
        assertTrue(result.isOk)
        assertEquals(pathWebDid, result.value.id, "path-bearing webvh DID must rewrite path through to did:web companion")
    }

    @Test
    fun rejectsNonWebvhInput() {
        val notAWebvh = DidDocument(id = "did:web:example.com")
        val result = WebvhDidWebCompanion.build("did:web:example.com", notAWebvh)
        assertTrue(result.isErr, "non-did:webvh input must error out")
    }

    @Test
    fun emptyDocumentProducesEmptyCompanionWithCorrectId() {
        // No VMs, no services — just an id-only document. Builder should still
        // produce a valid companion with the rewritten id.
        val empty = DidDocument(id = webvhDid)
        val result = WebvhDidWebCompanion.build(webvhDid, empty)
        assertTrue(result.isOk)
        assertEquals(webDid, result.value.id)
        assertNull(result.value.verificationMethod)
        assertNull(result.value.service)
    }
}
