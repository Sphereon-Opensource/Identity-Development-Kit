/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.ktor.http.client.provider

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Marks the current call as a wallet holder fetching on counterparty-supplied URLs. The holder protocol commands (OID4VP
 * and OID4VCI holder) and the wallet interaction engine run their work inside [withCounterpartyEgress]. A component that
 * holders share with servers (did:web, the OAuth 2.0 client, JWKS and OIDC discovery, issuer and SD-JWT VC metadata,
 * status lists) checks [isCounterpartyEgress] per call and then fetches under [UrlValidationPolicy.COUNTERPARTY_EGRESS].
 *
 * The mark lives in the coroutine context of the call, never in the graph: server work that happens in the same
 * process (inbound token validation, identity provider discovery, internal token exchanges) is not marked and keeps
 * its own policy.
 */
class CounterpartyEgressContext private constructor() : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CounterpartyEgressContext> {
        internal val instance: CounterpartyEgressContext = CounterpartyEgressContext()
    }
}

/** Runs [block] as a holder counterparty fetch: every shared component it reaches applies the counterparty egress rule. */
suspend fun <T> withCounterpartyEgress(block: suspend () -> T): T = withContext(CounterpartyEgressContext.instance) { block() }

/** True when the current call was marked by [withCounterpartyEgress]. */
suspend fun isCounterpartyEgress(): Boolean = currentCoroutineContext()[CounterpartyEgressContext.Key] != null

/**
 * Applies [UrlValidationPolicy.COUNTERPARTY_EGRESS] to these options, replacing any policy already set so a call site
 * cannot weaken it. Use it for every fetch a holder makes on a counterparty-supplied URL.
 */
fun HttpClientOptions.counterpartyEgress(): HttpClientOptions = copy(urlValidation = UrlValidationPolicy.COUNTERPARTY_EGRESS)

/** Applies the counterparty egress rule when [active] is true. */
fun HttpClientOptions.counterpartyEgressIf(active: Boolean): HttpClientOptions = if (active) counterpartyEgress() else this

/** Applies the counterparty egress rule when the current call is a holder counterparty fetch (see [withCounterpartyEgress]). */
suspend fun HttpClientOptions.counterpartyEgressWhenHolder(): HttpClientOptions = counterpartyEgressIf(isCounterpartyEgress())

/**
 * For a platform whose engine cannot check the addresses it connects to: refuses options whose policy requires that
 * check ([UrlValidationPolicy.requireResolvedAddressEnforcement]) instead of silently building a client that only
 * checks the literal host.
 */
fun HttpClientOptions.requireResolvedAddressEnforcementSupport(platform: String) {
    if (urlValidation?.requireResolvedAddressEnforcement == true) {
        throw UrlValidationException("The $platform HTTP engine cannot check the addresses it connects to, so it cannot enforce the counterparty egress rule")
    }
}

/** Implemented by the exceptions a platform engine raises when the egress rule refuses a destination. */
interface EgressRefusal

/**
 * True when this failure, or one of its causes, is the egress rule refusing a destination. A caller must not retry such a
 * fetch against another route.
 */
fun Throwable.isEgressRefusal(): Boolean = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is UrlValidationException || it is EgressRefusal }

private const val MAX_CAUSE_DEPTH = 8

/**
 * Options for fetching a document that lives at exactly the URL derived from an identifier (a DID document, a log, a
 * witness file): redirects are off in the option and in the Ktor configuration, so a host cannot hand the fetch to another
 * host or downgrade it to http.
 */
fun noRedirectFetchOptions(): HttpClientOptions =
    HttpClientOptions(
        followRedirects = false,
        additionalConfig = { followRedirects = false },
    )
