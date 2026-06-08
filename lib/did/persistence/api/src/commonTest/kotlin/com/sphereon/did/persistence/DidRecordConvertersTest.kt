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

package com.sphereon.did.persistence

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.did.manager.DidRole
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Converter tests covering the subset of [decomposition rules][toDidDetail] that are
 * load-bearing for the manager and REST layers.
 */
class DidRecordConvertersTest {
    private val fixedInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val fixedClock =
        object : Clock {
            override fun now(): Instant = fixedInstant
        }

    /** Deterministic id generator — emits `id-0`, `id-1`, … in call order. */
    private class SeqGen : IdGenerator {
        private var i = 0

        override fun next(): String = "id-${i++}"
    }

    private fun binding(alias: String = "default-alias") = VmKmsBinding(keyInfo = KeyInfo<KeyType>(providerId = "test-kms", alias = alias))

    private fun ctx(
        did: String = "did:example:123",
        role: DidRole = DidRole.MANAGED,
        vmKmsBindings: Map<String, VmKmsBinding> = emptyMap(),
    ) = DecomposeContext(
        recordId = "record-uuid",
        did = did,
        method = did.substringAfter("did:").substringBefore(":"),
        role = role,
        tenantId = "t1",
        idGen = SeqGen(),
        clock = fixedClock,
        vmKmsBindings = vmKmsBindings,
    )

    // ---- Web location (did:web / did:webvh share the key) ----

    @Test
    fun webAndWebvhDecomposeToTheSameWebLocation() {
        val webDid = "did:web:example.com:tenants:acme"
        val webvhDid = "did:webvh:QmScid123:example.com:tenants:acme"
        val web = (DidDocument(id = webDid).toDidDetail(ctx(did = webDid)) as Ok).value
        val webvh = (DidDocument(id = webvhDid).toDidDetail(ctx(did = webvhDid)) as Ok).value
        assertEquals("example.com:tenants:acme", web.record.webLocation)
        assertEquals(web.record.webLocation, webvh.record.webLocation, "web and webvh must share the web location")
    }

    @Test
    fun nonWebMethodHasNoWebLocation() {
        val doc = DidDocument(id = "did:example:123")
        val detail = (doc.toDidDetail(ctx()) as Ok).value
        assertNull(detail.record.webLocation)
    }

    // ---- Controller (single vs multi) ----

    @Test
    fun singleControllerRoundTrips() {
        val doc = DidDocument(id = "did:example:123", controller = listOf("did:example:ctrl"))
        val composite = (doc.toDidDetail(ctx()) as Ok).value
        assertEquals(1, composite.controller.size)
        assertEquals("did:example:ctrl", composite.controller[0].controllerDid)
        val rebuilt = composite.toDidDocument()
        assertEquals(listOf("did:example:ctrl"), rebuilt.controller)
    }

    @Test
    fun multiControllerRoundTrips() {
        val doc =
            DidDocument(
                id = "did:example:123",
                controller = listOf("did:example:a", "did:example:b"),
            )
        val composite = (doc.toDidDetail(ctx()) as Ok).value
        assertEquals(listOf(0, 1), composite.controller.sortedBy { it.ordinal }.map { it.ordinal })
        val rebuilt = composite.toDidDocument()
        assertEquals(listOf("did:example:a", "did:example:b"), rebuilt.controller)
    }

    // ---- Verification method + relationship (top-level ref + inline) ----

    @Test
    fun topLevelVmReferencedByRelationshipRoundTrips() {
        val vm =
            VerificationMethod(
                id = "did:example:123#key-1",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "z6Mk...",
            )
        val doc =
            DidDocument(
                id = "did:example:123",
                verificationMethod = listOf(vm),
                authentication = listOf(VerificationMethodOrReference.fromReference("did:example:123#key-1")),
            )
        val bindings = mapOf("did:example:123#key-1" to binding("alias-key-1"))
        val composite = (doc.toDidDetail(ctx(vmKmsBindings = bindings)) as Ok).value

        assertEquals(1, composite.verificationMethod.size)
        val vmRec = composite.verificationMethod[0]
        assertEquals("test-kms", vmRec.kmsProviderId)
        assertEquals("alias-key-1", vmRec.kmsKeyAlias)
        // No inline purposes — rendered top-level only.
        assertNull(vmRec.inlineInJson)

        assertEquals(1, composite.verificationRelationship.size)
        val rel = composite.verificationRelationship[0]
        assertEquals("authentication", rel.purpose)
        assertEquals("did:example:123#key-1", rel.entryRefDidUrl)
        // String-ref to a same-doc VM also populates the FK (entryEmbeddedVmId) for cascade
        // delete; entryRefDidUrl remains the source of truth for the wire form.
        assertEquals(vmRec.id, rel.entryEmbeddedVmId)

        val rebuilt = composite.toDidDocument()
        val auth = rebuilt.authentication ?: error("no authentication array")
        assertEquals(1, auth.size)
        assertEquals("did:example:123#key-1", auth[0].reference)
        assertEquals(1, rebuilt.verificationMethod?.size)
        assertEquals("z6Mk...", rebuilt.verificationMethod?.single()?.publicKeyMultibase)
    }

    @Test
    fun relativeRelationshipRefMatchesAbsoluteTopLevelVmOnRoundTrip() {
        // VM declared absolute in verificationMethod[]; relationship references it with a
        // relative fragment. Decompose preserves the wire string; recompose must still treat
        // the VM as "referenced by string" so it is emitted top-level and does NOT collapse
        // to inline-only.
        val vm =
            VerificationMethod(
                id = "did:example:123#key-1",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "z6Mk...",
            )
        val doc =
            DidDocument(
                id = "did:example:123",
                verificationMethod = listOf(vm),
                authentication = listOf(VerificationMethodOrReference.fromReference("#key-1")),
            )
        val bindings = mapOf("did:example:123#key-1" to binding("alias-key-1"))
        val composite = (doc.toDidDetail(ctx(vmKmsBindings = bindings)) as Ok).value

        // Wire form of the reference is preserved verbatim on decompose.
        val rel = composite.verificationRelationship.single()
        assertEquals("#key-1", rel.entryRefDidUrl)

        val rebuilt = composite.toDidDocument()
        assertEquals(1, rebuilt.verificationMethod?.size, "VM must be emitted top-level on recompose")
        assertEquals("did:example:123#key-1", rebuilt.verificationMethod?.single()?.id)
        val auth = rebuilt.authentication ?: error("no authentication array")
        assertEquals("#key-1", auth.single().reference)
    }

    @Test
    fun inlineVmRoundTrips() {
        val inline =
            VerificationMethod(
                id = "did:example:123#embed-1",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "z6Mk...",
            )
        val doc =
            DidDocument(
                id = "did:example:123",
                authentication = listOf(VerificationMethodOrReference.fromEmbedded(inline)),
            )
        val bindings = mapOf("did:example:123#embed-1" to binding("alias-embed-1"))
        val composite = (doc.toDidDetail(ctx(vmKmsBindings = bindings)) as Ok).value
        assertEquals(1, composite.verificationMethod.size)
        val vmRec = composite.verificationMethod[0]
        val inlineIn = assertNotNull(vmRec.inlineInJson, "inlineInJson should be populated for inline-only VM")
        assertTrue(inlineIn.contains("authentication"))

        val rel = composite.verificationRelationship.single()
        assertEquals(vmRec.id, rel.entryEmbeddedVmId)
        assertNull(rel.entryRefDidUrl)

        val rebuilt = composite.toDidDocument()
        val auth = rebuilt.authentication ?: error("no authentication array")
        val reembedded = auth.single().embedded ?: error("expected embedded VM on round-trip")
        assertEquals("did:example:123#embed-1", reembedded.id)
        assertEquals("z6Mk...", reembedded.publicKeyMultibase)
        // Inline-only, no string ref ⇒ VM must NOT appear in top-level verificationMethod[].
        assertNull(rebuilt.verificationMethod)
    }

    @Test
    fun externalInlineVmWithoutKmsBindingRoundTrips() {
        val inline =
            VerificationMethod(
                id = "did:example:123#external-inline",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "zExternal",
            )
        val doc =
            DidDocument(
                id = "did:example:123",
                authentication = listOf(VerificationMethodOrReference.fromEmbedded(inline)),
            )

        val composite = (doc.toDidDetail(ctx(role = DidRole.EXTERNAL)) as Ok).value

        assertNull(composite.verificationMethod.single().kmsProviderId)
        assertEquals(
            "zExternal",
            composite
                .toDidDocument()
                .authentication
                ?.single()
                ?.embedded
                ?.publicKeyMultibase
        )
    }

    @Test
    fun conflictingDuplicateInlineVmRejected() {
        val first =
            VerificationMethod(
                id = "did:example:123#dup-inline",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "zFirst",
            )
        val second = first.copy(publicKeyMultibase = "zSecond")
        val doc =
            DidDocument(
                id = "did:example:123",
                authentication = listOf(VerificationMethodOrReference.fromEmbedded(first)),
                assertionMethod = listOf(VerificationMethodOrReference.fromEmbedded(second)),
            )
        val bindings = mapOf("did:example:123#dup-inline" to binding())

        val result = doc.toDidDetail(ctx(vmKmsBindings = bindings))

        assertTrue(result is Err, "expected Err for conflicting duplicate inline VM")
    }

    @Test
    fun duplicateVmIdInlineAndTopLevelRejected() {
        val vm =
            VerificationMethod(
                id = "did:example:123#k",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "z",
            )
        val doc =
            DidDocument(
                id = "did:example:123",
                verificationMethod = listOf(vm),
                authentication = listOf(VerificationMethodOrReference.fromEmbedded(vm)),
            )
        val bindings = mapOf("did:example:123#k" to binding())
        val result = doc.toDidDetail(ctx(vmKmsBindings = bindings))
        assertTrue(result is Err, "expected Err, got $result")
    }

    @Test
    fun missingKmsBindingRejected() {
        val vm =
            VerificationMethod(
                id = "did:example:123#key-1",
                type = "JsonWebKey2020",
                controller = "did:example:123",
                publicKeyMultibase = "z",
            )
        val doc = DidDocument(id = "did:example:123", verificationMethod = listOf(vm))
        val result = doc.toDidDetail(ctx()) // no bindings supplied
        assertTrue(result is Err, "expected Err when VmKmsBinding is missing")
    }

    // ---- Service polymorphism ----

    @Test
    fun serviceWithObjectEndpointRoundTrips() {
        val endpoint =
            buildJsonObject {
                put("uri", JsonPrimitive("https://svc.example"))
                put("accept", JsonPrimitive("application/did+json"))
            }
        val svc =
            DidService(
                id = "did:example:123#svc-1",
                type = listOf("LinkedDomains", "CredentialRegistry"),
                serviceEndpoint = endpoint,
            )
        val composite =
            (
                DidDocument(id = "did:example:123", service = listOf(svc))
                    .toDidDetail(ctx()) as Ok
            ).value
        assertEquals(1, composite.service.size)
        val rebuilt = composite.toDidDocument()
        val reSvc = rebuilt.service?.single() ?: error("no service on round-trip")
        assertEquals(listOf("LinkedDomains", "CredentialRegistry"), reSvc.type)
        assertEquals(endpoint, reSvc.serviceEndpoint)
    }

    @Test
    fun duplicateServiceIdRejected() {
        val a = DidService(id = "did:example:123#dup", type = listOf("X"), serviceEndpoint = JsonPrimitive("a"))
        val b = DidService(id = "did:example:123#dup", type = listOf("Y"), serviceEndpoint = JsonPrimitive("b"))
        val result = DidDocument(id = "did:example:123", service = listOf(a, b)).toDidDetail(ctx())
        assertTrue(result is Err)
    }

    // ---- Extension bag ----

    @Test
    fun documentExtensionBagRoundTripsThroughComposite() {
        val doc =
            DidDocument(
                id = "did:example:123",
                extensions = mapOf("my:key" to JsonPrimitive("value")),
            )
        val composite = (doc.toDidDetail(ctx()) as Ok).value
        assertNotNull(composite.record.extensionPropertiesJson)
        val rebuilt = composite.toDidDocument()
        assertEquals("value", rebuilt.extensions["my:key"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun emptyExtensionBagBecomesNullJson() {
        val doc = DidDocument(id = "did:example:123")
        val composite = (doc.toDidDetail(ctx()) as Ok).value
        assertNull(composite.record.extensionPropertiesJson)
    }

    // ---- ID mismatch ----

    @Test
    fun documentIdMismatchWithContextRejected() {
        val result = DidDocument(id = "did:example:OTHER").toDidDetail(ctx(did = "did:example:123"))
        assertTrue(result is Err)
    }
}
