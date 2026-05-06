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

package com.sphereon.oauth2.server.authorization.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Strategy for rendering the AS's RFC 8628 §3.3 user-interaction surface served at
 * `GET /device`, `POST /device`, `GET /device/approve`, and `POST /device/approve`. Mirrors
 * [LoginPageRenderer] in shape. IDK ships a single Sphereon-branded implementation; EDK / VDX
 * overlays a tenant-aware implementation through `@ContributesBinding(replaces = [...])`.
 *
 * Three render points cover the user's journey from RFC 8628 §3.3:
 *  - [renderEntryForm]: the form the user lands on at `verification_uri`, optionally pre-filled
 *    with `user_code` from `verification_uri_complete`.
 *  - [renderApprovalPrompt]: the consent prompt shown after the user-code lookup succeeds and
 *    the user is logged in, listing the requesting client and the requested scopes.
 *  - [renderResultPage]: the terminal page the user lands on after approval, denial, expiry, or
 *    a code miss, telling them what just happened and that they can return to their device.
 */
@JsExportCompat
interface DeviceVerificationPageRenderer {
    /**
     * Render the entry form for `GET /device`. Implementations MUST escape any caller-controlled
     * string before interpolating into HTML.
     */
    suspend fun renderEntryForm(context: DeviceEntryContext): IdkResult<DeviceVerificationResponse, IdkError>

    /**
     * Render the approval prompt for `GET /device/approve`. Listed scopes and client name are
     * caller-controlled and MUST be escaped before HTML interpolation.
     */
    suspend fun renderApprovalPrompt(context: DeviceApprovalContext): IdkResult<DeviceVerificationResponse, IdkError>

    /**
     * Render the result / terminal page for the verification flow. Used after `POST /device/approve`
     * with `action=allow` / `action=deny`, and on lookup misses or expiry.
     */
    suspend fun renderResultPage(context: DeviceResultContext): IdkResult<DeviceVerificationResponse, IdkError>
}

/**
 * Inputs to [DeviceVerificationPageRenderer.renderEntryForm].
 *
 * @property prefilledUserCode optional `user_code` carried in from the device's
 *   `verification_uri_complete` query parameter; when present the form pre-fills the input so the
 *   user only has to confirm.
 * @property errorMessage optional human-readable error to surface (typically "Invalid code" after
 *   a previous submission missed the storage).
 * @property locale negotiated UI locale for the page copy.
 * @property baseUrl absolute base URL of the AS for any in-page links the renderer needs.
 */
@JsExportCompat
data class DeviceEntryContext(
    val prefilledUserCode: String? = null,
    val errorMessage: String? = null,
    val locale: String = "en",
    val baseUrl: String,
)

/**
 * Inputs to [DeviceVerificationPageRenderer.renderApprovalPrompt].
 *
 * @property userCode the `user_code` that resolved to the pending record; rendered in a hidden
 *   form field on the consent form.
 * @property clientId requesting client's `client_id`, shown when [clientName] is null.
 * @property clientName human-readable client name from the registry, preferred over [clientId]
 *   in the prompt copy.
 * @property requestedScopes scopes the device asked the user to grant.
 * @property locale negotiated UI locale for the page copy.
 * @property baseUrl absolute base URL of the AS for any in-page links the renderer needs.
 */
@JsExportCompat
data class DeviceApprovalContext(
    val userCode: String,
    val clientId: String,
    val clientName: String? = null,
    val requestedScopes: List<String> = emptyList(),
    val locale: String = "en",
    val baseUrl: String,
)

/**
 * Inputs to [DeviceVerificationPageRenderer.renderResultPage].
 */
@JsExportCompat
data class DeviceResultContext(
    val outcome: DeviceResultOutcome,
    val locale: String = "en",
    val baseUrl: String,
)

/**
 * Terminal outcomes the result page may render. The HTTP layer maps the active
 * [com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState] (and lookup misses)
 * onto these values so the renderer never sees the storage enum directly.
 */
@JsExportCompat
enum class DeviceResultOutcome {
    APPROVED,
    DENIED,
    EXPIRED,
    INVALID_CODE,
}

/**
 * Output of every [DeviceVerificationPageRenderer] method. Mirrors [LoginPageResponse]: the HTTP
 * layer wraps this in the declared [statusCode] response with [contentType], and adds any cookies
 * or `Cache-Control` headers separately. Status code is part of the renderer output so the entry
 * form can re-render with a non-200 surface for a code miss without the HTTP shell needing to
 * inspect the page contents.
 */
@JsExportCompat
data class DeviceVerificationResponse(
    val html: String,
    val statusCode: Int = 200,
    val contentType: String = "text/html; charset=utf-8",
)
