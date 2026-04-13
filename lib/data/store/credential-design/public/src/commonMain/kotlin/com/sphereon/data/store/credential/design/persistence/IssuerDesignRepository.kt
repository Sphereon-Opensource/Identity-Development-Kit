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

package com.sphereon.data.store.credential.design.persistence

import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import kotlin.uuid.Uuid

interface IssuerDesignRepository {
    suspend fun findById(
        tenantId: String,
        id: Uuid,
    ): IssuerDesignRecord?

    suspend fun findByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): List<IssuerDesignRecord>

    suspend fun findByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): List<IssuerDesignRecord>

    suspend fun findAll(
        tenantId: String,
        filter: DesignFilter,
    ): List<IssuerDesignRecord>

    suspend fun create(record: IssuerDesignRecord): IssuerDesignRecord

    suspend fun update(record: IssuerDesignRecord): IssuerDesignRecord

    suspend fun delete(
        tenantId: String,
        id: Uuid,
    ): Boolean
}
