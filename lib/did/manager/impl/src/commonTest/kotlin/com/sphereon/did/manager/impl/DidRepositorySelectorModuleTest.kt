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
 */

package com.sphereon.did.manager.impl

import app.cash.sqldelight.db.SqlDriver
import com.sphereon.did.persistence.DidPersistenceConfig
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidRepositoryFactory
import com.sphereon.did.persistence.memory.MemoryDidRepositoryFactory
import com.sphereon.did.persistence.memory.MemoryDidRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the pure selector function. The Metro `@Provides` wrapper just composes
 * [DidPersistenceConfigBinder.bind] with [DidRepositorySelector.select]; full ConfigService
 * stubbing isn't needed to validate selector behaviour.
 */
class DidRepositorySelectorModuleTest {
    @Test
    fun defaultMemoryConfig_picksMemoryFactory() {
        val factory = MemoryDidRepositoryFactory()
        val repository =
            DidRepositorySelector.select(
                config = DidPersistenceConfig(),
                factories = mapOf(DidPersistenceConfig.TYPE_MEMORY to factory),
            )
        assertTrue(repository is MemoryDidRepositoryImpl)
    }

    @Test
    fun explicitMemoryType_picksMemoryFactory() {
        val factory = MemoryDidRepositoryFactory()
        val repository =
            DidRepositorySelector.select(
                config = DidPersistenceConfig(type = DidPersistenceConfig.TYPE_MEMORY),
                factories = mapOf(DidPersistenceConfig.TYPE_MEMORY to factory),
            )
        assertTrue(repository is MemoryDidRepositoryImpl)
    }

    @Test
    fun customDialect_picksContributedFactory_andForwardsConfig() {
        val captured = mutableListOf<DidPersistenceConfig>()
        val customRepo = MemoryDidRepositoryImpl()
        val customFactory =
            object : DidRepositoryFactory {
                override val type = "exotic"

                override fun createRepository(config: DidPersistenceConfig): DidRepository {
                    captured += config
                    return customRepo
                }

                override fun createRepository(driver: SqlDriver?): DidRepository = customRepo
            }
        val input =
            DidPersistenceConfig(
                type = "exotic",
                connectionUrl = "weird://there",
                username = "alice",
                poolSize = 8,
                properties = mapOf("flavor" to "blue"),
            )
        val repository = DidRepositorySelector.select(input, mapOf("exotic" to customFactory))
        assertSame(customRepo, repository)
        assertEquals(input, captured.single())
    }

    @Test
    fun unknownType_failsWithDialectListInMessage() {
        val failure =
            assertFailsWith<IllegalStateException> {
                DidRepositorySelector.select(
                    config = DidPersistenceConfig(type = DidPersistenceConfig.TYPE_POSTGRESQL),
                    factories = mapOf(DidPersistenceConfig.TYPE_MEMORY to MemoryDidRepositoryFactory()),
                )
            }
        val message = failure.message ?: ""
        assertTrue("postgresql" in message, "message should mention requested dialect: $message")
        assertTrue("memory" in message, "message should list contributed dialects: $message")
    }

    @Test
    fun emptyFactoryMap_emitsHelpfulError() {
        val failure =
            assertFailsWith<IllegalStateException> {
                DidRepositorySelector.select(DidPersistenceConfig(), emptyMap())
            }
        val message = failure.message ?: ""
        assertTrue("(none)" in message, "message should indicate no dialects contributed: $message")
    }
}
