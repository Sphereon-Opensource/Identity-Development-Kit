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

package com.sphereon.data.store.vault.portability

import com.sphereon.data.store.vault.VaultImportKeyDisposition
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultImportDispositionTest {
    @Test
    fun `unavailable non-exportable holder key requires reissuance`() {
        assertEquals(
            VaultImportKeyDisposition.REISSUANCE_REQUIRED,
            VaultImportDispositionMapper.forHolderKey(
                holderKeyRequired = true,
                holderKeyAvailable = false,
                holderKeyExportable = false,
            ),
        )
    }

    @Test
    fun `available or unnecessary holder key remains usable`() {
        assertEquals(
            VaultImportKeyDisposition.AVAILABLE,
            VaultImportDispositionMapper.forHolderKey(true, true, false),
        )
        assertEquals(
            VaultImportKeyDisposition.AVAILABLE,
            VaultImportDispositionMapper.forHolderKey(false, false, false),
        )
    }
}
