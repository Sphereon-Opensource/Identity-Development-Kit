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

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("MemoryKeyStoreFactory", exact = true)
interface MemoryKeyStoreFactory : KeyStoreFactory {
    override fun create(config: KeyStoreConfig): KeyStore

    override fun create(
        config: KeyStoreConfig,
        execution: SessionExecution?,
    ): KeyStore

    @ContributesTo(AppScope::class)
    interface Graph {
        val memoryKeyStore: MemoryKeyStoreFactory
    }
}
