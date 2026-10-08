/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VaultIdentifiersTest {
    @Test
    fun `opaque identities reject path material and whitespace`() {
        assertFailsWith<IllegalArgumentException> { VaultId("") }
        assertFailsWith<IllegalArgumentException> { VaultId("tenant/vault") }
        assertFailsWith<IllegalArgumentException> { VaultObjectId("object id") }
        assertFailsWith<IllegalArgumentException> { VaultVersionId("../version") }
        assertFailsWith<IllegalArgumentException> { VaultRevision(" revision") }

        assertEquals("vault:personal-01", VaultId("vault:personal-01").value)
        assertEquals("\"etag/base64==\"", VaultRevision("\"etag/base64==\"").value)
    }

    @Test
    fun `paths accept only canonical absolute representation`() {
        listOf(
            "",
            "relative/file",
            "/folder/",
            "/folder//file",
            "/folder/./file",
            "/folder/../file",
            "/folder\\file",
            "/folder\u0000/file",
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>("Expected '$invalid' to be rejected") {
                VaultPath(invalid)
            }
        }
    }

    @Test
    fun `path helpers preserve canonical paths`() {
        val file = VaultPath.ROOT.child("documents").child("credential.json")

        assertEquals("/documents/credential.json", file.value)
        assertEquals("credential.json", file.name)
        assertEquals(VaultPath("/documents"), file.parent())
        assertEquals(VaultPath.ROOT, VaultPath("/documents").parent())
        assertNull(VaultPath.ROOT.parent())
    }

    @Test
    fun `selector requires exactly one stable identity or mutable path`() {
        val vaultId = VaultId("vault-1")

        assertFailsWith<IllegalArgumentException> { VaultObjectSelector(vaultId) }
        assertFailsWith<IllegalArgumentException> {
            VaultObjectSelector(vaultId, VaultObjectId("object-1"), VaultPath("/file"))
        }

        assertEquals(
            VaultObjectId("object-1"),
            VaultObjectSelector(vaultId, objectId = VaultObjectId("object-1")).objectId,
        )
    }
}
