/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.core.defaults.conf

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for implementation constants.
 */
class PropertiesFilePropertySourceConstantsTest {

    @Test
    fun appPropertySourceHasCorrectName() {
        assertEquals("properties-file-app", PropertiesFileAppPropertySourceImpl.NAME)
    }

    @Test
    fun appPropertySourceHasCorrectFilePrefix() {
        assertEquals("application", PropertiesFileAppPropertySourceImpl.FILE_PREFIX)
    }

    @Test
    fun tenantPropertySourceHasCorrectName() {
        assertEquals("properties-file-tenant", PropertiesFileTenantPropertySourceImpl.NAME)
    }

    @Test
    fun tenantPropertySourceHasCorrectFilePrefix() {
        assertEquals("tenant", PropertiesFileTenantPropertySourceImpl.FILE_PREFIX)
    }

    @Test
    fun tenantPropertySourceHasCorrectTenantDir() {
        assertEquals("tenant", PropertiesFileTenantPropertySourceImpl.TENANT_DIR)
    }

    @Test
    fun principalPropertySourceHasCorrectName() {
        assertEquals("properties-file-principal", PropertiesFilePrincipalPropertySourceImpl.NAME)
    }

    @Test
    fun principalPropertySourceHasCorrectFilePrefix() {
        assertEquals("principal", PropertiesFilePrincipalPropertySourceImpl.FILE_PREFIX)
    }

    @Test
    fun principalPropertySourceHasCorrectTenantDir() {
        assertEquals("tenant", PropertiesFilePrincipalPropertySourceImpl.TENANT_DIR)
    }

    @Test
    fun principalPropertySourceHasCorrectPrincipalDir() {
        assertEquals("principal", PropertiesFilePrincipalPropertySourceImpl.PRINCIPAL_DIR)
    }
}
