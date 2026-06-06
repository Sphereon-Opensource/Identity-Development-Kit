/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.persistence.memory

import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.testfixtures.DidRepositoryContract
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

/**
 * Validates [MemoryDidRepositoryImpl] against the cross-dialect [DidRepositoryContract] —
 * the same suite that exercises SQLite, PostgreSQL, and MySQL implementations. Lifting Memory
 * onto the shared contract closes the IDK-18 review gap where the in-memory dev dialect
 * carried only bespoke smoke coverage.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryDidRepositoryContractTest : DidRepositoryContract() {
    private lateinit var repo: MemoryDidRepositoryImpl

    @BeforeEach
    fun setupEach() {
        repo = MemoryDidRepositoryImpl()
    }

    override fun repository(): DidRepository = repo

    override fun clearRows() {
        // A fresh instance per test gives the same effect as truncating the SQL stores.
        repo = MemoryDidRepositoryImpl()
    }
}
