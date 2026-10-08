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

package com.sphereon.conf.theme.core.validation

import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeValidatorScopeRulesTest {
    private fun definition(
        scope: ThemeScope,
        productType: ProductType? = null,
        applicationId: String? = null,
    ): ThemeDefinition =
        ThemeDefinition(
            id = "def-1",
            name = "Definition",
            scope = scope,
            productType = productType,
            applicationId = applicationId,
        )

    @Test
    fun productScopeWithProductTypeIsValid() {
        val result = ThemeValidator.validate(definition(ThemeScope.PRODUCT, productType = ProductType.WEB_WALLET))
        assertTrue(result.valid, "PRODUCT definition with productType should be valid: ${result.errors}")
    }

    @Test
    fun productScopeRequiresProductType() {
        val result = ThemeValidator.validate(definition(ThemeScope.PRODUCT))
        assertFalse(result.valid, "PRODUCT definition without productType should be invalid")
        assertTrue(result.errors.any { it.contains("productType") }, "Errors should mention productType: ${result.errors}")
    }

    @Test
    fun productScopeRejectsApplicationId() {
        val result =
            ThemeValidator.validate(
                definition(ThemeScope.PRODUCT, productType = ProductType.PORTAL, applicationId = "app-1"),
            )
        assertFalse(result.valid, "PRODUCT definition carrying an applicationId should be invalid")
        assertTrue(result.errors.any { it.contains("applicationId") }, "Errors should mention applicationId: ${result.errors}")
    }

    @Test
    fun applicationScopeWithApplicationIdIsValid() {
        val result = ThemeValidator.validate(definition(ThemeScope.APPLICATION, applicationId = "app-1"))
        assertTrue(result.valid, "APPLICATION definition with applicationId should be valid: ${result.errors}")
    }

    @Test
    fun applicationScopeRequiresApplicationId() {
        val result = ThemeValidator.validate(definition(ThemeScope.APPLICATION))
        assertFalse(result.valid, "APPLICATION definition without applicationId should be invalid")
        assertTrue(result.errors.any { it.contains("applicationId") }, "Errors should mention applicationId: ${result.errors}")
    }

    @Test
    fun applicationScopeRejectsProductType() {
        val result =
            ThemeValidator.validate(
                definition(ThemeScope.APPLICATION, productType = ProductType.WEB_WALLET, applicationId = "app-1"),
            )
        assertFalse(result.valid, "APPLICATION definition carrying a productType should be invalid")
        assertTrue(result.errors.any { it.contains("productType") }, "Errors should mention productType: ${result.errors}")
    }

    @Test
    fun tenantScopeRejectsProductTypeAndApplicationId() {
        val result =
            ThemeValidator.validate(
                definition(ThemeScope.TENANT, productType = ProductType.WEB_WALLET, applicationId = "app-1"),
            )
        assertFalse(result.valid, "TENANT definition carrying scope keys should be invalid")
        assertTrue(result.errors.any { it.contains("productType") }, "Errors should mention productType: ${result.errors}")
        assertTrue(result.errors.any { it.contains("applicationId") }, "Errors should mention applicationId: ${result.errors}")
    }

    @Test
    fun tenantScopeWithoutKeysIsValid() {
        val result = ThemeValidator.validate(definition(ThemeScope.TENANT))
        assertTrue(result.valid, "TENANT definition without scope keys should be valid: ${result.errors}")
    }

    @Test
    fun systemScopeRejectsScopeKeys() {
        val result = ThemeValidator.validate(definition(ThemeScope.SYSTEM, productType = ProductType.CUSTOM))
        assertFalse(result.valid, "SYSTEM definition carrying a productType should be invalid")
    }

    @Test
    fun principalScopeRejectsScopeKeys() {
        val result = ThemeValidator.validate(definition(ThemeScope.PRINCIPAL, applicationId = "app-1"))
        assertFalse(result.valid, "PRINCIPAL definition carrying an applicationId should be invalid")
    }
}
