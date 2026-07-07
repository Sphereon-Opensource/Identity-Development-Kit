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

package com.sphereon.data.store.credential.design.validation

import com.sphereon.data.store.credential.design.command.CreateCredentialDesignArgs
import com.sphereon.data.store.credential.design.command.CreateIssuerDesignArgs
import com.sphereon.data.store.credential.design.command.CreateRenderVariantArgs
import com.sphereon.data.store.credential.design.command.CreateVerifierDesignArgs
import com.sphereon.data.store.credential.design.command.ImportExternalDesignArgs
import com.sphereon.data.store.credential.design.command.ResolveCredentialDesignArgs
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.CreateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.CreateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.CreateRenderVariantInput
import com.sphereon.data.store.credential.design.model.CreateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.CredentialDesignModuleConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.CredentialDesignRefreshConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignValidationConfig
import com.sphereon.data.store.credential.design.model.CredentialTypeDescriptor
import com.sphereon.data.store.credential.design.model.CredentialTypeFormat
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.ImportExternalDesignInput
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantKind
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.VctHostingMode
import io.konform.validation.Validation
import io.konform.validation.constraints.maxItems
import io.konform.validation.constraints.minItems
import io.konform.validation.constraints.minLength

fun createCredentialDesignValidator(config: CredentialDesignValidationConfig) =
    Validation<CreateCredentialDesignArgs> {
        CreateCredentialDesignArgs::tenantId { minLength(1) }
        CreateCredentialDesignArgs::input {
            CreateCredentialDesignInput::bindings {
                minItems(1) hint "at least one binding is required"
                maxItems(config.maxBindingsPerDesign)
            }
            run {
                constrain("credentialType is required and must carry the format-specific type value") { input ->
                    input.credentialType?.let(::hasRequiredCredentialTypeValue) == true
                }
                constrain("binding credential type hints must match the credential design type") { input ->
                    input.credentialType?.let { credentialType ->
                        input.bindings.all { binding -> bindingMatchesCredentialType(binding, credentialType) }
                    } ?: false
                }
                constrain("HOSTED VCT bindings require vct or credentialConfigurationId") { input ->
                    input.bindings.all(::hasHostedVctSeed)
                }
                constrain("EXTERNAL VCT bindings require absolute URI vct") { input ->
                    input.bindings.all(::hasValidExternalVct)
                }
                constrain("REGISTERED VCT bindings require vct") { input ->
                    input.bindings.all(::hasRegisteredVct)
                }
            }
            CreateCredentialDesignInput::displays {
                minItems(1) hint "at least one locale display is required"
                maxItems(config.maxDisplaysPerDesign)
            }
            CreateCredentialDesignInput::displays onEach {
                LocalizedCredentialDisplay::locale { minLength(2) hint "locale must be a valid BCP47 tag" }
                LocalizedCredentialDisplay::name { minLength(1) hint "display name is required" }
            }
            CreateCredentialDesignInput::claims { maxItems(config.maxClaimsPerDesign) }
            CreateCredentialDesignInput::claims onEach {
                ClaimPresentation::entryCodes ifPresent {
                    maxItems(config.maxEntryCodesPerClaim) hint "too many entry codes"
                }
            }
            CreateCredentialDesignInput::renderVariantIds { maxItems(config.maxRenderVariantsPerDesign) }
        }
    }

fun createIssuerDesignValidator(config: CredentialDesignValidationConfig) =
    Validation<CreateIssuerDesignArgs> {
        CreateIssuerDesignArgs::tenantId { minLength(1) }
        CreateIssuerDesignArgs::input {
            CreateIssuerDesignInput::bindings { minItems(1) hint "at least one binding is required" }
            run {
                constrain("HOSTED VCT bindings require vct or credentialConfigurationId") { input ->
                    input.bindings.all(::hasHostedVctSeed)
                }
                constrain("EXTERNAL VCT bindings require absolute URI vct") { input ->
                    input.bindings.all(::hasValidExternalVct)
                }
                constrain("REGISTERED VCT bindings require vct") { input ->
                    input.bindings.all(::hasRegisteredVct)
                }
            }
            CreateIssuerDesignInput::displays { minItems(1) hint "at least one locale display is required" }
        }
    }

fun createVerifierDesignValidator(config: CredentialDesignValidationConfig) =
    Validation<CreateVerifierDesignArgs> {
        CreateVerifierDesignArgs::tenantId { minLength(1) }
        CreateVerifierDesignArgs::input {
            CreateVerifierDesignInput::bindings { minItems(1) hint "at least one binding is required" }
            run {
                constrain("HOSTED VCT bindings require vct or credentialConfigurationId") { input ->
                    input.bindings.all(::hasHostedVctSeed)
                }
                constrain("EXTERNAL VCT bindings require absolute URI vct") { input ->
                    input.bindings.all(::hasValidExternalVct)
                }
                constrain("REGISTERED VCT bindings require vct") { input ->
                    input.bindings.all(::hasRegisteredVct)
                }
            }
            CreateVerifierDesignInput::displays { minItems(1) hint "at least one locale display is required" }
        }
    }

val createRenderVariantValidator =
    Validation<CreateRenderVariantArgs> {
        CreateRenderVariantArgs::tenantId { minLength(1) }
        CreateRenderVariantArgs::input {
            run {
                constrain("SIMPLE_CARD requires at least one visual field") { input ->
                    input.kind != RenderVariantKind.SIMPLE_CARD ||
                        listOfNotNull(input.logo, input.backgroundImage, input.backgroundColor, input.textColor).isNotEmpty()
                }
                constrain("SVG_TEMPLATE requires svgTemplate") { input ->
                    input.kind != RenderVariantKind.SVG_TEMPLATE || input.svgTemplate != null
                }
                constrain("W3C_RENDER_METHOD requires w3cRenderMethod") { input ->
                    input.kind != RenderVariantKind.W3C_RENDER_METHOD || input.w3cRenderMethod != null
                }
            }
        }
    }

val importExternalDesignValidator =
    Validation<ImportExternalDesignArgs> {
        ImportExternalDesignArgs::tenantId { minLength(1) }
        ImportExternalDesignArgs::input {
            ImportExternalDesignInput::bindings { minItems(1) hint "at least one binding is required" }
            run {
                constrain("HOSTED VCT bindings require vct or credentialConfigurationId") { input ->
                    input.bindings.all(::hasHostedVctSeed)
                }
                constrain("EXTERNAL VCT bindings require absolute URI vct") { input ->
                    input.bindings.all(::hasValidExternalVct)
                }
                constrain("REGISTERED VCT bindings require vct") { input ->
                    input.bindings.all(::hasRegisteredVct)
                }
            }
            ImportExternalDesignInput::sourceUrl { minLength(1) hint "sourceUrl is required" }
        }
    }

val resolveCredentialDesignValidator =
    Validation<ResolveCredentialDesignArgs> {
        run {
            constrain("designId or binding or bindingKey+bindingValue is required") { args ->
                args.input.designId != null || args.input.binding != null ||
                    (args.input.bindingKey != null && args.input.bindingValue != null)
            }
        }
    }

val credentialDesignRecordValidator =
    Validation<CredentialDesignRecord> {
        CredentialDesignRecord::credentialType {
            constrain("is required and must carry exactly one format-specific type value") { type ->
                type != null && hasRequiredCredentialTypeValue(type)
            }
        }
        run {
            constrain("binding credential type hints must match the credential design type") { record ->
                record.credentialType?.let { credentialType ->
                    record.bindings.all { binding -> bindingMatchesCredentialType(binding, credentialType) }
                } ?: false
            }
        }
        CredentialDesignRecord::bindings { minItems(1) }
        CredentialDesignRecord::displays { minItems(1) }
        CredentialDesignRecord::displays onEach {
            LocalizedCredentialDisplay::locale { minLength(2) }
            LocalizedCredentialDisplay::name { minLength(1) }
        }
    }

val renderVariantRecordValidator =
    Validation<RenderVariantRecord> {
        run {
            constrain("SIMPLE_CARD must have at least one visual field") { rv ->
                rv.kind != RenderVariantKind.SIMPLE_CARD ||
                    listOfNotNull(rv.logo, rv.backgroundImage, rv.backgroundColor, rv.textColor).isNotEmpty()
            }
            constrain("SVG_TEMPLATE must have svgTemplate") { rv ->
                rv.kind != RenderVariantKind.SVG_TEMPLATE || rv.svgTemplate != null
            }
            constrain("W3C_RENDER_METHOD must have w3cRenderMethod") { rv ->
                rv.kind != RenderVariantKind.W3C_RENDER_METHOD || rv.w3cRenderMethod != null
            }
        }
    }

val moduleConfigValidator =
    Validation<CredentialDesignModuleConfig> {
        CredentialDesignModuleConfig::validation {
            CredentialDesignValidationConfig::maxBindingsPerDesign { constrain("must be > 0") { it > 0 } }
            CredentialDesignValidationConfig::maxDisplaysPerDesign { constrain("must be > 0") { it > 0 } }
            CredentialDesignValidationConfig::maxClaimsPerDesign { constrain("must be > 0") { it > 0 } }
            CredentialDesignValidationConfig::maxEntryCodesPerClaim { constrain("must be > 0") { it > 0 } }
        }
        CredentialDesignModuleConfig::refresh {
            CredentialDesignRefreshConfig::defaultTtlSeconds { constrain("must be >= 0") { it >= 0 } }
            CredentialDesignRefreshConfig::maxAssetSizeBytes { constrain("must be > 0") { it > 0 } }
        }
    }

private fun hasHostedVctSeed(binding: DesignBinding): Boolean =
    binding.vctHostingMode != VctHostingMode.HOSTED ||
        !binding.vct.isNullOrBlank() ||
        !binding.credentialConfigurationId.isNullOrBlank()

private fun hasValidExternalVct(binding: DesignBinding): Boolean =
    binding.vctHostingMode != VctHostingMode.EXTERNAL ||
        binding.vct?.contains("://") == true

private fun hasRegisteredVct(binding: DesignBinding): Boolean =
    binding.vctHostingMode != VctHostingMode.REGISTERED ||
        !binding.vct.isNullOrBlank()

private fun hasRequiredCredentialTypeValue(type: CredentialTypeDescriptor): Boolean =
    credentialTypeDiscriminatorCount(type) == 1 &&
        when (type.format) {
            CredentialTypeFormat.SD_JWT_VC -> !type.vct.isNullOrBlank()
            CredentialTypeFormat.MSO_MDOC -> !type.docType.isNullOrBlank()
            CredentialTypeFormat.W3C_VC -> !type.type.isNullOrBlank()
        }

private fun credentialTypeDiscriminatorCount(type: CredentialTypeDescriptor): Int = listOf(type.vct, type.docType, type.type).count { !it.isNullOrBlank() }

private fun bindingMatchesCredentialType(
    binding: DesignBinding,
    type: CredentialTypeDescriptor
): Boolean {
    val bindingType = binding.credentialType
    if (bindingType != null && bindingType != type) return false
    if (!binding.vct.isNullOrBlank() && binding.vct != type.vct) return false
    if (!binding.docType.isNullOrBlank() && binding.docType != type.docType) return false
    if (!binding.type.isNullOrBlank() && binding.type != type.type) return false
    if (!binding.context.isNullOrBlank() && binding.context != type.context) return false
    return true
}
