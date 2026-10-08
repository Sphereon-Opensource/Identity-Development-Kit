/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageContext
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageRenderer
import com.sphereon.oauth2.server.authorization.provider.AccountActionPageResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [AccountActionPageRenderer]: one neutral page covering activation, tenant
 * onboarding and password change. EDK overlays a tenant-themed implementation through
 * `@ContributesBinding(replaces = SphereonBrandedAccountActionPageRenderer::class, ...)`.
 *
 * The action token travels in the URL fragment, so the server never sees it and cannot decide the
 * page's shape. The page renders every state and reveals one after the resolve call answers.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AccountActionPageRenderer>())
class SphereonBrandedAccountActionPageRenderer : AccountActionPageRenderer {
    override suspend fun render(ctx: AccountActionPageContext): IdkResult<AccountActionPageResponse, IdkError> {
        val nonceAttr = ctx.cspNonce?.let { """ nonce="${escapeHtml(it)}"""" }.orEmpty()
        val webAuthn = if (ctx.showWebAuthn) "true" else "false"
        val html =
            """
            <!DOCTYPE html>
            <html lang="${escapeHtml(ctx.locale)}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="robots" content="noindex, nofollow">
              <title>Set up your sign-in</title>
              <style$nonceAttr>$PAGE_CSS</style>
            </head>
            <body>
              <main class="page">
                <h1 id="account-action-title">Set up your sign-in</h1>
                <p id="account-action-intro">Choose how you want to sign in. You use the same method to confirm wallet operations.</p>

                <section id="state-checking" aria-live="polite">
                  <p>Checking your link.</p>
                </section>

                <section id="state-choice" hidden>
                  ${passkeyChoiceBlock(ctx.showWebAuthn)}
                  <button id="choose-password" type="button" class="secondary">Use a password instead</button>
                </section>

                <section id="state-password" hidden>
                  <form id="password-form">
                    <label for="password">New password
                      <input id="password" name="password" type="password" autocomplete="new-password" minlength="8">
                    </label>
                    ${alsoPasskeyBlock(ctx.showWebAuthn)}
                    <button type="submit" class="primary">Save and continue</button>
                  </form>
                </section>

                <section id="state-failed" hidden>
                  <p class="form-error" id="failure-message">We could not verify this link.</p>
                  <button id="retry-activation" type="button" class="secondary">Try again</button>
                </section>

                <section id="state-done" hidden aria-live="polite">
                  <h2>Your account is ready</h2>
                  <p id="done-detail">You can sign in now.</p>
                  <a id="activation-login" href="${escapeHtml(ctx.loginPath)}">Sign in</a>
                </section>

                <p role="status" id="status"></p>
              </main>
              <noscript><p>JavaScript is required to verify this single-use link. Enable JavaScript and reload this page.</p></noscript>
              <script$nonceAttr>
                window.__ACCOUNT_ACTION__ = {
                  webAuthn: $webAuthn,
                  tenantRootUrl: "${escapeJs(ctx.tenantRootUrl.orEmpty())}",
                  organizationName: "${escapeJs(ctx.organizationName.orEmpty())}"
                };
              </script>
              <script$nonceAttr>$PAGE_SCRIPT</script>
            </body>
            </html>
            """.trimIndent()
        return Ok(AccountActionPageResponse(html = html, cspNonce = ctx.cspNonce))
    }

    private fun passkeyChoiceBlock(showWebAuthn: Boolean): String =
        if (!showWebAuthn) {
            ""
        } else {
            """
            <button id="choose-passkey" type="button" class="primary">Create a passkey</button>
                  <p class="hint">Use your fingerprint, face or device PIN. Nothing to remember.</p>
            """.trimIndent()
        }

    private fun alsoPasskeyBlock(showWebAuthn: Boolean): String =
        if (!showWebAuthn) {
            ""
        } else {
            """
            <label class="checkbox" for="also-passkey">
                      <input id="also-passkey" type="checkbox">
                      Also create a passkey on this device
                    </label>
            """.trimIndent()
        }

    private fun escapeHtml(value: String): String =
        buildString(value.length) {
            for (char in value) {
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }

    private fun escapeJs(value: String): String =
        buildString(value.length) {
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '<' -> append("\\u003c")
                    '>' -> append("\\u003e")
                    '&' -> append("\\u0026")
                    else -> append(char)
                }
            }
        }

    companion object {
        /**
         * `[hidden]` comes first and is marked important: a plain author rule such as
         * `button{display:block}` outranks the user-agent `[hidden]{display:none}` and silently
         * defeats every `hidden` attribute on the page.
         */
        private val PAGE_CSS: String =
            """
            [hidden]{display:none !important}
            :root{--color-primary:#4f34d2;--color-surface:#fff;--color-on-surface:#161b26;--color-border:#d7dbe4;--color-error:#b3261e}
            body{font-family:system-ui,sans-serif;max-width:28rem;margin:4rem auto;padding:0 1.25rem;color:var(--color-on-surface);background:var(--color-surface)}
            label{display:block;margin:0.75rem 0}
            label.checkbox{display:flex;align-items:center;gap:0.5rem;font-weight:400}
            label.checkbox input{width:auto}
            input[type="password"]{width:100%;padding:0.55rem 0.65rem;box-sizing:border-box;border:1px solid var(--color-border);border-radius:0.35rem;background:var(--color-surface);color:var(--color-on-surface)}
            input[type="password"]:focus{outline:none;border-color:var(--color-primary);box-shadow:0 0 0 3px color-mix(in srgb, var(--color-primary) 30%, transparent)}
            button{display:block;width:100%;margin:0.75rem 0;padding:0.65rem 1rem;cursor:pointer;border-radius:0.35rem;border:1px solid transparent;font:inherit}
            button.primary{background:var(--color-primary);color:#fff}
            button.secondary{background:transparent;color:var(--color-on-surface);border-color:var(--color-border)}
            button:focus-visible{outline:none;box-shadow:0 0 0 3px color-mix(in srgb, var(--color-primary) 30%, transparent)}
            .hint{margin:0 0 1rem;font-size:0.9rem;opacity:0.8}
            .form-error{color:var(--color-error)}
            [role=status]{min-height:1.25rem}
            """.trimIndent()

        /**
         * Page behaviour. Two things here are deliberate corrections of the previous inline page:
         *
         * 1. `bytesToB64url` uses `/\+/g` and `/\//g`. The previous page wrote `/\\+/g` and
         *    `/\\//g` inside a Kotlin raw string, which reaches the browser as an escaped
         *    backslash; `/\\//g` in particular closes the regex early and leaves a bare `g`, so
         *    the helper threw `ReferenceError: g is not defined` on every call. Because passkey
         *    enrollment was wrapped in a bare `catch`, that failure was invisible and every user
         *    silently ended up with a password only.
         * 2. Passkey and password are separate, deliberate actions with their own feedback, and
         *    `residentKey` is `required` so a passkey-only account is discoverable and can sign in
         *    without a username.
         */
        private val PAGE_SCRIPT: String =
            """
            (function () {
              var config = window.__ACCOUNT_ACTION__ || {};
              var token = (location.hash || "").replace(/^#/, "");
              var webAuthnEnabled = config.webAuthn === true && !!window.PublicKeyCredential;
              var orgName = config.organizationName || "";
              var tenantRootUrl = config.tenantRootUrl || "";
              var statusEl = document.getElementById("status");
              var titleEl = document.getElementById("account-action-title");
              var introEl = document.getElementById("account-action-intro");
              var doneDetail = document.getElementById("done-detail");
              var states = {
                checking: document.getElementById("state-checking"),
                choice: document.getElementById("state-choice"),
                password: document.getElementById("state-password"),
                failed: document.getElementById("state-failed"),
                done: document.getElementById("state-done")
              };
              var resolved = null;

              function show(name) {
                for (var key in states) {
                  if (states[key]) states[key].hidden = key !== name;
                }
                if (name === "done" || name === "failed") {
                  if (titleEl) titleEl.hidden = true;
                  if (introEl) introEl.hidden = true;
                }
              }

              function setStatus(text) { if (statusEl) statusEl.textContent = text; }

              function passkeyLabel() {
                return (resolved && resolved.displayLabel) || orgName || "Account holder";
              }

              function applyCopy() {
                if (!resolved || !titleEl || !introEl) return;
                if (resolved.action === "identity.password-change") {
                  titleEl.textContent = "Set a new password";
                  introEl.textContent = "Choose a new password for your account.";
                } else {
                  titleEl.textContent = "Set up your sign-in";
                  introEl.textContent = "Choose how you want to sign in. You use the same method to confirm wallet operations.";
                }
              }

              function resolveAction() {
                if (!token) {
                  show("failed");
                  document.getElementById("failure-message").textContent =
                    "This link is missing its token. Open the complete link from the message you received.";
                  document.getElementById("retry-activation").hidden = true;
                  return;
                }
                show("checking");
                setStatus("");
                post("/api/account-actions/v1/resolve", { token: token })
                  .then(function (result) {
                    if (!result.ok || !result.body || !result.body.action) throw new Error("invalid-action");
                    resolved = result.body.action;
                    applyCopy();
                    if (resolved.requiresPassword || !webAuthnEnabled) {
                      show("password");
                    } else {
                      show("choice");
                    }
                  })
                  .catch(function () {
                    show("failed");
                  });
              }

              function post(path, body) {
                return fetch(path, {
                  method: "POST",
                  headers: { "content-type": "application/json" },
                  body: JSON.stringify(body)
                }).then(function (res) {
                  return res.json()
                    .catch(function () { return {}; })
                    .then(function (parsed) { return { ok: res.ok, body: parsed }; });
                });
              }

              function b64urlToBytes(value) {
                var pad = value.length % 4 === 0 ? "" : "=".repeat(4 - (value.length % 4));
                var bin = atob(value.replace(/-/g, "+").replace(/_/g, "/") + pad);
                var out = new Uint8Array(bin.length);
                for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
                return out;
              }

              function bytesToB64url(buffer) {
                var view = new Uint8Array(buffer);
                var bin = "";
                for (var i = 0; i < view.length; i++) bin += String.fromCharCode(view[i]);
                return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+${'$'}/g, "");
              }

              function enrollPasskey() {
                return post("/api/account-actions/v1/passkey/begin", {
                  token: token,
                  origin: location.origin,
                  rpId: location.hostname
                }).then(function (result) {
                  var options = result.ok && result.body && result.body.options;
                  if (!options || !options.challenge || !options.rpId || !options.challengeId || !options.userHandle) {
                    throw new Error("passkey-unavailable");
                  }
                  var label = passkeyLabel();
                  return navigator.credentials.create({
                    publicKey: {
                      challenge: b64urlToBytes(options.challenge),
                      rp: { id: options.rpId, name: options.rpName || orgName || options.rpId },
                      user: {
                        id: new TextEncoder().encode(options.userHandle),
                        name: label,
                        displayName: label
                      },
                      pubKeyCredParams: [
                        { type: "public-key", alg: -7 },
                        { type: "public-key", alg: -8 },
                        { type: "public-key", alg: -257 }
                      ],
                      timeout: 60000,
                      attestation: "none",
                      authenticatorSelection: {
                        authenticatorAttachment: "platform",
                        residentKey: "required",
                        requireResidentKey: true,
                        userVerification: "required"
                      }
                    }
                  }).then(function (credential) {
                    if (!credential || !credential.rawId) throw new Error("passkey-missing");
                    return {
                      passkeyChallengeId: options.challengeId,
                      passkeyCredentialId: bytesToB64url(credential.rawId),
                      passkeyAttestationObject: bytesToB64url(credential.response.attestationObject),
                      passkeyClientDataJson: bytesToB64url(credential.response.clientDataJSON),
                      passkeyOrigin: location.origin,
                      passkeyRpId: options.rpId
                    };
                  });
                });
              }

              // Where a completed action sends the user: the destination the link was issued with,
              // already authorised server-side against the issuing client's registered redirect
              // URIs, otherwise the tenant root. Never the raw token, which stays in the fragment.
              function destination() {
                return (resolved && resolved.redirectUri) || tenantRootUrl || "";
              }

              function complete(body, successText) {
                setStatus("Saving.");
                return post("/api/account-actions/v1/complete", body).then(function (result) {
                  if (!result.ok) throw new Error("refused");
                  setStatus("");
                  if (doneDetail) doneDetail.textContent = successText;
                  var target = destination();
                  var link = document.getElementById("activation-login");
                  if (target && link) {
                    link.href = target;
                    link.textContent = "Continue";
                  }
                  show("done");
                  if (target) {
                    // Brief pause so the confirmation is readable; the link is the fallback when
                    // the navigation is blocked.
                    setTimeout(function () { location.replace(target); }, 1500);
                  }
                });
              }

              var choosePasskey = document.getElementById("choose-passkey");
              if (choosePasskey) {
                choosePasskey.addEventListener("click", function () {
                  choosePasskey.disabled = true;
                  setStatus("Follow your device prompt to create the passkey.");
                  enrollPasskey()
                    .then(function (passkey) {
                      var body = { token: token };
                      for (var key in passkey) body[key] = passkey[key];
                      return complete(body, "Sign in with the passkey on this device.");
                    })
                    .catch(function () {
                      choosePasskey.disabled = false;
                      setStatus("The passkey was not created. Try again, or use a password instead.");
                    });
                });
              }

              var choosePassword = document.getElementById("choose-password");
              if (choosePassword) {
                choosePassword.addEventListener("click", function () {
                  setStatus("");
                  show("password");
                });
              }

              var passwordForm = document.getElementById("password-form");
              if (passwordForm) {
                passwordForm.addEventListener("submit", function (event) {
                  event.preventDefault();
                  var password = passwordForm.password.value;
                  if (!password || password.length < 8) {
                    setStatus("Use a password of at least 8 characters.");
                    return;
                  }
                  var alsoPasskey = document.getElementById("also-passkey");
                  var wantsPasskey = webAuthnEnabled && alsoPasskey && alsoPasskey.checked;
                  var enrollment = wantsPasskey
                    ? (setStatus("Follow your device prompt to create the passkey."), enrollPasskey())
                    : Promise.resolve(null);
                  enrollment
                    .then(function (passkey) {
                      var body = { token: token, password: password };
                      if (passkey) { for (var key in passkey) body[key] = passkey[key]; }
                      return complete(
                        body,
                        passkey
                          ? "Sign in with this password or the passkey on this device."
                          : "Sign in with this password."
                      );
                    })
                    .catch(function (error) {
                      if (wantsPasskey && error && error.message !== "refused") {
                        setStatus("The passkey was not created, so nothing was saved. Try again, or clear the passkey option to continue with the password only.");
                      } else {
                        setStatus("That did not go through. Check the password and try again.");
                      }
                    });
                });
              }

              var retry = document.getElementById("retry-activation");
              if (retry) retry.addEventListener("click", resolveAction);

              resolveAction();
            })();
            """.trimIndent()
    }
}
