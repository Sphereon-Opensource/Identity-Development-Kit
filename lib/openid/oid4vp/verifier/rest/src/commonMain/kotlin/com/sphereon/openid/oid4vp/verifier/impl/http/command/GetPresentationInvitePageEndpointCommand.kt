/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.verifier.impl.http.command

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
 * Public HTML landing for presentation-request invitation emails.
 *
 * `GET /invite` (and `/oid4vp/{instance}/invite` after instance-prefix strip).
 */
interface GetPresentationInvitePageEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID: String = "oid4vp.verifier.invite-page"

        val ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/invite",
                produces = setOf(MediaType.Custom("text/html")),
                operationId = "renderPresentationInvitePage",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("oid4vp", "invitation"),
                summary = "Render the verifier presentation-request invitation landing page",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )

        val INSTANCE_ENDPOINT: HttpEndpointDescriptor =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/{instanceId}/invite",
                produces = setOf(MediaType.Custom("text/html")),
                operationId = "renderPresentationInvitePageForInstance",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("oid4vp", "invitation"),
                summary = "Render the verifier presentation-request invitation landing page",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetPresentationInvitePageEndpointCommand.COMMAND_ID)
class GetPresentationInvitePageEndpointCommandImpl(
    execution: SessionExecution,
) : HttpEndpointCommandAdapter(
        id = GetPresentationInvitePageEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetPresentationInvitePageEndpointCommand.ENDPOINT,
    ),
    GetPresentationInvitePageEndpointCommand {
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
<title>Present your credential</title>
<style>
  body{font-family:system-ui,sans-serif;max-width:36rem;margin:4rem auto;padding:0 1.25rem;color:#111}
  [role=status]{min-height:1.25rem}
  code{word-break:break-all}
</style></head><body>
<h1>Present your credential</h1>
<p>This page is on your organization's verifier.</p>
<p role="status" id="status">Checking your invitation…</p>
<div id="offer" hidden></div>
<script>
  (function () {
    var params = new URLSearchParams(location.search);
    var token = params.get("t") || params.get("token") || "";
    var status = document.getElementById("status");
    var offer = document.getElementById("offer");
    if (!token) { status.textContent = "This presentation link is missing its token."; return; }
    fetch("/api/invitation/v1/invitations/resolve", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ token: token })
    }).then(function (res) { return res.json().then(function (body) { return { ok: res.ok, body: body }; }); })
      .then(function (result) {
        var ctx = result.ok && result.body && result.body.resolved && result.body.resolved.context;
        var deepLink = ctx && (ctx.walletDeepLink || ctx.wallet_deep_link);
        if (!deepLink) throw new Error("refused");
        status.textContent = "Open this request in a wallet to present the requested credentials.";
        offer.hidden = false;
        offer.innerHTML = '<p><a href="' + deepLink + '">Open in wallet</a></p><p><code>' + deepLink + '</code></p>';
      }).catch(function () {
        status.textContent = "This invitation cannot be used.";
      });
  })();
</script>
</body></html>
"""
    }
}
