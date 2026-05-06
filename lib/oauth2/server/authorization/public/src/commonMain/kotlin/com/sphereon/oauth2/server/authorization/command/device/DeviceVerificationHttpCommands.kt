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

package com.sphereon.oauth2.server.authorization.command.device

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

// HTTP endpoint contracts for the AS RFC 8628 §3.3 user-interaction surface served at /device and
// /device/approve. The DeviceVerificationPageRenderer SPI fills in the page; the HTTP layer carries
// the wire shape: form parsing, login session lookup, approval / denial state transitions on the
// DeviceAuthorizationStorage record, and HTML rendering.

/**
 * `GET /device`: render the user-code entry form. Reads optional `user_code` from the query string
 * (carried over from the device's `verification_uri_complete`). Always returns 200 HTML.
 */
@JsExportCompat
interface DeviceVerificationEntryHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oauth2.device.verification-entry"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/device",
                produces = setOf(MediaType.Custom("text/html")),
                operationId = "deviceEntry",
                commandId = COMMAND_ID,
                tags = setOf("device"),
                summary = "Render the RFC 8628 device verification entry form",
            )
    }
}

/**
 * `POST /device`: validate the submitted `user_code`, then either redirect to `/login` (when no
 * OIDC login session is active), redirect to `GET /device/approve?user_code=...` (when logged in),
 * re-render the entry form with an "Invalid code" banner (200 HTML) on a miss, or render the
 * result page on an EXPIRED / DENIED / CONSUMED record.
 */
@JsExportCompat
interface DeviceVerificationSubmitHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oauth2.device.verification-submit"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/device",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                operationId = "deviceSubmit",
                commandId = COMMAND_ID,
                tags = setOf("device"),
                summary = "Submit the user_code to the RFC 8628 device verification flow",
            )
    }
}

/**
 * `GET /device/approve`: render the approval prompt for the resolved `user_code`. Requires an
 * active `oidc_login_sid` session; absent / expired session 302-redirects to `/login` with a
 * `return_url` pointing back at this endpoint with the same `user_code`.
 */
@JsExportCompat
interface DeviceVerificationApprovalHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oauth2.device.verification-approval"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/device/approve",
                produces = setOf(MediaType.Custom("text/html")),
                operationId = "deviceApproveGet",
                commandId = COMMAND_ID,
                tags = setOf("device"),
                summary = "Render the RFC 8628 device approval prompt",
            )
    }
}

/**
 * `POST /device/approve`: persist the user's allow / deny decision on the device-authorization
 * record and render the result page. Requires an active OIDC login session.
 */
@JsExportCompat
interface DeviceVerificationApprovalSubmitHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oauth2.device.verification-approval-submit"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/device/approve",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                operationId = "deviceApprove",
                commandId = COMMAND_ID,
                tags = setOf("device"),
                summary = "Submit the allow / deny decision for the RFC 8628 device approval",
            )
    }
}
