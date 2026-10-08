/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.api.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.model.Origin
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKey
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKeyResponse
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ========== Input DTOs ==========

/**
 * Input for the GetKey service command.
 *
 * @property aliasOrKid Key alias or kid to retrieve
 * @property providerId Optional provider ID to scope the key lookup
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetKeyInput", exact = true)
@Serializable
data class GetKeyInput(
    val aliasOrKid: String,
    val providerId: String? = null,
)

/**
 * Input for the ListKeys service command.
 *
 * @property providerId Optional provider ID to filter keys
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListKeysInput", exact = true)
@Serializable
data class ListKeysInput(
    val providerId: String? = null,
)

/**
 * Input for the DeleteKey service command.
 *
 * @property aliasOrKid Key alias or kid to delete
 * @property providerId Optional provider ID to scope the key deletion
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyInput", exact = true)
@Serializable
data class DeleteKeyInput(
    val aliasOrKid: String,
    val providerId: String? = null,
)

/**
 * Output for the DeleteKey service command.
 *
 * @property aliasOrKid The deleted key alias or kid
 * @property deleted Whether the key was successfully deleted
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyOutput", exact = true)
@Serializable
data class DeleteKeyOutput(
    val aliasOrKid: String,
    val deleted: Boolean = true,
)

/**
 * Input for the RegisterKeyReference service command.
 *
 * @property providerId The provider that holds the key
 * @property alias Alias to register the key under
 * @property kid Optional kid for the key
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RegisterKeyReferenceInput", exact = true)
@Serializable
data class RegisterKeyReferenceInput(
    val providerId: String,
    val alias: String,
    val kid: String? = null,
)

/**
 * Output for the RegisterKeyReference service command.
 *
 * @property registered Whether the key was successfully registered
 * @property alias The alias the key was registered under
 * @property providerId The provider that holds the key
 * @property kid Optional kid for the key
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RegisterKeyReferenceResponse", exact = true)
@Serializable
data class RegisterKeyReferenceResponse(
    val registered: Boolean,
    val alias: String,
    val providerId: String,
    val kid: String? = null,
    val origin: Origin = Origin.EXTERNAL,
    val controlMode: ResourceControlMode = ResourceControlMode.EXTERNALLY_MANAGED,
)

// ========== Service Command Interfaces ==========

/**
 * Service command for getting a key by alias or kid.
 *
 * GET /keys/{aliasOrKid}
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetKeyServiceCommand", exact = true)
interface GetKeyServiceCommand :
    ServiceCommand<GetKeyInput, GetKeyResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.keys.get"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/keys/{aliasOrKid}",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Keys"),
                summary = "Get a key by alias or kid",
            )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

/**
 * Service command for listing all keys.
 *
 * GET /keys
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListKeysServiceCommand", exact = true)
interface ListKeysServiceCommand :
    ServiceCommand<ListKeysInput, ListKeysResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.keys.list"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/keys",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Keys"),
                summary = "List all keys",
            )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

/**
 * Service command for importing externally supplied key material.
 *
 * POST /keys/import
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ImportKeyServiceCommand", exact = true)
interface ImportKeyServiceCommand :
    // Args ARE the wire body: the OpenAPI ImportKeyRequest body is the flat
    // ImportKey schema, and BinaryCommandAdapter decodes the raw body into the
    // args type directly — no wrapper envelope.
    ServiceCommand<ImportKey, ImportKeyResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.keys.import"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/keys/import",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Keys"),
                summary = "Import externally supplied key material",
            )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

/**
 * Service command for generating a new key.
 *
 * POST /keys
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GenerateKeyServiceCommand", exact = true)
interface GenerateKeyServiceCommand :
    // Args ARE the wire body: the OpenAPI GenerateKeyRequest body is the flat
    // GenerateKey schema (generated as GenerateKeyGlobal), and
    // BinaryCommandAdapter decodes the raw body into the args type directly —
    // no wrapper envelope.
    ServiceCommand<GenerateKeyGlobal, GenerateKeyResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.keys.generate"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/keys",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Keys"),
                summary = "Generate a new key",
            )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

/**
 * Service command for deleting a key.
 *
 * DELETE /keys/{aliasOrKid}
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyServiceCommand", exact = true)
interface DeleteKeyServiceCommand :
    ServiceCommand<DeleteKeyInput, DeleteKeyOutput, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.keys.delete"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/keys/{aliasOrKid}",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Keys"),
                summary = "Delete a key by alias or kid",
            )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

/**
 * Service command for registering an existing provider key as an external reference.
 *
 * POST /keys/register
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RegisterKeyReferenceServiceCommand", exact = true)
interface RegisterKeyReferenceServiceCommand :
    ServiceCommand<RegisterKeyReferenceInput, RegisterKeyReferenceResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.keys.register"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/keys/register",
                produces = setOf(MediaType.ApplicationJson),
                consumes = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("Keys"),
                summary = "Register an existing provider key for platform use",
            )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}
