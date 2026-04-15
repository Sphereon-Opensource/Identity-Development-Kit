# Module lib-sdjwt-impl

Runtime for the SD-JWT contracts declared in `lib-sdjwt-public`. It issues SD-JWTs with per-claim disclosures and optional key binding, constructs holder presentations that reveal only the required subset, and verifies both envelope signatures and the disclosure digests that tie claims back to the issued token.

Pull this module in wherever SD-JWTs are actually produced or validated, typically in OID4VCI issuer and OID4VP holder / verifier stacks.
