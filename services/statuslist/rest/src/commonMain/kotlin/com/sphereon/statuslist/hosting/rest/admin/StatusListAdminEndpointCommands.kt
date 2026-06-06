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

package com.sphereon.statuslist.hosting.rest.admin

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.StatusListDefinitionsProvider
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListResult
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.UpdateEntryStatusArgs
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.AdminCommandIds
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.AdminPaths
import com.sphereon.statuslist.hosting.rest.StatusListHostingApiConstants.Tags
import com.sphereon.statuslist.spi.StatusListDriver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val ADMIN_TAGS = setOf(Tags.STATUS_LIST_ADMIN)
private val adminJson = Json { encodeDefaults = true }
private val JSON_HEADERS = mapOf("Content-Type" to "application/json")

/**
 * Operator-facing view of one status-list index — only what the bit array actually encodes: the
 * status value at the index plus a human-readable label.
 *
 * A status list deliberately reveals nothing beyond the bit. The default unrevoked value is `0`
 * (`VALID`), which is indistinguishable from an index that was never allocated — so this view
 * exposes neither an `issued` flag nor any holder business key. Surfacing allocation state would
 * break the unlinkability the list is designed to provide.
 */
@Serializable
data class StatusListEntryStatusView(
    val correlationId: String,
    val index: Int,
    val length: Int,
    val value: Int,
    /** `VALID` | `REVOKED` | `SUSPENDED` | `UNKNOWN`. */
    val status: String,
    val revoked: Boolean,
)

@Serializable
private data class AdminError(
    val error: String,
    @SerialName("error_description") val errorDescription: String,
)

private fun statusLabel(value: Int): String =
    when (value) {
        StatusValues.VALID -> "VALID"
        StatusValues.INVALID -> "REVOKED"
        StatusValues.SUSPENDED -> "SUSPENDED"
        else -> "UNKNOWN"
    }

private fun viewFor(
    list: StatusListResult,
    index: Int,
    value: Int,
): StatusListEntryStatusView =
    StatusListEntryStatusView(
        correlationId = list.correlationId,
        index = index,
        length = list.length,
        value = value,
        status = statusLabel(value),
        revoked = value == StatusValues.INVALID,
    )

/** Admin responses are dynamic (status can change any moment), so never cache them. */
private fun adminJsonResponse(
    statusCode: Int,
    body: String,
): GenericHttpResponse = jsonResponse(statusCode, body).copy(headers = JSON_HEADERS + ("Cache-Control" to "no-store"))

private fun adminError(
    statusCode: Int,
    code: String,
    message: String,
): GenericHttpResponse = adminJsonResponse(statusCode, adminJson.encodeToString(AdminError.serializer(), AdminError(code, message)))

private fun okView(view: StatusListEntryStatusView): GenericHttpResponse = adminJsonResponse(200, adminJson.encodeToString(StatusListEntryStatusView.serializer(), view))

/**
 * Rendered when a deployment does not wire status-list support (no [StatusListDriver] /
 * [StatusListDefinitionsProvider] on the classpath). The admin endpoints are always registered, but
 * their backing SPIs are optional — so a deployment without `lib-statuslist-impl` resolves the graph
 * and answers `503` here rather than forcing every consumer to depend on an implementation it never uses.
 */
private fun statusListNotConfigured(): GenericHttpResponse = adminError(503, "statuslist_not_configured", "Status list support is not configured")

/** A validation failure to render as an admin JSON error response at the endpoint boundary. */
private data class AdminFailure(
    val statusCode: Int,
    val code: String,
    val message: String,
) {
    fun toResponse(): GenericHttpResponse = adminError(statusCode, code, message)
}

/**
 * Resolve the list + parse/validate the index. Returns a domain [AdminFailure] (not a rendered HTTP
 * response) so validation stays separate from HTTP rendering; the endpoint maps it via [AdminFailure.toResponse].
 */
private suspend fun resolveListAndIndex(
    driver: StatusListDriver,
    request: GenericHttpRequest,
): IdkResult<Pair<StatusListResult, Int>, AdminFailure> {
    val id = request.requirePathParam("id").getOrElse { return Err(AdminFailure(400, "invalid_request", it.message.defaultMessage)) }
    val indexRaw = request.requirePathParam("index").getOrElse { return Err(AdminFailure(400, "invalid_request", it.message.defaultMessage)) }
    val index =
        indexRaw.toIntOrNull()?.takeIf { it >= 0 }
            ?: return Err(AdminFailure(400, "invalid_index", "index must be a non-negative integer, was '$indexRaw'"))
    val list =
        driver.getStatusList(StatusListRef(correlationId = id)).getOrElse {
            return Err(AdminFailure(500, "driver_error", it.message.defaultMessage))
        } ?: return Err(AdminFailure(404, "not_found", "No status list '$id'"))
    if (index >= list.length) {
        return Err(AdminFailure(400, "index_out_of_range", "index $index is outside [0, ${list.length})"))
    }
    return Ok(list to index)
}

/**
 * Read the current status of a single status-list index (the operator/demo lookup).
 *
 * `GET <basePath>/{id}/entries/{index}` — `{id}` is the list `correlationId`. Returns only the bit
 * value at the index: `VALID` (the default, `0`), `REVOKED` (`1`), or `SUSPENDED` (`2`). It cannot and
 * does not report whether a credential was ever issued there — an unrevoked index is `VALID` whether
 * or not it was allocated, exactly as a status list is meant to behave. This is the SIMPLE, by-index
 * admin surface (no business keys); the EDK `lib-statuslist-management-rest` is the durable,
 * business-key management API. Unauthenticated here; a production deployment guards it behind admin auth.
 */
interface GetStatusListEntryStatusEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AdminCommandIds.ENTRY_GET

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = AdminPaths.ENTRY_STATUS,
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getStatusListEntryStatus",
                commandId = COMMAND_ID,
                tags = ADMIN_TAGS,
                summary = "Read the status of a status-list index",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetStatusListEntryStatusEndpointCommand>())
class GetStatusListEntryStatusEndpointCommandImpl(
    execution: SessionExecution,
    private val statusListDriverProvider: Provider<StatusListDriver>? = null,
) : HttpEndpointCommandAdapter(
        id = GetStatusListEntryStatusEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetStatusListEntryStatusEndpointCommand.ENDPOINT,
    ),
    GetStatusListEntryStatusEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val driver = statusListDriverProvider?.invoke() ?: return Ok(statusListNotConfigured())
        val request = applyDuring(args)
        val (list, index) = resolveListAndIndex(driver, request).getOrElse { return Ok(it.toResponse()) }
        // The bit value is all a status list encodes: a non-revoked index reads VALID (0) whether or
        // not it was ever allocated, so there is nothing else to surface.
        val entry = driver.getEntry(EntryRef(correlationId = list.correlationId, statusListIndex = index)).getOrElse { return Err(it) }
        return Ok(okView(viewFor(list, index, entry?.value ?: StatusValues.VALID)))
    }
}

/**
 * Revoke the credential at a single status-list index (the operator/demo action).
 *
 * `POST <basePath>/{id}/entries/{index}/revoke` — sets the bit at the index to `INVALID` and re-signs
 * the list. Works on any in-range index (a status list lets you set any bit; refusing "unissued"
 * indices would itself leak allocation state), and is idempotent: an already-revoked index returns
 * `200` with the current state. Unauthenticated here; a production deployment guards it behind admin auth.
 */
interface RevokeStatusListEntryEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AdminCommandIds.ENTRY_REVOKE

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = AdminPaths.ENTRY_REVOKE,
                produces = setOf(MediaType.ApplicationJson),
                operationId = "revokeStatusListEntry",
                commandId = COMMAND_ID,
                tags = ADMIN_TAGS,
                summary = "Revoke the credential at a status-list index",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RevokeStatusListEntryEndpointCommand>())
class RevokeStatusListEntryEndpointCommandImpl(
    execution: SessionExecution,
    private val statusListDriverProvider: Provider<StatusListDriver>? = null,
) : HttpEndpointCommandAdapter(
        id = RevokeStatusListEntryEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RevokeStatusListEntryEndpointCommand.ENDPOINT,
    ),
    RevokeStatusListEntryEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val driver = statusListDriverProvider?.invoke() ?: return Ok(statusListNotConfigured())
        val request = applyDuring(args)
        val (list, index) = resolveListAndIndex(driver, request).getOrElse { return Ok(it.toResponse()) }
        val ref = EntryRef(correlationId = list.correlationId, statusListIndex = index)

        // Set the bit to INVALID. An index that already carries an entry is updated; an index with no
        // entry yet is allocated directly in the revoked state — both leave the bit at INVALID without
        // revealing whether a credential had been issued there.
        val entry = driver.getEntry(ref).getOrElse { return Err(it) }
        when {
            entry == null -> {
                driver
                    .allocateEntry(
                        AllocateEntryArgs(
                            statusList = StatusListRef(correlationId = list.correlationId),
                            purpose = list.purposes.firstOrNull() ?: StatusPurpose.REVOCATION,
                            explicitIndex = index,
                            initialValue = StatusValues.INVALID,
                        ),
                    ).getOrElse { return Err(it) }
            }

            entry.value != StatusValues.INVALID -> {
                driver.updateEntryStatus(UpdateEntryStatusArgs(entry = ref, value = StatusValues.INVALID)).getOrElse { return Err(it) }
            }
            // else: already INVALID → idempotent, nothing to do.
        }
        return Ok(okView(viewFor(list, index, StatusValues.INVALID)))
    }
}

/** Confirmation returned after clearing a status list. */
@Serializable
data class StatusListClearedView(
    val correlationId: String,
    val length: Int,
    val cleared: Boolean = true,
)

/**
 * Reset a status list back to all-valid (the operator/demo "clear" action).
 *
 * `POST <basePath>/{id}/clear` — drops every revocation by deleting the list and recreating it from
 * its configured definition, so all bits return to `0` (VALID) while the `correlationId` and hosting
 * `uri` stay the same (credentials already referencing it keep resolving, now unrevoked). Mainly a
 * convenience for the in-memory example, where the list is not persisted anyway. Unauthenticated here;
 * a production deployment guards it behind admin auth.
 */
interface ClearStatusListEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = AdminCommandIds.CLEAR

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = AdminPaths.CLEAR,
                produces = setOf(MediaType.ApplicationJson),
                operationId = "clearStatusList",
                commandId = COMMAND_ID,
                tags = ADMIN_TAGS,
                summary = "Reset a status list to all-valid (clear all revocations)",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ClearStatusListEndpointCommand>())
class ClearStatusListEndpointCommandImpl(
    execution: SessionExecution,
    private val statusListDriverProvider: Provider<StatusListDriver>? = null,
    private val definitionsProviderRef: Provider<StatusListDefinitionsProvider>? = null,
) : HttpEndpointCommandAdapter(
        id = ClearStatusListEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ClearStatusListEndpointCommand.ENDPOINT,
    ),
    ClearStatusListEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val driver = statusListDriverProvider?.invoke() ?: return Ok(statusListNotConfigured())
        val provider = definitionsProviderRef?.invoke() ?: return Ok(statusListNotConfigured())
        val request = applyDuring(args)
        val id = request.requirePathParam("id").getOrElse { return Err(it) }
        // Recreate from the configured definition so the fresh list keeps the same correlationId/uri.
        val definition =
            provider.byId(id)
                ?: return Ok(adminError(404, "not_found", "No configured status list '$id' to clear"))
        driver.deleteStatusList(StatusListRef(correlationId = id)).getOrElse { return Err(it) }
        driver.createStatusList(definition).getOrElse { return Err(it) }
        return Ok(adminJsonResponse(200, adminJson.encodeToString(StatusListClearedView.serializer(), StatusListClearedView(id, definition.length))))
    }
}
