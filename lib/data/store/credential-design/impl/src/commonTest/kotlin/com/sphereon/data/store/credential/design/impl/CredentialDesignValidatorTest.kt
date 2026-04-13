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

package com.sphereon.data.store.credential.design.impl

import com.sphereon.data.store.credential.design.command.CreateCredentialDesignArgs
import com.sphereon.data.store.credential.design.command.CreateIssuerDesignArgs
import com.sphereon.data.store.credential.design.command.CreateRenderVariantArgs
import com.sphereon.data.store.credential.design.command.ResolveCredentialDesignArgs
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.CreateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.CreateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.CreateRenderVariantInput
import com.sphereon.data.store.credential.design.model.CredentialDesignModuleConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignValidationConfig
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.EntityLocaleDesign
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantKind
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.validation.createCredentialDesignValidator
import com.sphereon.data.store.credential.design.validation.createIssuerDesignValidator
import com.sphereon.data.store.credential.design.validation.createRenderVariantValidator
import com.sphereon.data.store.credential.design.validation.moduleConfigValidator
import com.sphereon.data.store.credential.design.validation.resolveCredentialDesignValidator
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlin.test.Test
import kotlin.test.assertTrue

class CredentialDesignValidatorTest {
    private val defaultConfig = CredentialDesignValidationConfig()

    @Test
    fun validCreateCredentialDesignPasses() {
        val validator = createCredentialDesignValidator(defaultConfig)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "tenant-1",
                input =
                    CreateCredentialDesignInput(
                        bindings = listOf(DesignBinding(vct = "urn:example:pid")),
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Person ID")),
                    ),
            )
        assertTrue(validator(args) is Valid)
    }

    @Test
    fun emptyTenantIdFails() {
        val validator = createCredentialDesignValidator(defaultConfig)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "",
                input =
                    CreateCredentialDesignInput(
                        bindings = listOf(DesignBinding(vct = "test")),
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Test")),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun emptyBindingsFails() {
        val validator = createCredentialDesignValidator(defaultConfig)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "t1",
                input =
                    CreateCredentialDesignInput(
                        bindings = emptyList(),
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Test")),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun emptyDisplaysFails() {
        val validator = createCredentialDesignValidator(defaultConfig)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "t1",
                input =
                    CreateCredentialDesignInput(
                        bindings = listOf(DesignBinding(vct = "test")),
                        displays = emptyList(),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun tooManyBindingsFails() {
        val config = CredentialDesignValidationConfig(maxBindingsPerDesign = 2)
        val validator = createCredentialDesignValidator(config)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "t1",
                input =
                    CreateCredentialDesignInput(
                        bindings = (1..3).map { DesignBinding(vct = "vct-$it") },
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Test")),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun tooManyEntryCodesFails() {
        val config = CredentialDesignValidationConfig(maxEntryCodesPerClaim = 3)
        val validator = createCredentialDesignValidator(config)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "t1",
                input =
                    CreateCredentialDesignInput(
                        bindings = listOf(DesignBinding(vct = "test")),
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Test")),
                        claims =
                            listOf(
                                ClaimPresentation(
                                    path = listOf(ClaimPathSegment.Property("status")),
                                    labels = emptyList(),
                                    entryCodes = listOf("A", "B", "C", "D"),
                                ),
                            ),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun shortLocaleInDisplayFails() {
        val validator = createCredentialDesignValidator(defaultConfig)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "t1",
                input =
                    CreateCredentialDesignInput(
                        bindings = listOf(DesignBinding(vct = "test")),
                        displays = listOf(LocalizedCredentialDisplay(locale = "x", name = "Test")),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun emptyDisplayNameFails() {
        val validator = createCredentialDesignValidator(defaultConfig)
        val args =
            CreateCredentialDesignArgs(
                tenantId = "t1",
                input =
                    CreateCredentialDesignInput(
                        bindings = listOf(DesignBinding(vct = "test")),
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "")),
                    ),
            )
        assertTrue(validator(args) is Invalid)
    }

    @Test
    fun validIssuerDesignPasses() {
        val validator = createIssuerDesignValidator(defaultConfig)
        val args =
            CreateIssuerDesignArgs(
                tenantId = "t1",
                input =
                    CreateIssuerDesignInput(
                        bindings = listOf(DesignBinding(issuerUri = "https://issuer.example.com")),
                        displays = listOf(EntityLocaleDesign(locale = "en", displayName = "Example Issuer")),
                    ),
            )
        assertTrue(validator(args) is Valid)
    }

    @Test
    fun simpleCardWithNoVisualFieldsFails() {
        val result =
            createRenderVariantValidator(
                CreateRenderVariantArgs(
                    tenantId = "t1",
                    input = CreateRenderVariantInput(kind = RenderVariantKind.SIMPLE_CARD),
                ),
            )
        assertTrue(result is Invalid)
    }

    @Test
    fun simpleCardWithColorPasses() {
        val result =
            createRenderVariantValidator(
                CreateRenderVariantArgs(
                    tenantId = "t1",
                    input = CreateRenderVariantInput(kind = RenderVariantKind.SIMPLE_CARD, backgroundColor = "#FF0000"),
                ),
            )
        assertTrue(result is Valid)
    }

    @Test
    fun svgTemplateWithoutTemplateFails() {
        val result =
            createRenderVariantValidator(
                CreateRenderVariantArgs(
                    tenantId = "t1",
                    input = CreateRenderVariantInput(kind = RenderVariantKind.SVG_TEMPLATE),
                ),
            )
        assertTrue(result is Invalid)
    }

    @Test
    fun resolveRequiresDesignIdOrBinding() {
        val result =
            resolveCredentialDesignValidator(
                ResolveCredentialDesignArgs(
                    tenantId = "t1",
                    input = ResolveCredentialDesignInput(),
                ),
            )
        assertTrue(result is Invalid)
    }

    @Test
    fun resolveWithDesignIdPasses() {
        val result =
            resolveCredentialDesignValidator(
                ResolveCredentialDesignArgs(
                    tenantId = "t1",
                    input = ResolveCredentialDesignInput(designId = kotlin.uuid.Uuid.random()),
                ),
            )
        assertTrue(result is Valid)
    }

    @Test
    fun moduleConfigValidatorRejectsZeroMaxBindings() {
        val config =
            CredentialDesignModuleConfig(
                validation = CredentialDesignValidationConfig(maxBindingsPerDesign = 0),
            )
        assertTrue(moduleConfigValidator(config) is Invalid)
    }

    @Test
    fun moduleConfigValidatorAcceptsDefaults() {
        assertTrue(moduleConfigValidator(CredentialDesignModuleConfig()) is Valid)
    }
}
