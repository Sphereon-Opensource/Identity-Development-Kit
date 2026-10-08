# Module lib-sdjwt-public

Public contracts for IETF SD-JWT (Selective Disclosure JWT). It defines the issue, present, and verify command surface, the disclosure and key-binding model, and the configuration hooks consumers need to produce or validate SD-JWTs without coupling to the runtime.

SD-JWT is an envelope format used across OID4VCI credential profiles (for example SD-JWT VCs) and by holders presenting selectively over OID4VP. Depend on this module from code that has to describe SD-JWT payloads at the contract level; runtime behaviour is in `lib-sdjwt-impl`.
