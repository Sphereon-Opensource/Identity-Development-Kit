@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.impl

import com.sphereon.data.credential.definition.ClaimPath
import com.sphereon.data.credential.definition.ClaimValueKind
import com.sphereon.data.credential.definition.CredentialClaim
import com.sphereon.data.credential.definition.CredentialDefinition
import com.sphereon.data.credential.definition.CredentialDefinitionLifecycleStatus
import com.sphereon.data.credential.definition.CredentialTypeBindingRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CredentialDefinitionTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private fun claim(
        path: String,
        valueKind: ClaimValueKind = ClaimValueKind.STRING,
        labels: Map<String, String> = mapOf("en" to "Label for $path"),
    ) = CredentialClaim(path = ClaimPath(path), valueKind = valueKind, labels = labels)

    private fun definition(
        id: Uuid = Uuid.random(),
        tenantId: String = "tenant-a",
        name: String = "PID definition",
        version: Long = 1,
        claims: List<CredentialClaim> =
            listOf(
                claim("given_name"),
                claim("address", ClaimValueKind.OBJECT),
                claim("address.postal_code"),
            ),
    ) = CredentialDefinition(
        id = id,
        tenantId = tenantId,
        name = name,
        version = version,
        lifecycleStatus = CredentialDefinitionLifecycleStatus.DRAFT,
        credentialTypeBindingRef = CredentialTypeBindingRef("binding-pid"),
        claims = claims,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun constructsWithNestedClaimsAndRequiredLabels() {
        val def = definition()
        assertEquals(3, def.claims.size)
        val nested = def.claims.first { it.path.value == "address.postal_code" }
        assertEquals(listOf("address", "postal_code"), nested.path.segments)
        assertEquals("postal_code", nested.path.leaf)
        assertEquals("Label for address.postal_code", nested.labels["en"])
    }

    @Test
    fun rejectsEmptyLabelsMap() {
        assertFailsWith<IllegalArgumentException> {
            claim("given_name", labels = emptyMap())
        }
    }

    @Test
    fun rejectsBlankLabelValue() {
        assertFailsWith<IllegalArgumentException> {
            claim("given_name", labels = mapOf("en" to "  "))
        }
    }

    @Test
    fun rejectsBlankLocaleKey() {
        assertFailsWith<IllegalArgumentException> {
            claim("given_name", labels = mapOf("" to "Given name"))
        }
    }

    @Test
    fun rejectsMalformedNestedPath() {
        assertFailsWith<IllegalArgumentException> {
            claim("address..postal_code")
        }
    }

    @Test
    fun rejectsDuplicateClaimPaths() {
        assertFailsWith<IllegalArgumentException> {
            definition(claims = listOf(claim("given_name"), claim("given_name")))
        }
    }

    @Test
    fun rejectsVersionBelowOne() {
        assertFailsWith<IllegalArgumentException> {
            definition(version = 0)
        }
    }

    @Test
    fun storeRoundTrips() =
        runTest {
            val store = InMemoryCredentialDefinitionStore()
            val def = definition()

            assertTrue(store.save(def).isOk)
            val loaded = store.get("tenant-a", def.id)
            assertTrue(loaded.isOk)
            assertEquals(def, loaded.value)

            val listed = store.list("tenant-a")
            assertTrue(listed.isOk)
            assertEquals(1, listed.value.size)

            val deleted = store.delete("tenant-a", def.id)
            assertTrue(deleted.isOk)
            assertTrue(deleted.value)
            assertNull(store.get("tenant-a", def.id).value)
        }

    @Test
    fun storeIsolatesTenants() =
        runTest {
            val store = InMemoryCredentialDefinitionStore()
            val defA = definition(tenantId = "tenant-a")
            val defB = definition(tenantId = "tenant-b")

            store.save(defA)
            store.save(defB)

            assertNotNull(store.get("tenant-a", defA.id).value)
            assertNull(store.get("tenant-a", defB.id).value)
            assertEquals(1, store.list("tenant-a").value.size)
            assertEquals(1, store.list("tenant-b").value.size)
        }
}
