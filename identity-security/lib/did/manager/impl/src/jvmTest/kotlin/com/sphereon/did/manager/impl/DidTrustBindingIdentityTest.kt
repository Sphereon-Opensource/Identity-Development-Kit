package com.sphereon.did.manager.impl

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.did.DidTrustValidator
import com.sphereon.trust.did.ValidateDidTrustCommand
import com.sphereon.trust.did.extractor.DidTrustEntityInfoExtractor
import dev.zacsweers.metro.ContributesTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@ContributesTo(SessionScope::class)
interface DidTrustBindingIdentityGraph {
    val didTrustValidator: DidTrustValidator
    val didTrustExtractor: DidTrustEntityInfoExtractor
    val validators: Set<TrustValidationService>
    val extractors: Set<EntityInfoExtractor>
    val validateDidTrustCommand: ValidateDidTrustCommand
    val commands: Map<String, ServiceCommand<*, *, *>>
}

class DidTrustBindingIdentityTest {
    @Test
    fun `DID interfaces and registry entries share their session instances`() {
        val app = createDidManagerTestAppGraph(this)
        try {
            val user = app.userContextManager.getAnonymous()
            fun graph(id: String) = user.sessionContextManager
                .createOrGetFromId(id, principalType = PrincipalType.ANONYMOUS)
                .graph as DidTrustBindingIdentityGraph
            val first = graph("did-trust-bindings-first")
            val second = graph("did-trust-bindings-second")
            val validators = first.validators.filterIsInstance<DidTrustValidator>()
            val extractors = first.extractors.filterIsInstance<DidTrustEntityInfoExtractor>()
            assertEquals(1, validators.size)
            assertEquals(1, extractors.size)
            assertSame(first.didTrustValidator, validators.single())
            assertSame(first.didTrustExtractor, extractors.single())
            assertSame(first.validateDidTrustCommand, first.commands[ValidateDidTrustCommand.COMMAND_ID])
            assertSame(first.didTrustValidator, first.didTrustValidator)
            assertNotSame(first.didTrustValidator, second.didTrustValidator)
            assertNotSame(first.didTrustExtractor, second.didTrustExtractor)
        } finally {
            app.destroy()
        }
    }
}
