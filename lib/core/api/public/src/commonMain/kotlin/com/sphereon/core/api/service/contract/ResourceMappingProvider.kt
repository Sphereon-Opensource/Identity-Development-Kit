package com.sphereon.core.api.service.contract

/**
 * Optional interface for input DTOs that can describe their resource mappings
 * for policy evaluation at runtime.
 *
 * When implemented, the EDK policy layer can automatically extract resource
 * instances without a custom ResourceMapper. Falls back to
 * [ResourceTargetDescriptor]-based mapping when not implemented.
 *
 * For most commands, this replaces the need for separate ResourceMapper classes.
 * Complex cases (conditional mapping, computed resource IDs) implement this
 * interface manually on the input DTO.
 */
interface ResourceMappingProvider {
    fun toResourceInstances(): List<ResourceInstance>
}
