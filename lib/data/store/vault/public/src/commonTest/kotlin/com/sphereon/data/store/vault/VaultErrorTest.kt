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

class VaultErrorTest {
    @Test
    fun `zero access plaintext failure has canonical typed code`() {
        val error =
            VaultError.ClientUnwrapRequired(
                vaultId = VaultId("vault-1"),
                objectId = VaultObjectId("object-1"),
                versionId = VaultVersionId("version-1"),
                protectedPackageRef = "protected:package-1",
            )

        assertEquals(VaultError.Kind.CLIENT_UNWRAP_REQUIRED, error.kind)
        assertEquals("VAULT_CLIENT_UNWRAP_REQUIRED", error.code)
        assertEquals("VAULT_CLIENT_UNWRAP_REQUIRED", error.toIdkError().code)
    }

    @Test
    fun `zero access failure requires a protected package reference`() {
        assertFailsWith<IllegalArgumentException> {
            VaultError.ClientUnwrapRequired(
                vaultId = VaultId("vault-1"),
                objectId = VaultObjectId("object-1"),
                versionId = VaultVersionId("version-1"),
                protectedPackageRef = " ",
            )
        }
    }
}
