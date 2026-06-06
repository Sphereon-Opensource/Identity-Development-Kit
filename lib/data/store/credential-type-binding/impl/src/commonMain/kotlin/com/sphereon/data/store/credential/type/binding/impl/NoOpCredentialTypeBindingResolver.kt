package com.sphereon.data.store.credential.type.binding.impl

import com.sphereon.credential.issuance.pipeline.SemanticAttributeSetRef
import com.sphereon.data.store.credential.type.binding.CredentialTypeBinding
import com.sphereon.data.store.credential.type.binding.CredentialTypeBindingResolver
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * No-op default [CredentialTypeBindingResolver] — resolves nothing. A persisted-registry
 * implementation replaces this binding via `replaces` when one is on the classpath.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialTypeBindingResolver>())
class NoOpCredentialTypeBindingResolver : CredentialTypeBindingResolver {
    override suspend fun resolveByConfigId(credentialConfigurationId: String): CredentialTypeBinding? = null

    override suspend fun resolveBySemanticSet(semanticAttributeSetRef: SemanticAttributeSetRef,): List<CredentialTypeBinding> = emptyList()
}
