# Module lib-openid-oid4vp-verifier-public

Public API for the OID4VP verifier side. It defines the verifier command surface, the authorization-session contract an application implements to persist and look up verification flows, the client-metadata and DCQL-query configuration model, and the callback shape the verifier uses to notify the embedding app about session state transitions.

Depend on this module from verifier-side application code that wants to orchestrate OID4VP flows without binding to the runtime. Implementations live in `lib-openid-oid4vp-verifier-impl`; the cross-session coordination layer sits in `lib-openid-oid4vp-universal-*`; a deployable server that exposes this over HTTP lives in `services-oid4vp-verifier-rest`.
