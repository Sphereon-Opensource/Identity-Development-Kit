package com.sphereon.data.store.credential.type.binding

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.credential.design.model.SemanticAttributeSetRef

/**
 * Resolves [CredentialTypeBinding] records for the session's current tenant. Implementations
 * are session-scoped and read tenant context from the execution context; the SPI surface
 * never carries a tenant id.
 *
 * A no-op default is contributed in this layer; a persisted-registry implementation may
 * replace it via the DI `replaces` mechanism when one is on the classpath.
 */
@JsExportCompat
interface CredentialTypeBindingResolver {
    /**
     * Returns the binding whose [CredentialTypeBinding.credentialConfigurationId] matches
     * [credentialConfigurationId], or `null` when none is registered.
     */
    suspend fun resolveByConfigId(credentialConfigurationId: String): CredentialTypeBinding?

    /**
     * Returns every binding registered against [semanticAttributeSetRef]. A single semantic
     * attribute set may bind to multiple credential types (e.g. the same set ships as both
     * an SD-JWT VC and an mdoc credential).
     */
    suspend fun resolveBySemanticSet(semanticAttributeSetRef: SemanticAttributeSetRef): List<CredentialTypeBinding>
}
