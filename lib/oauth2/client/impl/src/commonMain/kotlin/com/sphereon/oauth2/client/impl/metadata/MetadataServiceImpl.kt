package com.sphereon.oauth2.client.impl.metadata

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.command.FetchJwksArgs
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.client.service.MetadataService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of MetadataService that delegates to command implementations
 *
 * This service is session-scoped to support multi-tenancy, matching the pattern
 * used by other IDK services.
 *
 * @property fetchAuthorizationServerMetadataCommand Command for fetching authorization server metadata
 * @property fetchJwksCommand Command for fetching JWK Sets
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MetadataService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MetadataServiceImpl", exact = true)
class MetadataServiceImpl(
    private val fetchAuthorizationServerMetadataCommand: FetchAuthorizationServerMetadataCommand,
    private val fetchJwksCommand: FetchJwksCommand
) : MetadataService {

    /**
     * DI component interface for MetadataService
     */
    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val metadataService: MetadataService
    }

    /**
     * Implementation of Commands that exposes the injected command instances.
     */
    inner class CommandsImpl : MetadataService.Commands {
        override val fetchAuthorizationServerMetadata = this@MetadataServiceImpl.fetchAuthorizationServerMetadataCommand
        override val fetchJwks = this@MetadataServiceImpl.fetchJwksCommand
    }

    override val commands: MetadataService.Commands = CommandsImpl()

    // Delegate service methods to commands

    override suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError> {
        return fetchAuthorizationServerMetadataCommand.execute(FetchServerMetadataArgs(issuer))
    }

    override suspend fun fetchJwks(jwksUri: String): IdkResult<JwkSet, IdkError> {
        return fetchJwksCommand.execute(FetchJwksArgs(jwksUri))
    }
}
