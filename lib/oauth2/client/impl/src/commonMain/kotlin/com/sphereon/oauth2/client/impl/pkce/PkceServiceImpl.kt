package com.sphereon.oauth2.client.impl.pkce

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.service.PkceService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of PkceService that delegates to command implementations (RFC 7636)
 *
 * This service follows the Command/Service pattern for consistency with other IDK services
 * like SdJwtService and JwtService.
 *
 * @property createPkceCommand Command for creating PKCE challenge/verifier pairs
 * @property verifyPkceCommand Command for verifying PKCE during token exchange
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<PkceService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PkceServiceImpl", exact = true)
class PkceServiceImpl(
    private val createPkceCommand: CreatePkceCommand,
    private val verifyPkceCommand: VerifyPkceCommand
) : PkceService {

    /**
     * DI component interface for PkceService
     */
    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val pkceService: PkceService
    }

    /**
     * Implementation of Commands that exposes the injected command instances
     */
    inner class CommandsImpl : PkceService.Commands {
        override val createPkce: CreatePkceCommand = this@PkceServiceImpl.createPkceCommand
        override val verifyPkce: VerifyPkceCommand = this@PkceServiceImpl.verifyPkceCommand
    }

    override val commands: PkceService.Commands = CommandsImpl()

    // Delegate service methods to commands

    override suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError> =
        createPkceCommand.execute(args)

    override suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError> =
        verifyPkceCommand.execute(args)
}
