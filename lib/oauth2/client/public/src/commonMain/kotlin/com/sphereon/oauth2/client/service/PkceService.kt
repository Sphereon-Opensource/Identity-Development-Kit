package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for PKCE (Proof Key for Code Exchange) operations (RFC 7636)
 *
 * Provides functionality for creating and verifying PKCE challenge/verifier pairs
 * used in the OAuth 2.0 authorization code flow to prevent authorization code
 * interception attacks.
 *
 * This service follows the Command/Service pattern, delegating to command implementations
 * for testability and consistency with other IDK services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PkceService", exact = true)
@JsExportCompat
interface PkceService {

    suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError>
    suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError>

    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all PKCE commands
     */
    @JsExportIgnoreCompat
    interface Commands {
        val createPkce: CreatePkceCommand
        val verifyPkce: VerifyPkceCommand
    }

}
