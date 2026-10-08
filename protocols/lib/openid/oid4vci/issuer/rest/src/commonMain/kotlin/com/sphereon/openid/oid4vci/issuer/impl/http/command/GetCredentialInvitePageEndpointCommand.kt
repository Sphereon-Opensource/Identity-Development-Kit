/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding

/**
 * Public HTML landing for credential-claim invitation emails.
 *
 * `GET /invite` (and `/oid4vci/{instance}/invite` after instance-prefix strip).
 * The token stays in the query string (`?t=`) to match the email landing template.
 */
interface GetCredentialInvitePageEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oid4vci.protocol.invite-page"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/invite",
                produces = setOf(MediaType.Custom("text/html")),
                operationId = "renderCredentialInvitePage",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer", "invitation"),
                summary = "Render the issuer credential-claim invitation landing page",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )

        val INSTANCE_ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/{instanceId}/invite",
                produces = setOf(MediaType.Custom("text/html")),
                operationId = "renderCredentialInvitePageForInstance",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer", "invitation"),
                summary = "Render the issuer credential-claim invitation landing page",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetCredentialInvitePageEndpointCommand.COMMAND_ID)
class GetCredentialInvitePageEndpointCommandImpl(
    execution: SessionExecution,
) : HttpEndpointCommandAdapter(
        id = GetCredentialInvitePageEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetCredentialInvitePageEndpointCommand.ENDPOINT,
    ),
    GetCredentialInvitePageEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> =
        Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf(
                    "Content-Type" to "text/html; charset=utf-8",
                    "Cache-Control" to "no-store",
                ),
                body = HTML,
            ),
        )

    companion object {
        private const val HTML: String = """
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Claim your credential</title>
<style>
  body{font-family:system-ui,sans-serif;max-width:36rem;margin:4rem auto;padding:0 1.25rem;color:#111}
  [role=status]{min-height:1.25rem}
  code{word-break:break-all}
</style></head><body>
<h1>Claim your credential</h1>
<p>This page is on your organization's issuer.</p>
<p role="status" id="status">Checking your invitation…</p>
<div id="offer" hidden></div>
<script>
  (function () {
    var params = new URLSearchParams(location.search);
    var token = params.get("t") || params.get("token") || "";
    var status = document.getElementById("status");
    var offer = document.getElementById("offer");
    if (!token) { status.textContent = "This claim link is missing its token."; return; }
    fetch("/api/invitation/v1/redeem", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ token: token, redemptionBaseUrl: location.origin + "/api/invitation/v1/redeem" })
    }).then(function (res) { return res.json().then(function (body) { return { ok: res.ok, body: body }; }); })
      .then(function (result) {
        var body = result.body || {};
        if (body.type === "IDV_REDIRECT" && body.dispatchUrl) { location.replace(body.dispatchUrl); return; }
        if (body.type === "RENDER_QR" && body.credentialOfferUrl) {
          var named = (body.credentialType || "").trim();
          status.textContent = named
            ? ("You can claim your " + named + " credential. Open this offer in a wallet.")
            : "You can claim this credential. Open this offer in a wallet.";
          offer.hidden = false;
          offer.innerHTML = '<p><a href="' + body.credentialOfferUrl + '">Open in wallet</a></p><p><code>' + body.credentialOfferUrl + '</code></p>';
          return;
        }
        status.textContent = body.publicMessage || "This invitation cannot be used.";
      }).catch(function () {
        status.textContent = "This invitation cannot be used.";
      });
  })();
</script>
</body></html>
"""
    }
}
