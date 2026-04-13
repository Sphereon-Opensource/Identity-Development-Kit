package com.sphereon.oauth2.client.impl.introspection

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.IntrospectTokenArgs
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.client.service.IntrospectionService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of IntrospectionService that delegates to command implementations.
 *
 * This service is session-scoped to support multi-tenancy, matching the pattern
 * used by other IDK services.
 *
 * @property introspectTokenCommand Command for introspecting tokens
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IntrospectionService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("IntrospectionServiceImpl", exact = true)
class IntrospectionServiceImpl(
    private val introspectTokenCommand: IntrospectTokenCommand
) : IntrospectionService {

    /**
     * Implementation of Commands that exposes the injected command instances.
     */
    inner class CommandsImpl : IntrospectionService.Commands {
        override val introspectToken: IntrospectTokenCommand = this@IntrospectionServiceImpl.introspectTokenCommand
    }

    override val commands: IntrospectionService.Commands = CommandsImpl()

    // Delegate service method to command

    override suspend fun introspectToken(
        args: IntrospectTokenArgs
    ): IdkResult<TokenIntrospectionResponse, IdkError> {
        return introspectTokenCommand.execute(args)
    }
}
