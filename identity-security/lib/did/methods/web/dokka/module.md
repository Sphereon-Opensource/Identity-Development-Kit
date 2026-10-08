# Module lib-did-methods-web

`did:web` method provider and resolver. Resolves DIDs to a `/.well-known/did.json` document (or a path-based variant) over HTTPS, which is the usual choice when a DID subject controls a public hostname and wants discoverable metadata without a ledger.

The resolver side needs outbound HTTPS; deployments that lock down outbound network must allow the target hosts.
